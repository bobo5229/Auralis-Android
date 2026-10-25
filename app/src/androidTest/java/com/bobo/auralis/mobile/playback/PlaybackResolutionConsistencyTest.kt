package com.bobo.auralis.mobile.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.library.browser.LibraryBrowserRepository
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.TrackActiveSourceEntity
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.reconciliation.RoomReconciliationTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackResolutionConsistencyTest {
    private lateinit var db: AuralisDatabase
    private lateinit var tx: RoomReconciliationTransaction

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AuralisDatabase::class.java).build()
        tx = RoomReconciliationTransaction(db)
    }
    @After fun tearDown() = db.close()

    @Test fun rejectsAnActivePointerWhoseSourceBelongsToAnotherTrack() = runBlocking {
        val first = tx.reconcileObservation(playbackObservation("primary:Music/a.flac"), null, 1)
        val second = tx.reconcileObservation(playbackObservation("primary:Music/b.flac", album = "Other Album"), null, 1)
        val otherSource = db.sourceDao().findByTrack(second).single()
        db.sourceDao().clearActiveSource(second)
        db.sourceDao().setActiveSource(TrackActiveSourceEntity(first, otherSource.sourceId))
        try {
            PlaybackMediaItemResolver(db).resolve(MediaItem.Builder().setMediaId(first.toString()).build())
            fail("Mismatched source ownership must be rejected")
        } catch (expected: PlaybackResolutionException) {
            assertTrue(expected.message!!.contains("不属于当前曲目"))
        }
    }

    @Test fun concurrentCommitsNeverMixTrackAndSourceVersions() = runBlocking {
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/a.flac"), null, 1)
        val source = db.sourceDao().findByTrack(trackId).single()
        val track = db.trackDao().findById(trackId)!!
        suspend fun writeVersion(version: Int) = db.withTransaction {
            db.trackDao().update(track.copy(title = "v$version"))
            yield()
            db.sourceDao().update(source.copy(uri = "content://test/v$version", modifiedMs = version.toLong()))
        }
        writeVersion(0)
        val start = CompletableDeferred<Unit>()
        val writer = launch(Dispatchers.IO) {
            start.await()
            repeat(40) { writeVersion(it + 1); yield() }
        }
        val resolver = PlaybackMediaItemResolver(db)
        start.complete(Unit)
        try {
            repeat(40) {
                val item = resolver.resolve(MediaItem.Builder().setMediaId(trackId.toString()).build())
                val version = item.sourceEvidence()!!.modifiedMs
                assertEquals("v$version", item.mediaMetadata.title.toString())
                assertEquals("content://test/v$version", item.localConfiguration!!.uri.toString())
                assertEquals("content://test/v$version", item.playbackTrack()!!.audioSource!!.audioUri)
            }
        } finally {
            writer.cancelAndJoin()
        }
    }

    @Test fun queuedSnapshotSurvivesSourceReplacementAndAnEmptyVisibleLibrary() = runBlocking {
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/a.flac"), null, 1)
        tx.reconcileObservation(playbackObservation("primary:Music/b.flac"), null, 1)
        val requestExtras = Bundle().apply { putString(PlaybackModeMetadata.Mode, PlaybackMode.ShuffleAlbums.key) }
        val request = MediaItem.Builder().setMediaId(trackId.toString())
            .setMediaMetadata(MediaMetadata.Builder().setExtras(requestExtras).build()).build()
        val item = PlaybackMediaItemResolver(db).resolve(request)
        val snapshot = item.playbackTrack()!!
        val originalSource = snapshot.audioSource!!.sourceId
        db.withTransaction {
            val source = db.sourceDao().findById(originalSource)!!
            db.sourceDao().update(source.copy(availabilityState = SourceAvailabilityState.UNREACHABLE))
            tx.refreshActiveSourceForTrack(trackId)
        }
        assertNotEquals(originalSource, db.sourceDao().findActiveSource(trackId)!!.sourceId)
        assertEquals(originalSource, item.playbackTrack()!!.audioSource!!.sourceId)
        db.withTransaction {
            db.sourceDao().findByTrack(trackId).forEach {
                db.sourceDao().update(it.copy(availabilityState = SourceAvailabilityState.MISSING))
            }
            tx.refreshActiveSourceForTrack(trackId)
        }
        assertTrue(LibraryBrowserRepository(db).snapshot.first().tracks.isEmpty())
        assertEquals(snapshot, item.playbackTrack())
        assertEquals(listOf("Artist A", "Artist B"), snapshot.artists)
        assertEquals(2, snapshot.discNumber)
        assertEquals(3, snapshot.trackNumber)
        assertEquals(PlaybackMode.ShuffleAlbums.key, item.mediaMetadata.extras!!.getString(PlaybackModeMetadata.Mode))
        assertNull(request.mediaMetadata.extras!!.getString(PlaybackSourceMetadata.SourceId))
    }

    @Test fun missingDisplayFieldsStayMissingAndDoNotRetainOldExtras() {
        val track = PlaybackTrack(com.bobo.auralis.mobile.library.model.TrackId.random(), "Unknown",
            emptyList(), null, null, 100, 1, 2, null, false)
        val extras = Bundle().apply {
            putPlaybackTrack(track)
            putPlaybackTrack(track.copy(durationMs = null, discNumber = null, trackNumber = null))
        }
        val item = MediaItem.Builder().setMediaId(track.trackId.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Unknown").setExtras(extras).build()).build()
        val snapshot = item.playbackTrack()!!
        assertNull(snapshot.durationMs)
        assertNull(snapshot.discNumber)
        assertNull(snapshot.trackNumber)
        assertNull(snapshot.albumId)
        assertNull(snapshot.audioSource)
        assertTrue(snapshot.artists.isEmpty())
    }

    @Test fun sessionTransportsTheQueuedSourceAndModeChangesKeepTheSnapshot() = runBlocking {
        val trackId = tx.reconcileObservation(playbackObservation("primary:Music/a.flac"), null, 1)
        val resolved = PlaybackMediaItemResolver(db).resolve(MediaItem.Builder().setMediaId(trackId.toString()).build())
        val snapshot = resolved.playbackTrack()!!
        val changed = PlaybackQueueTrack(trackId, snapshot.albumId?.toString(), snapshot.discNumber,
            snapshot.trackNumber, 4).toMediaItem(PlaybackMode.RepeatAll, resolved)
        assertEquals(snapshot, changed.playbackTrack())
        assertEquals(PlaybackMode.RepeatAll.key, changed.mediaMetadata.extras!!.getString(PlaybackModeMetadata.Mode))
        assertEquals(4, changed.mediaMetadata.extras!!.getInt(PlaybackModeMetadata.QueueIndex))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var player: ExoPlayer
        lateinit var session: MediaSession
        var controller: MediaController? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.setMediaItem(changed)
                session = MediaSession.Builder(context, player).build()
            }
            controller = MediaController.Builder(context, session.token).buildAsync().get(10, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                assertEquals(snapshot, controller.connectionState().currentTrack)
                assertEquals(PlaybackMode.RepeatAll, controller.connectionState().playbackMode)
            }
        } finally {
            instrumentation.runOnMainSync {
                controller?.release()
                session.release()
                player.release()
            }
        }
    }
}
