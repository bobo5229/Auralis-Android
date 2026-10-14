package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSearchTest {
    private val firstAlbum = AlbumId.random()
    private val secondAlbum = AlbumId.random()
    private val first = song(firstAlbum, "夜曲", listOf("Singer", "Guest"))
    private val second = song(secondAlbum, "Morning", emptyList())
    private val missing = song(null, "Unavailable", emptyList()).copy(isPlayable = false)
    private val tracks = listOf(first, second, missing)
    private val albums = listOf(
        LibraryAlbum(firstAlbum, "Shared", listOf("Band", "Producer"), null, listOf(first), null),
        LibraryAlbum(secondAlbum, "Shared", listOf("Another"), null, listOf(second), null),
    ).associateBy { it.albumId.toString() }

    @Test fun matchesAllFourFieldsIncludingEveryArtist() {
        for (query in listOf("夜", "sING", "guest", "producer", "band")) {
            assertEquals(listOf(first), filterSongTracks(tracks, albums, query))
        }
        assertEquals(listOf(first, second), filterSongTracks(tracks, albums, " shared "))
        assertEquals(listOf(second), filterSongTracks(tracks, albums, "another"))
    }

    @Test fun blankQueryAndFilteringPreserveInputOrder() {
        assertEquals(tracks, filterSongTracks(tracks, albums, "  "))
        assertEquals(listOf(second, first), filterSongTracks(tracks.reversed(), albums, "shared"))
    }

    @Test fun simplifiedQueriesMatchTraditionalTextAcrossAllFourFields() {
        val traditional = first.copy(title = "後來", artists = listOf("陳奕迅", "張學友"), albumTitle = "樂與怒")
        val albumMap = mapOf(firstAlbum.toString() to albums.getValue(firstAlbum.toString()).copy(
            albumArtists = listOf("華語群星", "臺灣樂團"),
        ))
        for (query in listOf("后来", "陈奕迅", "张学友", "乐与怒", "华语", "台湾乐团")) {
            assertEquals(listOf(traditional), filterSongTracks(listOf(traditional), albumMap, query))
        }
        assertEquals("後來", traditional.title)
    }

    @Test fun traditionalQueriesAlsoMatchSimplifiedAndMixedText() {
        val mixed = first.copy(title = "后来愛過你 Live")
        assertEquals(listOf(mixed), filterSongTracks(listOf(mixed), albums, "後來爱过你 live"))
        assertEquals("头发与发光", SongSearchText.normalize("頭髮與發光"))
        assertEquals("乐😀𠮷", SongSearchText.normalize("樂😀𠮷"))
        assertTrue(filterSongTracks(listOf(first.copy(title = "鐘聲")), albums, "终生").isEmpty())
    }

    @Test fun missingMetadataAndUnavailableTracksRemainSafe() {
        assertEquals(listOf(missing), filterSongTracks(tracks, albums, "unavailable"))
        assertTrue(filterSongTracks(tracks, emptyMap(), "producer").isEmpty())
        assertTrue(filterSongTracks(tracks, albums, "未知").isEmpty())
        assertTrue(filterSongTracks(tracks, albums, "Rock").isEmpty())
        assertTrue(filterSongTracks(emptyList(), albums, "night").isEmpty())
    }

    private fun song(albumId: AlbumId?, title: String, artists: List<String>) = LibraryTrack(
        trackId = TrackId.random(), albumId = albumId, title = title, artists = artists,
        albumTitle = if (albumId != null) "Shared" else null, durationMs = null,
        discNumber = null, trackNumber = null, isPlayable = true,
        unavailableReason = null, artworkSource = null,
    )
}
