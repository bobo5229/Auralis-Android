package com.bobo.auralis.mobile.library.identity

import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The §58 identity test matrix, row by row.
 *
 * Two rows cannot appear here as value changes: `path` and `URI` are not fields
 * of [AuralisMetadata] at all, so they are structurally incapable of reaching
 * identity. They become meaningful once Step C introduces the observation model
 * that carries them, and are covered by [excludedFieldsNeverReachTheTrackKey]
 * until then.
 */
class TrackKeyTest {

    private val base: AuralisMetadata = auralisMetadata(
        title = "360",
        artists = listOf("Charli xcx"),
        albumArtists = listOf("Charli xcx"),
        album = "BRAT",
        genres = listOf("Hyperpop"),
        date = "2024-06-07",
        trackNumber = 1,
        trackTotal = 15,
        discNumber = 1,
        discTotal = 1,
        embeddedLyrics = "[00:01.00] lyrics",
        hasEmbeddedArtwork = true,
        artworkBytes = 300_359,
        durationMs = 131_000L,
        bitrateKbps = 256,
        sampleRateHz = 44_100,
        mimeType = "audio/aac",
    )

    private fun trackKey(metadata: AuralisMetadata): TrackKey =
        IdentityDerivation.derive(metadata).trackKey

    private fun albumKey(metadata: AuralisMetadata): AlbumKey? =
        IdentityDerivation.derive(metadata).albumKey

    @Test
    fun `codec change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(mimeType = "audio/flac")))
    }

    @Test
    fun `bitrate change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(bitrateKbps = 128)))
    }

    @Test
    fun `sample rate change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(sampleRateHz = 96_000)))
    }

    @Test
    fun `duration change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(durationMs = 131_500L)))
    }

    @Test
    fun `genre change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(genres = listOf("Electropop", "Dance"))))
    }

    @Test
    fun `artwork change keeps the same track key`() {
        assertEquals(
            trackKey(base),
            trackKey(base.copy(hasEmbeddedArtwork = false, artworkBytes = 0)),
        )
    }

    @Test
    fun `lyrics change keeps the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(embeddedLyrics = null)))
    }

    @Test
    fun `track total and disc total changes keep the same track key`() {
        assertEquals(trackKey(base), trackKey(base.copy(trackTotal = 12, discTotal = 2)))
    }

    @Test
    fun `artist order does not change the track key`() {
        val forward = base.copy(artists = listOf("A", "B"))
        val reversed = base.copy(artists = listOf("B", "A"))
        assertEquals(trackKey(forward), trackKey(reversed))
    }

    @Test
    fun `album artist order does not change the album key`() {
        val forward = base.copy(albumArtists = listOf("A", "B"))
        val reversed = base.copy(albumArtists = listOf("B", "A"))
        assertEquals(albumKey(forward), albumKey(reversed))
        assertEquals(trackKey(forward), trackKey(reversed))
    }

    @Test
    fun `nfc equivalent metadata keeps the same track key`() {
        val composed = base.copy(album = "Caf\u00E9", artists = listOf("Caf\u00E9"))
        val decomposed = base.copy(album = "Cafe\u0301", artists = listOf("Cafe\u0301"))
        assertNotEquals(composed.album, decomposed.album)
        assertEquals(trackKey(composed), trackKey(decomposed))
    }

    @Test
    fun `title change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(title = "Guess")))
    }

    @Test
    fun `track artist change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(artists = listOf("Billie Eilish"))))
    }

    @Test
    fun `track number change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(trackNumber = 2)))
    }

    @Test
    fun `disc number change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(discNumber = 2)))
    }

    @Test
    fun `album title change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(album = "Brat and it's completely different")))
    }

    @Test
    fun `album artist change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(albumArtists = listOf("Various Artists"))))
    }

    @Test
    fun `date change changes the track key`() {
        assertNotEquals(trackKey(base), trackKey(base.copy(date = "2025-06-07")))
    }

    @Test
    fun `date appearing where none existed changes the album key`() {
        val withoutDate = base.copy(date = null)
        assertNotEquals(albumKey(withoutDate), albumKey(base))
        assertNotEquals(trackKey(withoutDate), trackKey(base))
    }

    @Test
    fun `missing album artist is never backfilled from track artist`() {
        val noAlbumArtist = base.copy(albumArtists = emptyList())
        val explicitAlbumArtist = base.copy(albumArtists = listOf("Charli xcx"))

        assertEquals(emptyList<String>(), IdentityDerivation.derive(noAlbumArtist).albumIdentity?.albumArtists)
        // If Artist were used as a fallback these two would agree.
        assertNotEquals(albumKey(noAlbumArtist), albumKey(explicitAlbumArtist))
    }

    @Test
    fun `excludedFieldsNeverReachTheTrackKey`() {
        // Everything §9 excludes, changed at once, must not move the key.
        val reencoded = base.copy(
            genres = listOf("Different"),
            bitrateKbps = 320,
            sampleRateHz = 48_000,
            mimeType = "audio/flac",
            durationMs = 132_777L,
            hasEmbeddedArtwork = false,
            artworkBytes = 0,
            embeddedLyrics = null,
            trackTotal = 99,
            discTotal = 9,
        )
        assertEquals(trackKey(base), trackKey(reencoded))
    }

    @Test
    fun `track key is versioned and deterministic`() {
        assertEquals(1, trackKey(base).version)
        assertEquals(trackKey(base), trackKey(base))
        assertEquals(64, trackKey(base).hash.length)
    }
}
