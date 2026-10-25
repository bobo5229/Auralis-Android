package com.bobo.auralis.mobile.library.browser

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.*
import com.bobo.auralis.mobile.library.scan.AudioFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryMissingTracksTest {
    @Test fun onlyAllMissingSourcesAreHiddenAndRecoveryPreservesDatabaseRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AuralisDatabase::class.java).build()
        try {
            val track = TrackEntity(TrackId.random(), 1, "recoverable", TrackKeyStrength.STRONG,
                null, "Recoverable", null, 1, null, 1, null, 0, 0)
            db.trackDao().insert(track)
            fun source(state: SourceAvailabilityState): TrackSourceEntity {
                val id = TrackSourceId.random()
                return TrackSourceEntity(id, track.trackId, "test", id.toString(), null,
                    "content://test/$id", "track.flac", "track.flac", AudioFormat.FLAC, "audio/flac",
                    100, 1, null, null, null, state, ParseState.PARSED, PlayabilityState.UNKNOWN,
                    null, null, null, null, null)
            }
            val missing = source(SourceAvailabilityState.MISSING)
            val backup = source(SourceAvailabilityState.MISSING)
            db.sourceDao().insert(missing)
            db.sourceDao().insert(backup)
            val repository = LibraryBrowserRepository(db)
            assertEquals(listOf(track.trackId), db.libraryBrowserDao().readMissingTrackIds())
            assertTrue(repository.snapshot.first().tracks.isEmpty())
            assertEquals(track, db.trackDao().findById(track.trackId))
            assertEquals(2, db.sourceDao().findByTrack(track.trackId).size)
            // No active source is needed to retain temporarily inaccessible or broken files.
            for (state in listOf(SourceAvailabilityState.UNREACHABLE, SourceAvailabilityState.AVAILABLE)) {
                db.sourceDao().update(backup.copy(availabilityState = state, playabilityState = PlayabilityState.UNPLAYABLE))
                assertTrue(db.libraryBrowserDao().readMissingTrackIds().isEmpty())
                val restored = repository.snapshot.first().tracks.single()
                assertEquals(track.trackId, restored.trackId)
                assertFalse(restored.isPlayable)
            }
            db.sourceDao().update(backup)
            assertTrue(repository.snapshot.first().tracks.isEmpty())
            assertEquals(1, db.trackDao().count())
        } finally {
            db.close()
        }
    }
}
