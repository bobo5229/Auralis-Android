package com.bobo.auralis.mobile.ui.demo

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.MainActivity
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AppearancePreferencesRepository
import java.io.File
import java.util.Collections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in hardware probe: uses the existing library, restores preferences, never scans or plays. */
@RunWith(AndroidJUnit4::class)
class AlbumReflowProfileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    @Volatile private var sample: String? = null
    private val frames = Collections.synchronizedList(mutableListOf<JSONObject>())

    @Test
    fun compareSameDensityTransitionsOnRealLibrary() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit opt-in required on a phone with a library", args.getString("profileAlbumReflow") == "true")
        val preferences = AppearancePreferencesRepository(instrumentation.targetContext)
        val originalMode = runBlocking { preferences.albumLayoutMode.first() }
        val originalStyle = runBlocking { preferences.albumLayoutAnimationStyle.first() }
        val thread = HandlerThread("album-frame-metrics").apply { start() }
        val handler = Handler(thread.looper)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            runBlocking {
                preferences.setAlbumLayoutAnimationStyle(AlbumLayoutAnimationStyle.Reflow)
                preferences.setAlbumLayoutMode(AlbumLayoutMode.Grid2)
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(1_500)
            click("资料库", description = true)
            SystemClock.sleep(500)
            click("专辑")
            find("切换专辑布局", description = true)
            SystemClock.sleep(1_500)
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener({ _, metrics, dropped ->
                    val label = sample ?: return@addOnFrameMetricsAvailableListener
                    if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                        frames.add(JSONObject().apply {
                            put("sample", label)
                            put("vsyncNs", metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                            put("totalMs", metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
                            put("layoutMs", metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1e6)
                            put("drawMs", metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1e6)
                            put("syncMs", metrics.getMetric(FrameMetrics.SYNC_DURATION) / 1e6)
                            put("gpuMs", metrics.getMetric(FrameMetrics.GPU_DURATION) / 1e6)
                            put("deadlineMs", metrics.getMetric(FrameMetrics.DEADLINE) / 1e6)
                            put("unknownMs", metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) / 1e6)
                            put("droppedReports", dropped)
                        })
                    }
                }, handler)
            }
            // Warm both input paths and both cover sizes before collecting comparisons.
            menu(AlbumLayoutMode.Grid3, null)
            assertEquals(AlbumLayoutMode.Grid3, runBlocking { preferences.albumLayoutMode.first() })
            pinch(AlbumLayoutMode.Grid2, null)
            assertEquals(AlbumLayoutMode.Grid2, runBlocking { preferences.albumLayoutMode.first() })
            pinch(AlbumLayoutMode.Grid3, null)
            assertEquals(AlbumLayoutMode.Grid3, runBlocking { preferences.albumLayoutMode.first() })
            menu(AlbumLayoutMode.Grid2, null)
            assertEquals(AlbumLayoutMode.Grid2, runBlocking { preferences.albumLayoutMode.first() })
            repeat(6) { round ->
                val paths = if (round % 2 == 0) listOf("menu", "pinch") else listOf("pinch", "menu")
                paths.forEach { path ->
                    listOf(AlbumLayoutMode.Grid3, AlbumLayoutMode.Grid2).forEach { target ->
                        val label = "$path-${if (target == AlbumLayoutMode.Grid3) "2-3" else "3-2"}-$round"
                        if (path == "menu") menu(target, label) else pinch(target, label)
                        assertEquals("Gesture must reach exactly the same target", target, runBlocking { preferences.albumLayoutMode.first() })
                    }
                }
            }
            val output = JSONObject().apply {
                put("device", android.os.Build.MODEL)
                put("sdk", android.os.Build.VERSION.SDK_INT)
                put("frames", JSONArray(frames.toList()))
            }
            val directory = File(instrumentation.targetContext.cacheDir, "reflow-profile").apply { mkdirs() }
            File(directory, "${args.getString("profileLabel") ?: "capture"}.json").writeText(output.toString(2))
            assertTrue("Probe must capture rendering frames", frames.size > 100)
        } finally {
            sample = null
            runBlocking {
                preferences.setAlbumLayoutMode(originalMode)
                preferences.setAlbumLayoutAnimationStyle(originalStyle)
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            scenario.close()
            thread.quitSafely()
        }
    }

    private fun menu(target: AlbumLayoutMode, label: String?) {
        click("切换专辑布局", description = true)
        SystemClock.sleep(500)
        val option = find(target.label)
        sample = label
        assertTrue("Menu option must be clickable", clickNode(option))
        SystemClock.sleep(1_400)
        sample = null
    }

    private fun pinch(target: AlbumLayoutMode, label: String?) {
        val bounds = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
        val centerX = bounds.exactCenterX()
        val touchY = bounds.top + bounds.height() * 0.45f
        val radius = bounds.width() * 0.17f
        val factor = if (target == AlbumLayoutMode.Grid3) 0.80f else 1.25f
        var downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, count: Int, scale: Float) {
            val properties = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(count) { i -> MotionEvent.PointerCoords().apply {
                x = centerX + (if (i == 0) -1 else 1) * radius * scale
                y = touchY; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue("Touch injection failed", automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        // Cancel a contact before sampling so the baseline's cached gesture mode
        // cannot turn a menu->pinch comparison into a different column change.
        inject(MotionEvent.ACTION_DOWN, 1, 1f)
        inject(MotionEvent.ACTION_CANCEL, 1, 1f)
        SystemClock.sleep(200)
        downTime = SystemClock.uptimeMillis()
        sample = label
        inject(MotionEvent.ACTION_DOWN, 1, 1f)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 1f)
        repeat(16) { step ->
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, 2, 1f + (factor - 1f) * (step + 1) / 16f)
        }
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, factor)
        inject(MotionEvent.ACTION_UP, 1, factor)
        SystemClock.sleep(1_400)
        sample = null
    }

    private fun click(text: String, description: Boolean = false) {
        assertTrue("Cannot click $text", clickNode(find(text, description)))
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    private fun find(text: String, description: Boolean = false): AccessibilityNodeInfo {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val value = if (description) node.contentDescription else node.text
            if (value?.toString() == text) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { found -> return found } }
            return null
        }
        repeat(50) {
            visit(automation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Cannot find $text in the foreground app")
    }
}
