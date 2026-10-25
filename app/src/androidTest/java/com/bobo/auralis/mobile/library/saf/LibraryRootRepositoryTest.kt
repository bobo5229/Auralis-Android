package com.bobo.auralis.mobile.library.saf

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for Step I: Root persistence migration.
 *
 * Verifies §55:
 * - Legacy roots migrated without losing addition order (priority)
 * - Tree URIs migrated verbatim
 * - Room becomes sole source of truth
 */
@RunWith(AndroidJUnit4::class)
class LibraryRootRepositoryTest {

    private lateinit var db: AuralisDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        // Never clear real app preferences when these tests run on the user's phone.
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("root_repository_test_$name", mode)
        }
        db = Room.inMemoryDatabaseBuilder(context, AuralisDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Clean legacy prefs before each test
        context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun legacySpikeRootsMigratedToRoomPreservingOrder() = runBlocking {
        val prefs = context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
        val uri0 = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val uri1 = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        // Simulate legacy entries in order
        prefs.edit().putString("roots", "$uri0\n$uri1").commit()

        val repository = LibraryRootRepository(context, db)
        val roots = repository.initialize()

        // §55: Verify order, URIs, and priority sequence
        assertEquals(2, roots.size)
        assertEquals(uri0, roots[0].treeUri)
        assertEquals(0, roots[0].priority)

        assertEquals(uri1, roots[1].treeUri)
        assertEquals(1, roots[1].priority)

        // Adding a new root continues monotonic priority
        val uri2 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAlbums")
        val opened2 = SafLocation.restore(uri2)
        val added = repository.addRoot(opened2)

        assertNotNull(added)
        assertEquals(2, added?.priority)

        val updatedList = repository.getRoots()
        assertEquals(3, updatedList.size)
        assertEquals(2, updatedList[2].priority)
    }

    @Test
    fun removingAllMigratedRootsDoesNotImportThemAgain() = runBlocking {
        val prefs = context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
        val uri = "content://com.android.externalstorage.documents/tree/primary%3A__migration_test__"
        prefs.edit().putString("roots", uri).commit()
        val repository = LibraryRootRepository(context, db)
        val root = repository.initialize().single()
        assertFalse(prefs.contains("roots"))

        assertTrue(repository.removeRoot(root.rootId))
        assertTrue(LibraryRootRepository(context, db).initialize().isEmpty())
    }

    @Test
    fun existingRoomRootsRetireStaleLegacyRecords() = runBlocking {
        val prefs = context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
        prefs.edit().putString("roots", "content://com.android.externalstorage.documents/tree/primary%3AOld").commit()
        val repository = LibraryRootRepository(context, db)
        val root = requireNotNull(repository.addRoot(SafLocation.restore(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A__room_test__"),
        )))

        assertEquals(listOf(root), repository.initialize())
        assertFalse(prefs.contains("roots"))
        assertTrue(repository.removeRoot(root.rootId))
        assertTrue(repository.initialize().isEmpty())
    }

    @Test
    fun failedMigrationRetainsLegacyRootsForRetry() = runBlocking {
        val prefs = context.getSharedPreferences("auralis_saf_spike", Context.MODE_PRIVATE)
        val first = "content://com.android.externalstorage.documents/tree/primary%3AFirst"
        val second = "content://com.android.externalstorage.documents/tree/primary%3ASecond"
        prefs.edit().putString("roots", "$first\n$second").commit()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_second_root BEFORE INSERT ON library_root " +
                "WHEN NEW.priority = 1 BEGIN SELECT RAISE(ABORT, 'migration failed'); END",
        )
        val repository = LibraryRootRepository(context, db)
        try {
            repository.initialize()
            fail("Migration should fail")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(0, db.libraryRootDao().count())
            assertEquals("$first\n$second", prefs.getString("roots", null))
        }
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_root")
        assertEquals(listOf(first, second), repository.initialize().map { it.treeUri })
        assertFalse(prefs.contains("roots"))
    }

    @Test
    fun duplicateTreeUriIsRejected() = runBlocking {
        val repository = LibraryRootRepository(context, db)
        repository.initialize()

        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        val opened = SafLocation.restore(uri)

        val first = repository.addRoot(opened)
        assertNotNull(first)

        val second = repository.addRoot(opened)
        assertEquals(null, second) // Rejected
    }
}
