package com.bobo.auralis.mobile.library.reconciliation

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.identity.AlbumIdentity
import com.bobo.auralis.mobile.library.identity.AlbumKey
import com.bobo.auralis.mobile.library.identity.TrackIdentity
import com.bobo.auralis.mobile.library.identity.TrackKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ObservedTrackSource
import com.bobo.auralis.mobile.library.model.ResolvedObservation
import com.bobo.auralis.mobile.library.model.RootReference
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.saf.SafComponents
import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import com.bobo.auralis.mobile.library.saf.SafPath
import com.bobo.auralis.mobile.library.saf.SafRoot
import com.bobo.auralis.mobile.library.scan.AudioFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for Step F reconciliation transactions.
 *
 * Covers requirements in `docs/phase3a/05_RECONCILIATION_TX.md`:
 * - §40: Active metadata update strategy (atomic winner projection)
 * - §61: Invariant verification:
 *   - A active -> A missing -> B active
 *   - Album boundary break detaches track
 *   - Reconcile missing sources with reachable / unreachable roots
 */
@RunWith(AndroidJUnit4::class)
class RoomReconciliationTransactionTest {

    private lateinit var db: AuralisDatabase
    private lateinit var tx: RoomReconciliationTransaction

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuralisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tx = RoomReconciliationTransaction(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createObservation(
        documentId: String,
        rootRef: RootReference,
        relativePath: String,
        title: String,
        album: String,
        artists: List<String>,
        albumArtists: List<String>,
        genres: List<String>,
        trackKeyHash: String,
        albumKeyHash: String,
    ): ResolvedObservation {
        val metadata = AuralisMetadata(
            title = title,
            artists = artists,
            albumArtists = albumArtists,
            album = album,
            genres = genres,
            date = "2024",
            trackNumber = 1,
            trackTotal = 10,
            discNumber = 1,
            discTotal = 1,
            embeddedLyrics = null,
            hasEmbeddedArtwork = false,
            artworkBytes = 0,
            durationMs = 180_000L,
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            mimeType = "audio/flac",
        )

        val safPath = SafPath(SafRoot.Volume("primary"), SafComponents.parseUnix(relativePath))
        val observed = ObservedTrackSource(
            documentKey = SafDocumentKey("com.android.externalstorage.documents", documentId),
            root = rootRef,
            uri = Uri.parse("content://music/$documentId"),
            relativePath = safPath,
            fileName = "$title.flac",
            sizeBytes = 1024L * 1024L,
            modifiedMs = 1000L,
            format = AudioFormat.FLAC,
            metadata = metadata,
        )

        val albumId = AlbumIdentity.from(album = album, albumArtists = albumArtists, date = "2024")
        return ResolvedObservation(
            observation = observed,
            albumIdentity = albumId,
            albumKey = AlbumKey(version = 1, hash = albumKeyHash),
            trackIdentity = TrackIdentity(
                albumIdentity = albumId,
                title = title,
                trackArtists = artists,
                discNumber = 1,
                trackNumber = 1,
            ),
            trackKey = TrackKey(version = 1, hash = trackKeyHash),
            trackKeyStrength = TrackKeyStrength.STRONG,
        )
    }

    @Test
    fun atomicActiveSourceTransition_A_active_to_A_missing_to_B_active() = runBlocking {
        val rootDao = db.libraryRootDao()
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()
        val graphDao = db.graphDao()

        val root0Id = LibraryRootId.random()
        val root1Id = LibraryRootId.random()

        rootDao.insert(
            LibraryRootEntity(
                rootId = root0Id,
                treeUri = "content://music/root0",
                displayPath = "/Music0",
                priority = 0, // Higher priority
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = 1000L,
                lastAttemptedScanAt = 1000L,
                createdAt = 1000L,
            )
        )
        rootDao.insert(
            LibraryRootEntity(
                rootId = root1Id,
                treeUri = "content://music/root1",
                displayPath = "/Music1",
                priority = 1, // Lower priority
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = 1000L,
                lastAttemptedScanAt = 1000L,
                createdAt = 1000L,
            )
        )

        val rootRef0 = RootReference(treeUri = "content://music/root0", priority = 0)
        val rootRef1 = RootReference(treeUri = "content://music/root1", priority = 1)

        // Source A in Root 0 (Priority 0)
        val obsA = createObservation(
            documentId = "doc_A",
            rootRef = rootRef0,
            relativePath = "A/song.flac",
            title = "Track A",
            album = "Album 1",
            artists = listOf("Artist A"),
            albumArtists = listOf("Artist A"),
            genres = listOf("Rock"),
            trackKeyHash = "common_track_key",
            albumKeyHash = "album_1_key",
        )

        // Source B in Root 1 (Priority 1), same trackKey (duplicate)
        val obsB = createObservation(
            documentId = "doc_B",
            rootRef = rootRef1,
            relativePath = "B/song.flac",
            title = "Track B",
            album = "Album 1",
            artists = listOf("Artist B"),
            albumArtists = listOf("Artist A"),
            genres = listOf("Pop"),
            trackKeyHash = "common_track_key",
            albumKeyHash = "album_1_key",
        )

        // Reconcile Source A
        val trackId = tx.reconcileObservation(obsA, root0Id, scanSessionId = 1L)

        // Reconcile Source B -> strong match attaches to existing Track
        val attachedTrackId = tx.reconcileObservation(obsB, root1Id, scanSessionId = 1L)
        assertEquals(trackId, attachedTrackId)

        // Since Root 0 has priority 0 vs Root 1 priority 1, Source A must be active winner
        val active1 = sourceDao.findActiveSource(trackId)
        assertNotNull(active1)
        val sourceA = sourceDao.findByDocumentKey("com.android.externalstorage.documents", "doc_A")
        val sourceB = sourceDao.findByDocumentKey("com.android.externalstorage.documents", "doc_B")
        assertEquals(sourceA?.sourceId, active1?.sourceId)

        // Verify logical track projected from A
        val trackSnapshot1 = trackDao.findById(trackId)
        assertEquals("Track A", trackSnapshot1?.title)
        val artists1 = graphDao.trackArtists(trackId)
        assertEquals(1, artists1.size)
        assertEquals("Artist A", graphDao.findArtistById(artists1[0].artistId)?.displayName)

        // Now run reconcileMissingSources in scan 2 where Root 0 was clean and did not see Source A
        tx.reconcileMissingSources(
            scanSessionId = 2L,
            rootIdToReachable = mapOf(root0Id to true, root1Id to true),
        )

        // Source A is now MISSING
        assertEquals(SourceAvailabilityState.MISSING, sourceDao.findById(sourceA!!.sourceId)?.availabilityState)

        // §40, §61: Source B must automatically and atomically take over as active winner!
        val active2 = sourceDao.findActiveSource(trackId)
        assertEquals(sourceB?.sourceId, active2?.sourceId)

        // All logical relations projected to B
        val trackSnapshot2 = trackDao.findById(trackId)
        assertEquals("Track B", trackSnapshot2?.title)
        val artists2 = graphDao.trackArtists(trackId)
        assertEquals(1, artists2.size)
        assertEquals("Artist B", graphDao.findArtistById(artists2[0].artistId)?.displayName)
        val genres2 = graphDao.trackGenres(trackId)
        assertEquals(1, genres2.size)
        assertEquals("Pop", graphDao.findGenreById(genres2[0].genreId)?.displayName)
    }

    @Test
    fun albumHardBoundaryBreakCreatesNewTrackAndDetachesOld() = runBlocking {
        val rootDao = db.libraryRootDao()
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()

        val rootId = LibraryRootId.random()
        rootDao.insert(
            LibraryRootEntity(
                rootId = rootId,
                treeUri = "content://music/root",
                displayPath = "/Music",
                priority = 0,
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = 1000L,
                lastAttemptedScanAt = 1000L,
                createdAt = 1000L,
            )
        )
        val rootRef = RootReference("content://music/root", 0)

        val obs1 = createObservation(
            documentId = "doc_boundary",
            rootRef = rootRef,
            relativePath = "song.flac",
            title = "Song",
            album = "Album One",
            artists = listOf("Artist"),
            albumArtists = listOf("Artist"),
            genres = listOf("Pop"),
            trackKeyHash = "hash1",
            albumKeyHash = "album_1_hash",
        )

        val trackId1 = tx.reconcileObservation(obs1, rootId, scanSessionId = 1L)
        assertNotNull(sourceDao.findActiveSource(trackId1))

        // Same documentId, but metadata edited to a completely different Album!
        val obs2 = createObservation(
            documentId = "doc_boundary",
            rootRef = rootRef,
            relativePath = "song.flac",
            title = "Song",
            album = "Album Two",
            artists = listOf("Artist"),
            albumArtists = listOf("Artist"),
            genres = listOf("Pop"),
            trackKeyHash = "hash2",
            albumKeyHash = "album_2_hash",
        )

        val trackId2 = tx.reconcileObservation(obs2, rootId, scanSessionId = 2L)

        // §30: Album boundary broken -> new TrackId must be assigned
        assertEquals(false, trackId1 == trackId2)

        // Old track lost its only source -> becomes tombstone (active source is null)
        assertNull(sourceDao.findActiveSource(trackId1))
        assertNotNull(trackDao.findById(trackId1)) // Track survives as tombstone

        // New track is active with this source
        val active2 = sourceDao.findActiveSource(trackId2)
        assertNotNull(active2)
    }
}
