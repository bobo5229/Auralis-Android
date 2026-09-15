package com.bobo.auralis.mobile.library.model

import com.bobo.auralis.mobile.library.identity.IdentityDerivation
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `docs/phase3a/02_SOURCE_MODEL.md` §13.
 *
 * The snapshot is what makes promoting a backup duplicate source possible without
 * re-reading the file, so these tests focus on "each source keeps its own values"
 * and on the display-order rule from §4.
 */
class SourceMetadataTest {

    private fun snapshotOf(metadata: AuralisMetadata) = SourceMetadata.from(
        metadata = metadata,
        identity = IdentityDerivation.derive(metadata),
    )

    private val base = AuralisMetadata(
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

    @Test
    fun `snapshot captures every field listed in section 13`() {
        val snapshot = snapshotOf(base)

        assertEquals("360", snapshot.title)
        assertEquals("BRAT", snapshot.albumTitle)
        assertEquals("2024-06-07", snapshot.date)
        assertEquals(1, snapshot.trackNumber)
        assertEquals(15, snapshot.trackTotal)
        assertEquals(1, snapshot.discNumber)
        assertEquals(1, snapshot.discTotal)
        assertEquals(listOf("Charli xcx"), snapshot.artists)
        assertEquals(listOf("Charli xcx"), snapshot.albumArtists)
        assertEquals(listOf("Hyperpop"), snapshot.genres)
        assertEquals(131_000L, snapshot.durationMs)
        assertEquals(256, snapshot.bitrateKbps)
        assertEquals(44_100, snapshot.sampleRateHz)
        assertEquals("audio/aac", snapshot.mimeType)
    }

    @Test
    fun `snapshot carries the identity key version hash and strength`() {
        val derived = IdentityDerivation.derive(base)
        val snapshot = SourceMetadata.from(base, derived)

        assertEquals(derived.trackKey.version, snapshot.trackKeyVersion)
        assertEquals(derived.trackKey.hash, snapshot.trackKeyHash)
        assertEquals(derived.trackKeyStrength, snapshot.trackKeyStrength)
        assertEquals(derived.albumKey?.version, snapshot.albumKeyVersion)
        assertEquals(derived.albumKey?.hash, snapshot.albumKeyHash)
    }

    @Test
    fun `a source without an album has no album key version either`() {
        val snapshot = snapshotOf(base.copy(album = null, albumArtists = emptyList()))

        assertNull(snapshot.albumTitle)
        assertNull(snapshot.albumKeyVersion)
        assertNull(snapshot.albumKeyHash)
        assertNotNull(snapshot.trackKeyHash)
    }

    @Test
    fun `multi value fields keep display order rather than identity order`() {
        val snapshot = snapshotOf(
            base.copy(
                artists = listOf("Zed", "Alpha", "Mike"),
                albumArtists = listOf("Various Artists", "Another"),
                genres = listOf("Synthpop", "Ambient"),
            ),
        )

        // Identity sorts into sets; the snapshot must not.
        assertEquals(listOf("Zed", "Alpha", "Mike"), snapshot.artists)
        assertEquals(listOf("Various Artists", "Another"), snapshot.albumArtists)
        assertEquals(listOf("Synthpop", "Ambient"), snapshot.genres)
    }

    @Test
    fun `values are stored as interpreted without re-canonicalization`() {
        val snapshot = snapshotOf(base.copy(title = "  360  ", artists = listOf("  A  ", "A")))

        // This is the display-side record of what the file said.
        assertEquals("  360  ", snapshot.title)
        assertEquals(listOf("  A  ", "A"), snapshot.artists)
    }

    @Test
    fun `duplicate sources of one track each keep their own snapshot`() {
        // The §13 scenario: A/song.flac is active, B/song.m4a is the backup. They
        // share a TrackKey but disagree on artist and genre wording.
        val lossless = base.copy(
            title = "Song",
            artists = listOf("Artist"),
            album = "Album",
            albumArtists = listOf("Artist"),
            trackNumber = 1,
            genres = listOf("Genre A"),
            mimeType = "audio/flac",
            bitrateKbps = 900,
        )
        val lossy = lossless.copy(
            genres = listOf("Genre B"),
            mimeType = "audio/aac",
            bitrateKbps = 256,
        )

        val losslessSnapshot = snapshotOf(lossless)
        val lossySnapshot = snapshotOf(lossy)

        assertEquals(losslessSnapshot.trackKeyHash, lossySnapshot.trackKeyHash)
        assertEquals(listOf("Genre A"), losslessSnapshot.genres)
        assertEquals(listOf("Genre B"), lossySnapshot.genres)
        assertEquals("audio/flac", losslessSnapshot.mimeType)
        assertEquals("audio/aac", lossySnapshot.mimeType)
    }

    @Test
    fun `raw metadata and artwork bytes never enter the snapshot`() {
        val names = instanceFieldNames(SourceMetadata::class.java)

        assertEquals(false, names.any { it.contains("raw") })
        assertEquals(false, names.any { it.contains("artwork") })
        assertEquals(false, names.any { it.contains("cover") })
        assertEquals(false, names.any { it.contains("lyrics") })
    }
}
