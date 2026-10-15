package com.bobo.auralis.mobile.library.identity

import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.reconciliation.CreateTrackReason
import com.bobo.auralis.mobile.library.reconciliation.ReconcileDecision
import com.bobo.auralis.mobile.library.reconciliation.ReconcileObservation
import com.bobo.auralis.mobile.library.reconciliation.ReconciliationDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Completeness and cross-source merge rules from METADATA.md §8.3.
 */
class TrackKeyStrengthTest {

    private fun strengthOf(
        album: String? = null,
        title: String? = null,
        artists: List<String> = emptyList(),
        trackNumber: Int? = null,
        discNumber: Int? = null,
    ): TrackKeyStrength = IdentityDerivation.derive(
        auralisMetadata(
            album = album,
            title = title,
            artists = artists,
            trackNumber = trackNumber,
            discNumber = discNumber,
        ),
    ).trackKeyStrength

    @Test
    fun `complete identity is strong`() {
        assertEquals(
            TrackKeyStrength.STRONG,
            strengthOf(album = "Album", title = "Title", artists = listOf("Artist"), discNumber = 1, trackNumber = 1),
        )
    }

    @Test
    fun `missing disc and track numbers is weak`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(album = "Album", title = "Title", artists = listOf("Artist")),
        )
    }

    @Test
    fun `missing artist is weak even when title and positions are present`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(album = "Album", title = "Title", discNumber = 1, trackNumber = 1),
        )
    }

    @Test
    fun `album and title alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", title = "Title"))
    }

    @Test
    fun `album and artist alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", artists = listOf("Artist")))
    }

    @Test
    fun `album and track number alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", trackNumber = 1))
    }

    @Test
    fun `title and artist without album is weak`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(title = "Title", artists = listOf("Artist")),
        )
    }

    @Test
    fun `everything missing is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf())
    }

    @Test
    fun `disc number never contributes to strength by itself`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", discNumber = 1))
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", title = "Title", discNumber = 1))
    }

    @Test
    fun `blank values count as absent evidence`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(album = "Album", title = "   ", artists = listOf("  ")),
        )
    }

    @Test
    fun `every missing identity dimension prevents cross-source merging`() {
        val complete = auralisMetadata(
            album = "Album", title = "Song", artists = listOf("A", "B"),
            discNumber = 1, trackNumber = 1,
        )
        val missingDimensions = listOf(
            complete.copy(album = null),
            complete.copy(title = null),
            complete.copy(title = "  "),
            complete.copy(artists = emptyList()),
            complete.copy(artists = listOf("  ")),
            complete.copy(discNumber = null),
            complete.copy(trackNumber = null),
        )
        for (metadata in missingDimensions) {
            val identity = IdentityDerivation.derive(metadata)
            assertEquals(TrackKeyStrength.WEAK, identity.trackKeyStrength)
            val decision = ReconciliationDecisionEngine.decide(
                observation = ReconcileObservation(
                    provider = "provider", documentId = "another-file", rootPriority = 0,
                    relativePath = "copy.flac", albumKeyVersion = identity.albumKey?.version,
                    albumKeyHash = identity.albumKey?.hash, trackKey = identity.trackKey,
                    trackKeyStrength = identity.trackKeyStrength,
                ),
                existingPhysicalSource = null,
                semanticMatchingTracks = listOf(TrackId.random()),
            )
            assertEquals(
                ReconcileDecision.CreateNewTrack(CreateTrackReason.WEAK_KEY_CANNOT_AUTO_MERGE),
                decision,
            )
        }
    }

    @Test
    fun `invalid positions cannot provide strong evidence`() {
        for ((disc, track) in listOf(0 to 1, -1 to 1, 1 to 0, 1 to -1)) {
            assertEquals(
                TrackKeyStrength.WEAK,
                strengthOf("Album", "Song", listOf("Artist"), trackNumber = track, discNumber = disc),
            )
        }
    }

    @Test
    fun `direct callers cannot count blank title or artists as strong evidence`() {
        val album = AlbumIdentity("Album", emptyList(), null)
        assertEquals(
            TrackKeyStrength.WEAK,
            TrackKeyV1.strengthOf(TrackIdentity(album, " ", listOf("Artist"), 1, 1)),
        )
        assertEquals(
            TrackKeyStrength.WEAK,
            TrackKeyV1.strengthOf(TrackIdentity(album, "Song", listOf(" "), 1, 1)),
        )
    }
}
