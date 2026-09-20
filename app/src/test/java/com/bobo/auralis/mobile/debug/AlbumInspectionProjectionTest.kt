package com.bobo.auralis.mobile.debug

import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class AlbumInspectionProjectionTest {

    @Test
    fun `same title releases stay separate because grouping uses AlbumId`() {
        val firstRelease = albumId(1)
        val secondRelease = albumId(2)

        val albums = buildAlbumInspectionItems(
            listOf(
                inspection(trackId = 1, albumId = firstRelease, albumTitle = "Album", date = "2024-01-01"),
                inspection(trackId = 2, albumId = secondRelease, albumTitle = "Album", date = "2025-01-01"),
            ),
        )

        assertEquals(2, albums.size)
        assertEquals(listOf(firstRelease, secondRelease), albums.map { it.albumId })
        assertEquals(listOf("2024-01-01", "2025-01-01"), albums.map { it.date })
    }

    @Test
    fun `album tracks sort by disc then track with missing positions last`() {
        val albumId = albumId(1)

        val album = buildAlbumInspectionItems(
            listOf(
                inspection(trackId = 1, albumId = albumId, title = "Missing"),
                inspection(trackId = 2, albumId = albumId, title = "Disc two", disc = 2, track = 1),
                inspection(trackId = 3, albumId = albumId, title = "Second", disc = 1, track = 2),
                inspection(trackId = 4, albumId = albumId, title = "First", disc = 1, track = 1),
            ),
        ).single()

        assertEquals(listOf("First", "Second", "Disc two", "Missing"), album.tracks.map { it.track.title })
        assertEquals(listOf("Album Artist"), album.albumArtists)
        assertEquals(3, album.activeTrackCount)
    }

    @Test
    fun `albumless tracks do not create a shared unknown album`() {
        val albums = buildAlbumInspectionItems(
            listOf(
                inspection(trackId = 1, albumId = null, albumTitle = null),
                inspection(trackId = 2, albumId = null, albumTitle = null),
            ),
        )

        assertEquals(emptyList<AlbumInspectionItem>(), albums)
        assertNull(inspection(trackId = 3, albumId = null, albumTitle = null).track.albumId)
    }

    private fun inspection(
        trackId: Int,
        albumId: AlbumId?,
        albumTitle: String? = "Album",
        date: String? = "2024-01-01",
        title: String = "Track $trackId",
        disc: Int? = null,
        track: Int? = null,
    ): TrackInspectionItem = TrackInspectionItem(
        track = TrackEntity(
            trackId = TrackId(uuid(trackId)),
            trackKeyVersion = 1,
            trackKeyHash = "key-$trackId",
            trackKeyStrength = TrackKeyStrength.STRONG,
            albumId = albumId,
            title = title,
            date = date,
            trackNumber = track,
            trackTotal = null,
            discNumber = disc,
            discTotal = null,
            createdAt = 0,
            updatedAt = 0,
        ),
        activeSourceId = if (title == "Missing") null else TrackSourceId(uuid(trackId + 100)),
        sources = emptyList(),
        albumTitle = albumTitle,
        albumDate = date,
        albumArtists = listOf("Album Artist"),
        artists = listOf("Track Artist"),
        genres = emptyList(),
    )

    private fun albumId(value: Int): AlbumId = AlbumId(uuid(value))

    private fun uuid(value: Int): UUID = UUID(0, value.toLong())
}
