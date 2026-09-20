package com.bobo.auralis.mobile.library.browser

import com.bobo.auralis.mobile.library.db.dao.ActiveTrackSourceRow
import com.bobo.auralis.mobile.library.db.dao.AlbumArtistNameRow
import com.bobo.auralis.mobile.library.db.dao.TrackArtistNameRow
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowserProjectionTest {
    @Test
    fun `same-title releases stay separate and album artist never falls back to track artist`() {
        val firstAlbum = album("Shared", "2024-01-01")
        val secondAlbum = album("Shared", "2025-01-01")
        val firstTrack = track(firstAlbum.albumId, title = "One")
        val secondTrack = track(secondAlbum.albumId, title = "Two")
        val firstAlbumArtist = ArtistId.random()
        val trackArtist = ArtistId.random()

        val snapshot = projectLibrarySnapshot(
            albums = listOf(firstAlbum, secondAlbum),
            tracks = listOf(firstTrack, secondTrack),
            albumArtistRows = listOf(
                AlbumArtistNameRow(firstAlbum.albumId, firstAlbumArtist, 0, "Album Owner"),
            ),
            trackArtistRows = listOf(
                TrackArtistNameRow(secondTrack.trackId, trackArtist, 0, "Track Performer"),
            ),
            activeRows = listOf(active(firstTrack.trackId), active(secondTrack.trackId)),
        )

        assertEquals(2, snapshot.albums.size)
        assertEquals("Album Owner", snapshot.albums.first { it.albumId == firstAlbum.albumId }.albumArtistLabel)
        assertEquals("未知 Album Artist", snapshot.albums.first { it.albumId == secondAlbum.albumId }.albumArtistLabel)
        assertEquals("Track Performer", snapshot.tracks.first { it.trackId == secondTrack.trackId }.artistLabel)
    }

    @Test
    fun `album artwork prefers an embedded source over an earlier track without one`() {
        val album = album("Artwork", "2026-01-01")
        val first = track(album.albumId, title = "First", number = 1)
        val second = track(album.albumId, title = "Second", number = 2)

        val snapshot = projectLibrarySnapshot(
            albums = listOf(album),
            tracks = listOf(first, second),
            albumArtistRows = emptyList(),
            trackArtistRows = emptyList(),
            activeRows = listOf(
                active(first.trackId, hasEmbeddedArtwork = false),
                active(second.trackId, hasEmbeddedArtwork = true),
            ),
        )

        assertEquals(second.trackId, snapshot.albums.single().tracks.first { it.artworkSource == snapshot.albums.single().artworkSource }.trackId)
    }

    @Test
    fun `artist order disc order and unavailable state are preserved`() {
        val album = album("Ordered", null)
        val later = track(album.albumId, title = "Later", disc = 2, number = 1)
        val earlier = track(album.albumId, title = null, disc = 1, number = 3)
        val artistA = ArtistId.random()
        val artistB = ArtistId.random()

        val snapshot = projectLibrarySnapshot(
            albums = listOf(album),
            tracks = listOf(later, earlier),
            albumArtistRows = emptyList(),
            trackArtistRows = listOf(
                TrackArtistNameRow(earlier.trackId, artistB, 1, "Guest"),
                TrackArtistNameRow(earlier.trackId, artistA, 0, "Lead"),
            ),
            activeRows = listOf(active(later.trackId)),
        )

        val projectedAlbum = snapshot.albums.single()
        assertEquals(listOf(earlier.trackId, later.trackId), projectedAlbum.tracks.map { it.trackId })
        val missing = projectedAlbum.tracks.first()
        assertEquals("未知曲目", missing.title)
        assertEquals(listOf("Lead", "Guest"), missing.artists)
        assertFalse(missing.isPlayable)
        assertEquals("没有可用的音频源", missing.unavailableReason)
        assertTrue(projectedAlbum.tracks.last().isPlayable)
    }

    @Test
    fun `songs sort by album artist pinyin then date album disc and track`() {
        val adeleAlbum = album("30", "2021-11-19")
        val chenOldAlbum = album("旧作", "2003-01-01")
        val chenNewAlbum = album("新作", "2024-01-01")
        val linAlbum = album("林的专辑", "2020-01-01")
        val adeleTrack = track(adeleAlbum.albumId, "Adele", number = 1)
        val chenOldDiscTwo = track(chenOldAlbum.albumId, "Disc 2", disc = 2, number = 1)
        val chenOldDiscOne = track(chenOldAlbum.albumId, "Disc 1", disc = 1, number = 3)
        val chenNewTrack = track(chenNewAlbum.albumId, "New", number = 1)
        val linTrack = track(linAlbum.albumId, "Lin", number = 1)
        val adeleArtist = ArtistId.random()
        val chenArtist = ArtistId.random()
        val linArtist = ArtistId.random()

        val snapshot = projectLibrarySnapshot(
            albums = listOf(linAlbum, chenNewAlbum, adeleAlbum, chenOldAlbum),
            tracks = listOf(chenNewTrack, linTrack, chenOldDiscTwo, adeleTrack, chenOldDiscOne),
            albumArtistRows = listOf(
                AlbumArtistNameRow(linAlbum.albumId, linArtist, 0, "林俊杰"),
                AlbumArtistNameRow(chenNewAlbum.albumId, chenArtist, 0, "陈奕迅"),
                AlbumArtistNameRow(adeleAlbum.albumId, adeleArtist, 0, "Adele"),
                AlbumArtistNameRow(chenOldAlbum.albumId, chenArtist, 0, "陈奕迅"),
            ),
            trackArtistRows = emptyList(),
            activeRows = listOf(
                active(adeleTrack.trackId),
                active(chenOldDiscTwo.trackId),
                active(chenOldDiscOne.trackId),
                active(chenNewTrack.trackId),
                active(linTrack.trackId),
            ),
        )

        assertEquals(
            listOf(
                adeleTrack.trackId,
                chenOldDiscOne.trackId,
                chenOldDiscTwo.trackId,
                chenNewTrack.trackId,
                linTrack.trackId,
            ),
            snapshot.tracks.map { it.trackId },
        )
    }

    @Test
    fun `pinyin sort key converts Chinese and normalizes Latin accents`() {
        assertEquals("chenyixun", pinyinAlphabeticalSortKey("陈奕迅"))
        assertEquals("chenyixun", pinyinAlphabeticalSortKey("陳奕迅"))
        assertEquals("beyonce", pinyinAlphabeticalSortKey("Beyoncé"))
    }

    private fun album(title: String, date: String?) = AlbumEntity(
        albumId = AlbumId.random(),
        albumKeyVersion = 1,
        albumKeyHash = AlbumId.random().toString(),
        title = title,
        date = date,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun track(
        albumId: AlbumId,
        title: String?,
        disc: Int = 1,
        number: Int = 1,
    ) = TrackEntity(
        trackId = TrackId.random(),
        trackKeyVersion = 1,
        trackKeyHash = TrackId.random().toString(),
        trackKeyStrength = TrackKeyStrength.STRONG,
        albumId = albumId,
        title = title,
        date = null,
        trackNumber = number,
        trackTotal = null,
        discNumber = disc,
        discTotal = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun active(trackId: TrackId, hasEmbeddedArtwork: Boolean = false) = ActiveTrackSourceRow(
        trackId = trackId,
        sourceId = TrackSourceId.random(),
        fileName = "track.flac",
        durationMs = 180_000,
        availabilityState = SourceAvailabilityState.AVAILABLE,
        parseState = ParseState.PARSED,
        playabilityState = PlayabilityState.UNKNOWN,
        uri = "content://music/$trackId",
        relativePath = "primary/Music/Album/track.flac",
        modifiedMs = 10,
        sizeBytes = 20,
        treeUri = "content://provider/tree/primary%3AMusic",
        rootLastSuccessfulScanAt = 30,
        hasEmbeddedArtwork = hasEmbeddedArtwork,
    )
}
