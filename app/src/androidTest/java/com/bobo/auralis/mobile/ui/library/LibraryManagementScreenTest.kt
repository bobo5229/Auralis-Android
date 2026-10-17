package com.bobo.auralis.mobile.ui.library

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.management.LibraryManagementState
import com.bobo.auralis.mobile.library.management.ScanStatus
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.ui.theme.AuralisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryManagementScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scanDisablesMutationAndRemovalRequiresConfirmation() {
        val root = LibraryRootEntity(LibraryRootId.random(), "content://test", "/Music", 0,
            RootAvailabilityState.AVAILABLE, null, null, 0)
        val state = mutableStateOf(LibraryManagementState(roots = listOf(root), initialized = true))
        var removals = 0
        var backs = 0
        compose.setContent {
            AuralisTheme {
                LibraryManagementScreen(state.value, { backs++ }, {}, { removals++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("曲库管理").assertIsDisplayed()
        capture("library-management-ready.png")
        compose.onNodeWithText("移除目录").performClick()
        compose.runOnIdle { assertEquals(0, removals) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, removals) }
        compose.onNodeWithText("移除目录").performClick()
        compose.onNodeWithText("移除").performClick()
        compose.runOnIdle { assertEquals(1, removals) }
        compose.runOnIdle { state.value = state.value.copy(busy = true, status = ScanStatus.Scanning) }
        compose.onNodeWithText("添加音乐目录").assertIsNotEnabled()
        compose.onNodeWithText("重新扫描").assertIsNotEnabled()
        compose.onNodeWithText("移除目录").assertIsNotEnabled()
        capture("library-management-scanning.png")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
