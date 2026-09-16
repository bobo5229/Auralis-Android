package com.bobo.auralis.mobile.library.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.db.entity.AlbumArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.ArtistEntity
import com.bobo.auralis.mobile.library.db.entity.GenreEntity
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackActiveSourceEntity
import com.bobo.auralis.mobile.library.db.entity.TrackArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackGenreCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.library.scan.AudioFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for Room database:
 * Verifies:
 * 1. Primary Keys (@JvmInline value classes resolved to TEXT)
 * 2. Foreign Keys & Cascading/SetNull deletes (Section 49)
 * 3. DAO parameter passing with value classes
 * 4. Query return mappings
 */
@RunWith(AndroidJUnit4::class)
class AuralisDatabaseTest {

    private lateinit var db: AuralisDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuralisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun primaryKeyAndDaoQueryRoundTrip() = runBlocking {
        val rootDao = db.libraryRootDao()
        val rootId = LibraryRootId.random()
        val root = LibraryRootEntity(
            rootId = rootId,
            treeUri = "content://com.android.externalstorage.documents/tree/music",
            displayPath = "/Music",
            priority = 1,
            availabilityState = RootAvailabilityState.AVAILABLE,
            lastSuccessfulScanAt = 1000L,
            lastAttemptedScanAt = 1000L,
            createdAt = 1000L,
        )
        rootDao.insert(root)

        val retrieved = rootDao.findById(rootId)
        assertNotNull(retrieved)
        assertEquals(rootId, retrieved?.rootId)
        assertEquals("/Music", retrieved?.displayPath)
        assertEquals(RootAvailabilityState.AVAILABLE, retrieved?.availabilityState)
    }

    @Test
    fun foreignKeyEnforcementOnSourceInsertWithoutTrackFails() = runBlocking {
        val sourceDao = db.sourceDao()
        val rootDao = db.libraryRootDao()

        val rootId = LibraryRootId.random()
        rootDao.insert(
            LibraryRootEntity(
                rootId = rootId,
                treeUri = "content://music",
                displayPath = "/Music",
                priority = 1,
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = null,
                lastAttemptedScanAt = null,
                createdAt = 1000L,
            )
        )

        val nonExistentTrackId = TrackId.random()
        val source = TrackSourceEntity(
            sourceId = TrackSourceId.random(),
            trackId = nonExistentTrackId,
            rootId = rootId,
            provider = "com.android.externalstorage.documents",
            documentId = "doc-1",
            uri = "content://music/doc-1",
            relativePath = "track1.mp3",
            fileName = "track1.mp3",
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 1024L,
            modifiedMs = 1000L,
            durationMs = 120_000L,
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            availabilityState = SourceAvailabilityState.AVAILABLE,
            parseState = ParseState.PARSED,
            playabilityState = PlayabilityState.PLAYABLE,
            lastSeenScanId = 1L,
            lastSeenAt = 1000L,
            failureStage = null,
            failureCode = null,
            failureMessage = null,
        )

        try {
            sourceDao.insert(source)
            fail("Expected SQLiteConstraintException when inserting TrackSource with non-existent trackId")
        } catch (e: SQLiteConstraintException) {
            // Success: foreign key constraint enforced
        }
    }

    @Test
    fun foreignKeyCascadeDeleteOnTrackCascadesToSourceMetadataAndActiveSource() = runBlocking {
        val rootDao = db.libraryRootDao()
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()

        val rootId = LibraryRootId.random()
        rootDao.insert(
            LibraryRootEntity(
                rootId = rootId,
                treeUri = "content://music",
                displayPath = "/Music",
                priority = 1,
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = null,
                lastAttemptedScanAt = null,
                createdAt = 1000L,
            )
        )

        val trackId = TrackId.random()
        val track = TrackEntity(
            trackId = trackId,
            trackKeyVersion = 1,
            trackKeyHash = "hash1",
            trackKeyStrength = TrackKeyStrength.STRONG,
            albumId = null,
            title = "Test Track",
            date = "2024-01-01",
            trackNumber = 1,
            trackTotal = 10,
            discNumber = 1,
            discTotal = 1,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        trackDao.insert(track)

        val sourceId = TrackSourceId.random()
        val source = TrackSourceEntity(
            sourceId = sourceId,
            trackId = trackId,
            rootId = rootId,
            provider = "com.android.externalstorage.documents",
            documentId = "doc-1",
            uri = "content://music/doc-1",
            relativePath = "track1.mp3",
            fileName = "track1.mp3",
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 1024L,
            modifiedMs = 1000L,
            durationMs = 120_000L,
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            availabilityState = SourceAvailabilityState.AVAILABLE,
            parseState = ParseState.PARSED,
            playabilityState = PlayabilityState.PLAYABLE,
            lastSeenScanId = 1L,
            lastSeenAt = 1000L,
            failureStage = null,
            failureCode = null,
            failureMessage = null,
        )
        sourceDao.insert(source)

        val metadata = SourceMetadataEntity(
            sourceId = sourceId,
            title = "Test Track",
            albumTitle = null,
            date = "2024-01-01",
            trackNumber = 1,
            trackTotal = 10,
            discNumber = 1,
            discTotal = 1,
            artists = listOf("Artist 1"),
            albumArtists = emptyList(),
            genres = listOf("Pop"),
            durationMs = 120_000L,
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            mimeType = "audio/mpeg",
            hasEmbeddedArtwork = false,
            trackKeyVersion = 1,
            trackKeyHash = "hash1",
            trackKeyStrength = TrackKeyStrength.STRONG,
            albumKeyVersion = null,
            albumKeyHash = null,
        )
        sourceDao.insertMetadata(metadata)

        sourceDao.setActiveSource(TrackActiveSourceEntity(trackId = trackId, sourceId = sourceId))

        // Verify everything exists
        assertNotNull(sourceDao.findById(sourceId))
        assertNotNull(sourceDao.findMetadata(sourceId))
        assertNotNull(sourceDao.findActiveSource(trackId))

        // Delete track -> cascades to TrackSource, which cascades to SourceMetadata and TrackActiveSource
        trackDao.deleteById(trackId)

        assertNull(trackDao.findById(trackId))
        assertNull(sourceDao.findById(sourceId))
        assertNull(sourceDao.findMetadata(sourceId))
        assertNull(sourceDao.findActiveSource(trackId))
    }

    @Test
    fun deletingAlbumSetsAlbumIdNullOnTrack() = runBlocking {
        val graphDao = db.graphDao()
        val trackDao = db.trackDao()

        val albumId = AlbumId.random()
        val album = AlbumEntity(
            albumId = albumId,
            albumKeyVersion = 1,
            albumKeyHash = "albumhash1",
            title = "Test Album",
            date = "2024",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        graphDao.insertAlbum(album)

        val trackId = TrackId.random()
        val track = TrackEntity(
            trackId = trackId,
            trackKeyVersion = 1,
            trackKeyHash = "hash2",
            trackKeyStrength = TrackKeyStrength.STRONG,
            albumId = albumId,
            title = "Track on Album",
            date = "2024",
            trackNumber = 1,
            trackTotal = 10,
            discNumber = 1,
            discTotal = 1,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        trackDao.insert(track)

        // Verify track is linked to album
        assertEquals(albumId, trackDao.findById(trackId)?.albumId)

        // When album is deleted via direct SQL query
        db.compileStatement("DELETE FROM album WHERE albumId = '${albumId.value}'").executeUpdateDelete()

        // Track should survive with albumId SET NULL
        val survivingTrack = trackDao.findById(trackId)
        assertNotNull(survivingTrack)
        assertNull(survivingTrack?.albumId)
    }

    @Test
    fun graphDaoRelationsAndOrderingPreserved() = runBlocking {
        val graphDao = db.graphDao()
        val trackDao = db.trackDao()

        val trackId = TrackId.random()
        trackDao.insert(
            TrackEntity(
                trackId = trackId,
                trackKeyVersion = 1,
                trackKeyHash = "hash3",
                trackKeyStrength = TrackKeyStrength.STRONG,
                albumId = null,
                title = "Collaboration Track",
                date = "2024",
                trackNumber = 1,
                trackTotal = 1,
                discNumber = 1,
                discTotal = 1,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        val artist1 = ArtistId.random()
        val artist2 = ArtistId.random()
        graphDao.insertArtist(ArtistEntity(artist1, 1, "artist_1", "Artist 1"))
        graphDao.insertArtist(ArtistEntity(artist2, 1, "artist_2", "Artist 2"))

        val genre1 = GenreId.random()
        graphDao.insertGenre(GenreEntity(genre1, 1, "electronic", "Electronic"))

        graphDao.insertTrackArtists(
            listOf(
                TrackArtistCrossRef(trackId, artist1, position = 0),
                TrackArtistCrossRef(trackId, artist2, position = 1),
            )
        )
        graphDao.insertTrackGenres(
            listOf(
                TrackGenreCrossRef(trackId, genre1, position = 0),
            )
        )

        val artists = graphDao.trackArtists(trackId)
        assertEquals(2, artists.size)
        assertEquals(artist1, artists[0].artistId)
        assertEquals(0, artists[0].position)
        assertEquals(artist2, artists[1].artistId)
        assertEquals(1, artists[1].position)

        val genres = graphDao.trackGenres(trackId)
        assertEquals(1, genres.size)
        assertEquals(genre1, genres[0].genreId)
    }
}
