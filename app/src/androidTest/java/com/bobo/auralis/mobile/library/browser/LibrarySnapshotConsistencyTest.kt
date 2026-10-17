package com.bobo.auralis.mobile.library.browser

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.*
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySnapshotConsistencyTest {
    @Test
    fun everyEmissionContainsOneCommittedGraphAndStandaloneChangesRefresh() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AuralisDatabase::class.java,
        ).build()
        val snapshots = Channel<LibrarySnapshot>(Channel.UNLIMITED)
        val collector = launch { LibraryBrowserRepository(db).snapshot.collect { snapshots.send(it) } }
        suspend fun nextMatching(predicate: (LibrarySnapshot) -> Boolean): LibrarySnapshot = withTimeout(10_000) {
            var snapshot = snapshots.receive()
            while (!predicate(snapshot)) snapshot = snapshots.receive()
            snapshot
        }
        try {
            assertTrue(nextMatching { true }.tracks.isEmpty())
            val albumId = AlbumId.random()
            val trackId = TrackId.random()
            val artistId = ArtistId.random()
            val genreId = GenreId.random()
            db.withTransaction {
                db.graphDao().insertAlbum(AlbumEntity(albumId, 1, "album", "v0", null, 0, 0))
                db.trackDao().insert(TrackEntity(trackId, 1, "track", TrackKeyStrength.STRONG,
                    albumId, "v0", null, 1, null, 1, null, 0, 0))
                db.graphDao().insertArtist(ArtistEntity(artistId, 1, "artist", "v0"))
                db.graphDao().insertGenre(GenreEntity(genreId, 1, "genre", "v0"))
                db.graphDao().insertAlbumArtists(listOf(AlbumArtistCrossRef(albumId, artistId, 0)))
                db.graphDao().insertTrackArtists(listOf(TrackArtistCrossRef(trackId, artistId, 0)))
                db.graphDao().insertTrackGenres(listOf(TrackGenreCrossRef(trackId, genreId, 0)))
            }
            fun assertConsistent(snapshot: LibrarySnapshot) {
                val track = snapshot.tracks.single()
                val album = snapshot.albums.single()
                assertEquals(track.title, album.title)
                assertEquals(listOf(track.title), track.artists)
                assertEquals(listOf(track.title), album.albumArtists)
                assertEquals(listOf(track.title), track.genres)
                assertEquals(track, album.tracks.single())
                assertEquals(track, snapshot.genres.single().tracks.single())
            }
            assertConsistent(nextMatching { it.tracks.isNotEmpty() })
            repeat(30) { index ->
                val version = "v${index + 1}"
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    sql.execSQL("UPDATE album SET title = ?", arrayOf(version))
                    yield()
                    sql.execSQL("UPDATE track SET title = ?", arrayOf(version))
                    sql.execSQL("UPDATE artist SET displayName = ?", arrayOf(version))
                    yield()
                    sql.execSQL("UPDATE genre SET displayName = ?", arrayOf(version))
                }
                nextMatching {
                    assertConsistent(it) // Check intermediate emissions as well as the final one.
                    it.tracks.single().title == version
                }
            }
            db.withTransaction {
                db.openHelper.writableDatabase.execSQL("UPDATE artist SET displayName = 'artist-only'")
            }
            val artistUpdate = nextMatching { it.tracks.single().artists == listOf("artist-only") }
            assertEquals(listOf("artist-only"), artistUpdate.albums.single().albumArtists)
            db.withTransaction {
                db.openHelper.writableDatabase.execSQL("UPDATE genre SET displayName = 'genre-only'")
            }
            assertEquals("genre-only", nextMatching {
                it.tracks.single().genres == listOf("genre-only")
            }.genres.single().displayName)
        } finally {
            collector.cancelAndJoin()
            db.close()
        }
    }
}
