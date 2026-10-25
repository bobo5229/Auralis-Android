package com.bobo.auralis.mobile.ui.demo

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.MainActivity
import com.bobo.auralis.mobile.ui.theme.GlassStyle
import com.bobo.auralis.mobile.ui.theme.AppearancePreferencesRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in interaction check against the installed app and existing local library. */
class LiquidGlassBlurSettingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun menuSliderUpdatesPersistsAndSurvivesActivityRecreation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realLibrary") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AppearancePreferencesRepository(context)
        val original = runBlocking { repository.liquidGlassBlurDp.first() }
        val originalStyle = runBlocking { repository.glassStyle.first() }
        try {
            runBlocking { repository.setGlassStyle(GlassStyle.Liquid) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("更多").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("更多").performClick()
            val slider = compose.onNodeWithContentDescription("Liquid Glass 模糊强度")
            slider.performScrollTo().assertIsDisplayed()
            val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(0f..5f, range.range)
            assertEquals(9, range.steps)
            for (dp in listOf(0f, 2.5f, 5f)) {
                slider.performSemanticsAction(SemanticsActions.SetProgress) { it(dp) }
                runBlocking { withTimeout(5_000) { repository.liquidGlassBlurDp.first { it == dp } } }
                compose.waitForIdle()
                assertEquals(dp, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
            }
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithContentDescription("Liquid Glass 模糊强度").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("模糊强度 · 5 dp").assertIsDisplayed()
            compose.onNodeWithText("磨砂玻璃").performClick()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithContentDescription("Liquid Glass 模糊强度").assertDoesNotExist()
            runBlocking { withTimeout(5_000) { repository.glassStyle.first { it == GlassStyle.Frosted } } }
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithContentDescription("Liquid Glass 模糊强度").assertDoesNotExist()
            assertEquals(5f, runBlocking { repository.liquidGlassBlurDp.first() }, 0f)
            compose.onNodeWithText("Liquid Glass").performClick()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("模糊强度 · 5 dp").assertIsDisplayed()
            val capture = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.cacheDir, "liquid-glass-blur-menu.png").outputStream().use {
                capture.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            runBlocking {
                repository.setLiquidGlassBlurDp(original)
                repository.setGlassStyle(originalStyle)
            }
        }
    }
}
