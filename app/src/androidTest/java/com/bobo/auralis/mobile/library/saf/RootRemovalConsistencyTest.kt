package com.bobo.auralis.mobile.library.saf

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.bobo.auralis.mobile.library.browser.LibraryBrowserRepository
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.reconciliation.RoomReconciliationTransaction
import com.bobo.auralis.mobile.playback.playbackObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RootRemovalConsistencyTest {
    private lateinit var db: AuralisDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tx get() = RoomReconciliationTransaction(db)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AuralisDatabase::class.java).build()
    }
    @After fun tearDown() = db.close()
    private suspend fun root(path: String): LibraryRootEntity = LibraryRootRepository(context, db).addRoot(
        SafLocation.restore(Uri.parse("content://auralis.root-removal-test/tree/" + Uri.encode("primary:$path"))),
    )!!

    @Test fun removingWinnerPromotesBackupWithoutChangingTrackIdentity() = runBlocking {
        val first = root("MusicA")
        val backup = root("MusicB")
        val trackId = tx.reconcileObservation(playbackObservation("primary:MusicA/a.flac", first), first.rootId, 1)
        assertEquals(trackId, tx.reconcileObservation(playbackObservation("primary:MusicB/b.flac", backup), backup.rootId, 1))
        val old = db.sourceDao().findActiveSource(trackId)!!.sourceId
        assertTrue(LibraryRootRepository(context, db).removeRoot(first.rootId))
        val detached = db.sourceDao().findById(old)!!
        assertNull(detached.rootId)
        assertEquals(SourceAvailabilityState.UNREACHABLE, detached.availabilityState)
        assertNotEquals(old, db.sourceDao().findActiveSource(trackId)!!.sourceId)
        assertEquals(trackId, LibraryBrowserRepository(db).snapshot.first().tracks.single().trackId)
        assertTrue(LibraryBrowserRepository(db).snapshot.first().tracks.single().isPlayable)
    }

    @Test fun removingLastRootRetainsRecordsButStopsAdvertisingPlayableSources() = runBlocking {
        val root = root("Music")
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/a.flac", root), root.rootId, 1)
        LibraryRootRepository(context, db).removeRoot(root.rootId)
        assertNotNull(db.trackDao().findById(trackId))
        assertEquals(SourceAvailabilityState.UNREACHABLE, db.sourceDao().findByTrack(trackId).single().availabilityState)
        assertNull(db.sourceDao().findActiveSource(trackId))
        assertFalse(LibraryBrowserRepository(db).snapshot.first().tracks.single().isPlayable)
    }

    @Test fun overlappingRootRebindsTheSameSourceOnlyAfterVerifiedAccess() = runBlocking {
        val parent = root("Music")
        val child = root("Music/Album")
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/Album/a.flac", parent), parent.rootId, 1)
        val oldSource = db.sourceDao().findByTrack(trackId).single()
        var probes = 0
        val repository = LibraryRootRepository(context, db) { uri, documentId ->
            assertFalse(db.inTransaction())
            assertEquals(child.treeUri, DocumentsTree(uri))
            assertEquals(oldSource.documentId, documentId)
            probes++
            true
        }
        repository.removeRoot(parent.rootId)
        assertEquals(1, probes)
        val rebound = db.sourceDao().findById(oldSource.sourceId)!!
        assertEquals(child.rootId, rebound.rootId)
        assertEquals(child.treeUri, DocumentsTree(Uri.parse(rebound.uri)))
        assertEquals(oldSource.sourceId, db.sourceDao().findActiveSource(trackId)!!.sourceId)
        assertEquals(SourceAvailabilityState.AVAILABLE, rebound.availabilityState)
    }

    @Test fun inaccessibleOverlapDoesNotClaimAccessOrResurrectMissingSources() = runBlocking {
        val parent = root("Music")
        root("Music/Album")
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/Album/a.flac", parent), parent.rootId, 1)
        db.withTransaction {
            val source = db.sourceDao().findByTrack(trackId).single()
            db.sourceDao().update(source.copy(availabilityState = SourceAvailabilityState.MISSING))
            tx.refreshActiveSourceForTrack(trackId)
        }
        LibraryRootRepository(context, db) { _, _ -> false }.removeRoot(parent.rootId)
        assertEquals(SourceAvailabilityState.MISSING, db.sourceDao().findByTrack(trackId).single().availabilityState)
        assertNull(db.sourceDao().findActiveSource(trackId))
        assertTrue(LibraryBrowserRepository(db).snapshot.first().tracks.isEmpty())
    }

    @Test fun overlappingPathWithoutReadableAccessIsNotEnoughToRetainAvailability() = runBlocking {
        val parent = root("Music")
        root("Music/Album")
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/Album/a.flac", parent), parent.rootId, 1)
        LibraryRootRepository(context, db) { _, _ -> false }.removeRoot(parent.rootId)
        val source = db.sourceDao().findByTrack(trackId).single()
        assertNull(source.rootId)
        assertEquals(SourceAvailabilityState.UNREACHABLE, source.availabilityState)
        assertNull(db.sourceDao().findActiveSource(trackId))
        assertFalse(LibraryBrowserRepository(db).snapshot.first().tracks.single().isPlayable)
    }

    @Test fun projectionFailureRollsBackRootAndSourceChangesTogether() = runBlocking {
        val root = root("Music")
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/a.flac", root), root.rootId, 1)
        val before = db.sourceDao().findByTrack(trackId).single()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_clear BEFORE DELETE ON track_active_source BEGIN SELECT RAISE(ABORT, 'projection failed'); END",
        )
        try {
            LibraryRootRepository(context, db).removeRoot(root.rootId)
            fail("Removal must fail atomically")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(root, db.libraryRootDao().findById(root.rootId))
            assertEquals(before, db.sourceDao().findById(before.sourceId))
            assertEquals(before.sourceId, db.sourceDao().findActiveSource(trackId)!!.sourceId)
        }
    }

    private fun DocumentsTree(uri: Uri): String = android.provider.DocumentsContract.buildTreeDocumentUri(
        uri.authority, android.provider.DocumentsContract.getTreeDocumentId(uri),
    ).toString()
}
