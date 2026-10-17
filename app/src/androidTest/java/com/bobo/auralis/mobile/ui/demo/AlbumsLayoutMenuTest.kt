package com.bobo.auralis.mobile.ui.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumsLayoutMenuTest {
    @get:Rule val compose = createComposeRule()
    private val selections = mutableListOf<AlbumLayoutMode>()

    private fun openMenu() {
        compose.setContent {
            MaterialTheme { AlbumsLayoutMenu(AlbumLayoutMode.Grid2) { selections.add(it) } }
        }
        compose.onNodeWithContentDescription("切换专辑布局").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun selectionCommitsOnceAfterThePopupFinishesExiting() {
        openMenu()
        compose.onNodeWithText("3 列").performClick()
        compose.runOnIdle { assertTrue("Grid must not start during menu exit", selections.isEmpty()) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithText("3 列").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(AlbumLayoutMode.Grid3), selections) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(1, selections.size) }
    }

    @Test
    fun selectingCurrentModeDoesNotRequestAnotherTransition() {
        openMenu()
        compose.onNodeWithText("2 列").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
    }
}
