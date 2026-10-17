package com.bobo.auralis.mobile.ui.demo

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.bobo.auralis.mobile.artwork.LibraryArtworkKeyer
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AuralisTheme
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, real-time rendering probe. Synthetic models never enter Room or DataStore. */
@RunWith(AndroidJUnit4::class)
class AlbumReflowStressTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val mode = mutableStateOf(AlbumLayoutMode.Grid2)
    private val frames = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val operations = JSONArray()
    private val commits = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
    @Volatile private var sample: String? = null

    @Test
    fun prepareLocalCovers() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("prepareStressCovers") == "true")
        createJpegs(File(instrumentation.targetContext.cacheDir, "album-reflow-stress/fixtures"))
    }

    @OptIn(ExperimentalMaterial3Api::class, DelicateCoilApi::class)
    @Test
    fun syntheticLibraryTransitions() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Hardware stress tests require explicit opt-in", args.getString("stressAlbums") != null)
        val count = args.getString("stressAlbums")!!.toInt()
        require(count in 100..10_000)
        val jpeg = args.getString("stressArtwork") == "jpeg"
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "album-reflow-stress").apply { mkdirs() }
        val label = args.getString("stressLabel") ?: "capture"
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val fetches = AtomicInteger()
        val originalLoader = SingletonImageLoader.get(context)
        val loader = ImageLoader.Builder(context).components {
            add(LibraryArtworkKeyer())
            add(object : Fetcher.Factory<LibraryArtworkSource> {
                override fun create(data: LibraryArtworkSource, options: Options, imageLoader: ImageLoader): Fetcher =
                    object : Fetcher {
                        override suspend fun fetch(): SourceFetchResult {
                            fetches.incrementAndGet()
                            val file = File(requireNotNull(Uri.parse(data.audioSource.audioUri).path))
                            return SourceFetchResult(ImageSource(file.source().buffer(), options.fileSystem), "image/jpeg", DataSource.DISK)
                        }
                    }
            })
        }.build()
        val fixtureStart = SystemClock.elapsedRealtime()
        val covers = if (jpeg) createJpegs(File(output, "fixtures")) else emptyList()
        val albums = List(count) { index ->
            val id = AlbumId(UUID(0, index.toLong() + 1))
            val source = covers.takeIf { it.isNotEmpty() }?.let {
                val file = it[index % it.size]
                LibraryArtworkSource(com.bobo.auralis.mobile.library.model.LibraryAudioSource(TrackSourceId(UUID(2, index.toLong())), Uri.fromFile(file).toString(), null, file.name, file.name, 0, file.length(), null), false)
            }
            LibraryAlbum(
                id, "专辑 $index · A longer album title 流年与四季",
                listOf("Artist ${index % 97}", "演奏者 ${index % 31}"), "2026-09-18",
                List(10) { track -> LibraryTrack(TrackId(UUID(1, index * 10L + track)), id, "曲目 $track", listOf("Artist ${index % 97}"), "专辑 $index", 180_000, 1, track + 1, true, null, source) },
                source,
            )
        }
        val fixtureMs = SystemClock.elapsedRealtime() - fixtureStart
        SingletonImageLoader.setUnsafe(loader)
        val metricsThread = HandlerThread("stress-frame-metrics").apply { start() }
        val gridState = LazyGridState()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val serviceInfo = automation.serviceInfo
        val originalAccessibilityFlags = serviceInfo.flags
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val current = sample ?: return@OnFrameMetricsAvailableListener
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) frames.add(JSONObject().apply {
                put("sample", current)
                put("vsyncNs", metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                put("totalMs", metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
                put("layoutMs", metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1e6)
                put("drawMs", metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1e6)
                put("gpuMs", metrics.getMetric(FrameMetrics.GPU_DURATION) / 1e6)
                put("unknownMs", metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) / 1e6)
                put("deadlineMs", metrics.getMetric(FrameMetrics.DEADLINE) / 1e6)
                put("reportsDropped", dropped)
            })
        }
        try {
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            // Wait for rotation/recreation before attaching content to the
            // current Activity. A fixed delay could attach it to a dying one.
            var previousActivity: ComponentActivity? = null
            var stable = 0
            for (attempt in 0 until 50) {
                scenario.onActivity { activity ->
                    stable = if (activity === previousActivity && activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) stable + 1 else 0
                    previousActivity = activity
                }
                if (stable >= 3) break
                SystemClock.sleep(100)
            }
            assertTrue("Portrait activity did not settle", stable >= 3)
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(metricsThread.looper))
                activity.setContent {
                    AuralisTheme(AuralisThemeMode.Dark) {
                        Scaffold(topBar = {
                            TopAppBar(title = { Text("专辑") }, actions = { AlbumsLayoutMenu(mode.value, ::selectMode) })
                        }) { padding ->
                            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                                DemoAlbumsScreen(albums, 0.dp, gridState, mode.value, AlbumLayoutAnimationStyle.Reflow, ::selectMode, {})
                            }
                        }
                    }
                }
            }
            find("切换专辑布局", true)
            // No animation warm-up: keep the very first interaction in the report.
            val waitMs = args.getString("stressWaitMs")?.toLong() ?: 0L
            SystemClock.sleep(waitMs)
            val firstInput = args.getString("stressFirstInput") ?: "menu"
            val menuOnly = args.getString("stressMenuOnly") == "true"
            measure("first-$firstInput-2-3", fetches) {
                if (firstInput == "pinch") pinch(true) else menu(AlbumLayoutMode.Grid3)
            }
            setMode(AlbumLayoutMode.Grid2)
            repeat(3) { round ->
                measure("warm-menu-2-3-$round", fetches) { menu(AlbumLayoutMode.Grid3) }
                measure("warm-menu-3-2-$round", fetches) { menu(AlbumLayoutMode.Grid2) }
                if (!menuOnly) {
                    measure("warm-pinch-2-3-$round", fetches) { pinch(true) }
                    measure("warm-pinch-3-2-$round", fetches) { pinch(false) }
                }
            }
            setMode(AlbumLayoutMode.Grid1)
            measure("jump-1-5", fetches) { menu(AlbumLayoutMode.Grid5) }
            measure("jump-5-1", fetches) { menu(AlbumLayoutMode.Grid1) }
            setMode(AlbumLayoutMode.Grid2)
            measure("interrupt-2-5-2", fetches) {
                instrumentation.runOnMainSync { selectMode(AlbumLayoutMode.Grid5) }
                SystemClock.sleep(80)
                instrumentation.runOnMainSync { selectMode(AlbumLayoutMode.Grid2) }
            }
            assertEquals(AlbumLayoutMode.Grid2, mode.value)
            runBlocking(Dispatchers.Main) { gridState.scrollToItem(count / 2) }
            measure("middle-menu-2-3", fetches) { menu(AlbumLayoutMode.Grid3) }
            measure("middle-menu-3-2", fetches) { menu(AlbumLayoutMode.Grid2) }
            for (target in listOf(AlbumLayoutMode.List, AlbumLayoutMode.Coverless, AlbumLayoutMode.Grid2)) {
                measure("mode-${target.key}", fetches) { menu(target) }
            }
            val screenshot = automation.takeScreenshot()
            File(output, "$label.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            assertTrue("Frame metrics were not collected", frames.size > 100)
        } finally {
            sample = null
            File(output, "$label.json").writeText(JSONObject().apply {
                put("device", android.os.Build.MODEL)
                put("sdk", android.os.Build.VERSION.SDK_INT)
                put("albums", count)
                put("tracks", count * 10)
                put("menuOnly", args.getString("stressMenuOnly") == "true")
                put("artwork", if (jpeg) "synthetic local JPEG, test fetcher, no SAF/TagLib" else "generated Canvas artwork")
                put("fixtureBuildMs", fixtureMs)
                put("fixtureBuildExcludedFromFrameSamples", true)
                put("scope", "Real-time ComponentActivity with production album screen/menu; excludes Room, DataStore, dock and playback")
                put("operations", operations)
                put("frames", JSONArray(frames.toList()))
            }.toString(2))
            scenario.onActivity {
                it.window.removeOnFrameMetricsAvailableListener(listener)
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            scenario.close()
            metricsThread.quitSafely()
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
            serviceInfo.flags = originalAccessibilityFlags
            automation.serviceInfo = serviceInfo
        }
    }

    private fun measure(label: String, fetches: AtomicInteger, action: () -> Unit) {
        assertNoExternalWindows()
        val initialFetches = fetches.get()
        val gcBefore = Debug.getRuntimeStat("art.gc.gc-count").toLongOrNull() ?: 0
        commits.clear()
        val startNs = System.nanoTime()
        sample = label
        // Diagnostic samples are separate from timing comparisons: the ART
        // sampler changes runtime overhead and must never label a benchmark run.
        val profile = InstrumentationRegistry.getArguments().getString("stressSample")
            ?.split(',')?.contains(label) == true
        val traceStartNs = if (profile) System.nanoTime() else null
        if (profile) Debug.startMethodTracingSampling(
            File(instrumentation.targetContext.cacheDir, "album-reflow-stress/$label.trace").path,
            16 * 1024 * 1024,
            1_000,
        )
        try {
            action()
            SystemClock.sleep(1_200)
        } finally {
            if (profile) Debug.stopMethodTracing()
            sample = null
        }
        assertNoExternalWindows()
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        operations.put(JSONObject().apply {
            put("sample", label)
            put("startNs", startNs)
            if (traceStartNs != null) put("diagnosticTraceStartNs", traceStartNs)
            put("commits", JSONArray(commits.toList().map { (mode, time) -> JSONObject().put("mode", mode).put("timeNs", time) }))
            put("javaHeapMiB", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0)
            put("pssMiB", memory.totalPss / 1024.0)
            put("fetches", fetches.get() - initialFetches)
            put("gcCount", (Debug.getRuntimeStat("art.gc.gc-count").toLongOrNull() ?: 0) - gcBefore)
        })
    }

    private fun assertNoExternalWindows() {
        val otherPackages = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }
            .filter { it != instrumentation.targetContext.packageName }.distinct()
        assertTrue("External application windows invalidate timing samples: $otherPackages", otherPackages.isEmpty())
    }

    private fun setMode(target: AlbumLayoutMode) {
        instrumentation.runOnMainSync { mode.value = target }
        SystemClock.sleep(1_200)
    }

    private fun selectMode(target: AlbumLayoutMode) {
        if (mode.value != target) commits.add(target.key to System.nanoTime())
        mode.value = target
    }

    private fun menu(target: AlbumLayoutMode) {
        click(find("切换专辑布局", true))
        click(find(target.label))
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (mode.value != target && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertEquals("Menu did not commit its target", target, mode.value)
    }

    private fun pinch(shrink: Boolean) {
        val bounds = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
        val touchY = bounds.top + bounds.height() * 0.45f
        val centerX = bounds.exactCenterX()
        val radius = bounds.width() * 0.17f
        val factor = if (shrink) 0.80f else 1.25f
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, count: Int, scale: Float) {
            val properties = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(count) { i -> MotionEvent.PointerCoords().apply {
                x = centerX + (if (i == 0) -1 else 1) * radius * scale
                y = touchY; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try {
                assertTrue("Touch injection failed: action=$action bounds=$bounds active=${automation.rootInActiveWindow?.packageName}", automation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, 1, 1f)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 1f)
        repeat(16) { step ->
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, 2, 1f + (factor - 1f) * (step + 1) / 16f)
        }
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, factor)
        inject(MotionEvent.ACTION_UP, 1, factor)
        assertEquals(AlbumLayoutMode.entries.first { it.gridColumns == if (shrink) 3 else 2 }, mode.value)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                assertTrue(current.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return
            }
            current = current.parent
        }
        error("Node is not clickable")
    }

    private fun find(text: String, description: Boolean = false): AccessibilityNodeInfo {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if ((if (description) node.contentDescription else node.text)?.toString() == text) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { found -> return found } }
            return null
        }
        repeat(100) {
            visit(automation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Cannot find $text in the synthetic scene; foreground package=${automation.rootInActiveWindow?.packageName}")
    }

    private fun createJpegs(directory: File): List<File> {
        directory.mkdirs()
        return List(24) { index ->
            File(directory, "cover-$index.jpg").also { file ->
                if (!file.exists()) {
                    val size = if (index % 4 == 0) 1536 else 768
                    val pixels = IntArray(size * size) { p ->
                        val x = p % size
                        val y = p / size
                        0xff000000.toInt() or (((x * 3 + index * 13) and 255) shl 16) or (((y * 5 + index * 7) and 255) shl 8) or ((x xor y xor (index * 17)) and 255)
                    }
                    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
