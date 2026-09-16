package com.bobo.auralis.mobile.debug

import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
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
    val artists: List<String>,
    val genres: List<String>,
)

data class SourceInspectionDetail(
    val source: TrackSourceEntity,
    val isActive: Boolean,
    val rootPriority: Int?,
)
