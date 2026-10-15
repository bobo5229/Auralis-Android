package com.bobo.auralis.mobile.library.reconciliation

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.cache.CacheClassification
import com.bobo.auralis.mobile.library.cache.SourceMetadataCache
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.identity.AlbumIdentity
import com.bobo.auralis.mobile.library.identity.AlbumKey
import com.bobo.auralis.mobile.library.identity.IdentityDerivation
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
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.playback.PlaybackSourceEvidence
import com.bobo.auralis.mobile.playback.PlaybackSourceStateRepository
import com.bobo.auralis.mobile.playback.PlaybackMediaItemResolver
import com.bobo.auralis.mobile.playback.sourceEvidence
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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

        // Only A is absent in scan 2; B was rediscovered successfully.
        tx.reconcileObservation(obsB, root1Id, scanSessionId = 2L)
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

    private fun regressionObservation(documentId: String): ResolvedObservation {
        val observation = createObservation(
            documentId = documentId,
            rootRef = RootReference("content://music/root", 0),
            relativePath = "$documentId/song.flac",
            title = "Song",
            album = "Album",
            artists = listOf("Artist A", "Artist B"),
            albumArtists = listOf("Album Artist"),
            genres = listOf("Rock", "Pop"),
            trackKeyHash = "unused",
            albumKeyHash = "unused",
        ).observation
        val metadata = observation.metadata.copy(date = "2024-01-01")
        return ResolvedObservation.of(observation.copy(metadata = metadata), IdentityDerivation.derive(metadata))
    }

    private fun withMetadata(original: ResolvedObservation, metadata: AuralisMetadata): ResolvedObservation =
        ResolvedObservation.of(
            original.observation.copy(metadata = metadata, modifiedMs = original.observation.modifiedMs + 1),
            IdentityDerivation.derive(metadata),
        )

    @Test
    fun sameActiveSourceRefreshesScalarsKeyAndOrderedRelations() = runBlocking {
        val before = regressionObservation("A")
        val trackId = tx.reconcileObservation(before, null, 1L)
        val activeBefore = db.sourceDao().findActiveSource(trackId)
        val after = withMetadata(before, before.observation.metadata.copy(
            title = "Updated Song",
            artists = listOf("Artist C", "Artist D"),
            genres = listOf("Jazz", "Soul"),
            discNumber = 2,
            trackNumber = 3,
        ))

        assertEquals(trackId, tx.reconcileObservation(after, null, 2L))
        assertEquals(activeBefore, db.sourceDao().findActiveSource(trackId))
        val track = db.trackDao().findById(trackId)!!
        assertEquals("Updated Song", track.title)
        assertEquals(after.trackKey.hash, track.trackKeyHash)
        assertEquals(2, track.discNumber)
        assertEquals(3, track.trackNumber)
        val graph = db.graphDao()
        assertEquals(listOf("Artist C", "Artist D"), graph.trackArtists(trackId).map {
            graph.findArtistById(it.artistId)!!.displayName
        })
        assertEquals(listOf("Jazz", "Soul"), graph.trackGenres(trackId).map {
            graph.findGenreById(it.genreId)!!.displayName
        })
    }

    @Test
    fun genreOnlyEditRefreshesSameActiveSourceWithoutChangingIdentity() = runBlocking {
        val before = regressionObservation("A")
        val trackId = tx.reconcileObservation(before, null, 1L)
        val after = withMetadata(before, before.observation.metadata.copy(genres = listOf("Jazz", "Soul")))
        assertEquals(before.trackKey, after.trackKey)
        tx.reconcileObservation(after, null, 2L)
        val graph = db.graphDao()
        assertEquals(listOf("Jazz", "Soul"), graph.trackGenres(trackId).map {
            graph.findGenreById(it.genreId)!!.displayName
        })
    }

    @Test
    fun updatedInactiveSourceDoesNotMixItsGenresIntoTheWinner() = runBlocking {
        val winner = regressionObservation("A")
        val backup = regressionObservation("B")
        val trackId = tx.reconcileObservation(winner, null, 1L)
        assertEquals(trackId, tx.reconcileObservation(backup, null, 1L))
        val after = withMetadata(backup, backup.observation.metadata.copy(genres = listOf("Jazz", "Soul")))
        tx.reconcileObservation(after, null, 2L)
        val graph = db.graphDao()
        assertEquals(listOf("Rock", "Pop"), graph.trackGenres(trackId).map {
            graph.findGenreById(it.genreId)!!.displayName
        })
    }

    @Test
    fun activeKeyEditIsVisibleToTheNextObservationInTheSameBatch() = runBlocking {
        val before = regressionObservation("A")
        val trackId = tx.reconcileObservation(before, null, 1L)
        val after = withMetadata(before, before.observation.metadata.copy(title = "Updated Song"))
        val duplicate = withMetadata(regressionObservation("B"), after.observation.metadata)
        tx.reconcileObservationsBatch(
            listOf(
                RoomReconciliationTransaction.BatchItem(after, null),
                RoomReconciliationTransaction.BatchItem(duplicate, null),
            ),
            scanSessionId = 2L,
        )
        assertEquals(1, db.trackDao().count())
        assertEquals(2, db.sourceDao().findByTrack(trackId).size)
        assertEquals(after.trackKey.hash, db.trackDao().findById(trackId)!!.trackKeyHash)
    }

    @Test
    fun cacheHitReevaluatesLegacyStrengthWithoutUsingFileNameAsTitle() = runBlocking {
        val observation = regressionObservation("A")
        val trackId = tx.reconcileObservation(observation, null, 1L)
        val source = db.sourceDao().findByTrack(trackId).single()
        val metadata = db.sourceDao().findMetadata(source.sourceId)!!.copy(
            title = null, discNumber = null, trackKeyStrength = TrackKeyStrength.STRONG,
        )
        val hydrated = SourceMetadataCache.hydrate(
            hit = CacheClassification.Hit(source, metadata),
            documentKey = observation.observation.documentKey,
            rootTreeUri = observation.observation.root.treeUri,
            rootPriority = 0,
            uri = observation.observation.uri,
            relativePath = observation.observation.relativePath,
            fileName = source.fileName,
            sizeBytes = source.sizeBytes,
            modifiedMs = source.modifiedMs,
            format = source.format,
        )
        assertNull(hydrated.trackIdentity.title)
        assertEquals(TrackKeyStrength.WEAK, hydrated.trackKeyStrength)
        // The cached rule correction must reach the logical track on a normal rescan.
        tx.reconcileObservation(hydrated, null, 2L)
        assertEquals(TrackKeyStrength.WEAK, db.trackDao().findById(trackId)!!.trackKeyStrength)
    }

    @Test
    fun repairedFileClearsOldDecoderEvidenceButUnchangedFileKeepsIt() = runBlocking {
        val before = regressionObservation("A")
        val trackId = tx.reconcileObservation(before, null, 1L)
        val source = db.sourceDao().findByTrack(trackId).single()
        val evidence = PlaybackSourceEvidence(trackId, source.sourceId, source.modifiedMs, source.sizeBytes)
        val repository = PlaybackSourceStateRepository(db)
        repository.markUnplayable(evidence, PlaybackException("bad encoding", null, PlaybackException.ERROR_CODE_DECODING_FAILED))
        tx.reconcileObservation(before, null, 2L)
        assertEquals(PlayabilityState.UNPLAYABLE, db.sourceDao().findById(source.sourceId)!!.playabilityState)
        assertNull(db.sourceDao().findActiveSource(trackId))

        val repaired = withMetadata(before, before.observation.metadata)
        tx.reconcileObservation(repaired, null, 3L)
        assertEquals(PlayabilityState.UNKNOWN, db.sourceDao().findById(source.sourceId)!!.playabilityState)
        assertEquals(source.sourceId, db.sourceDao().findActiveSource(trackId)?.sourceId)
        // Delayed decoder evidence for the old bytes must not poison the repair.
        repository.markUnplayable(evidence, PlaybackException("stale failure", null, PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(PlayabilityState.UNKNOWN, db.sourceDao().findById(source.sourceId)!!.playabilityState)
    }

    @Test
    fun evidenceForPreviousActiveSourceNeverMarksTheReplacementSource() = runBlocking {
        val trackId = tx.reconcileObservation(regressionObservation("A"), null, 1L)
        tx.reconcileObservation(regressionObservation("B"), null, 1L)
        val sourceA = db.sourceDao().findByDocumentKey("com.android.externalstorage.documents", "A")!!
        val sourceB = db.sourceDao().findByDocumentKey("com.android.externalstorage.documents", "B")!!
        val evidenceA = PlaybackSourceEvidence(trackId, sourceA.sourceId, sourceA.modifiedMs, sourceA.sizeBytes)
        val repository = PlaybackSourceStateRepository(db)
        val failure = PlaybackException("bad encoding", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        repository.markUnplayable(evidenceA, failure)
        assertEquals(sourceB.sourceId, db.sourceDao().findActiveSource(trackId)?.sourceId)
        repository.markUnplayable(evidenceA, failure)
        assertEquals(PlayabilityState.UNKNOWN, db.sourceDao().findById(sourceB.sourceId)!!.playabilityState)
        assertEquals(sourceB.sourceId, db.sourceDao().findActiveSource(trackId)?.sourceId)
    }

    @Test
    fun resolvedMediaItemCarriesTheActualSourceVersionAlongsideQueueMetadata() = runBlocking {
        val trackId = tx.reconcileObservation(regressionObservation("A"), null, 1L)
        val source = db.sourceDao().findByTrack(trackId).single()
        val extras = android.os.Bundle().apply { putString("queue-test", "preserved") }
        val request = MediaItem.Builder().setMediaId(trackId.toString())
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setExtras(extras).build()).build()
        val resolved = PlaybackMediaItemResolver(db).resolve(request)
        assertEquals(PlaybackSourceEvidence(trackId, source.sourceId, source.modifiedMs, source.sizeBytes), resolved.sourceEvidence())
        assertEquals("preserved", resolved.mediaMetadata.extras?.getString("queue-test"))
        assertNull(request.mediaMetadata.extras?.getString("com.bobo.auralis.playback.SOURCE_ID"))
    }
}
