package com.bobo.auralis.mobile.ui.library

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.AuralisApplication
import com.bobo.auralis.mobile.library.management.LibraryManagementStore
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryManagementActivityTest {
    @Test fun recreationAndReopeningKeepTheProcessOwnedStore() {
        var initial: LibraryManagementStore? = null
        ActivityScenario.launch(LibraryManagementActivity::class.java).use { scenario ->
            scenario.onActivity { initial = (it.application as AuralisApplication).libraryManagement }
            scenario.recreate()
            scenario.onActivity { assertSame(initial, (it.application as AuralisApplication).libraryManagement) }
        }
        ActivityScenario.launch(LibraryManagementActivity::class.java).use { scenario ->
            scenario.onActivity { assertSame(initial, (it.application as AuralisApplication).libraryManagement) }
        }
    }
}
