package com.bobo.auralis.mobile.debug

import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * Inspection view item combining logical track, active source pointer, and physical sources.
 *
 * Implements `docs/phase3a/08_DEBUG_ACCEPTANCE.md` §62:
 * - TrackId
 * - TrackKey / TrackKey strength
 * - SourceId
 * - SafDocumentKey
 * - root priority
 * - active source indicator
 * - source states (availability, parse, playability)
 */
data class TrackInspectionItem(
    val track: TrackEntity,
    val activeSourceId: TrackSourceId?,
    val sources: List<SourceInspectionDetail>,
    val albumTitle: String?,
    val albumDate: String?,
    val albumArtists: List<String>,
    val artists: List<String>,
    val genres: List<String>,
)

/** One real AlbumEntity and its logical tracks, projected for the spike browser. */
data class AlbumInspectionItem(
    val albumId: AlbumId,
    val title: String,
    val date: String?,
    val albumArtists: List<String>,
    val tracks: List<TrackInspectionItem>,
) {
    val activeTrackCount: Int
        get() = tracks.count { it.activeSourceId != null }
}

/**
 * Groups strictly by AlbumId, never by display title. This keeps same-name releases
 * separate and deliberately leaves album-less tracks outside a shared placeholder.
 */
internal fun buildAlbumInspectionItems(
    tracks: List<TrackInspectionItem>,
): List<AlbumInspectionItem> = tracks
    .filter { it.track.albumId != null }
    .groupBy { requireNotNull(it.track.albumId) }
    .map { (albumId, albumTracks) ->
        val representative = albumTracks.first()
        AlbumInspectionItem(
            albumId = albumId,
            title = requireNotNull(representative.albumTitle),
            date = representative.albumDate,
            albumArtists = representative.albumArtists,
            tracks = albumTracks.sortedForAlbumInspection(),
        )
    }
    .sortedWith(
        compareBy<AlbumInspectionItem>(
            { it.title },
            { it.date ?: "" },
            { it.albumId.value.toString() },
        ),
    )

internal fun List<TrackInspectionItem>.sortedForAlbumInspection(): List<TrackInspectionItem> =
    sortedWith(
        compareBy<TrackInspectionItem>(
            { it.track.discNumber ?: Int.MAX_VALUE },
            { it.track.trackNumber ?: Int.MAX_VALUE },
            { it.track.title ?: "" },
            { it.track.trackId.value.toString() },
        ),
    )

data class SourceInspectionDetail(
    val source: TrackSourceEntity,
    val isActive: Boolean,
    val rootPriority: Int?,
)
