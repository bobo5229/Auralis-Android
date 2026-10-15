package com.bobo.auralis.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import java.util.UUID

internal data class PlaybackSourceEvidence(
    val trackId: TrackId,
    val sourceId: TrackSourceId,
    val modifiedMs: Long,
    val sizeBytes: Long,
) {
    fun matches(track: TrackId, source: TrackSourceId, modified: Long, size: Long): Boolean =
        trackId == track && sourceId == source && modifiedMs == modified && sizeBytes == size
}

internal object PlaybackSourceMetadata {
    const val SourceId = "com.bobo.auralis.playback.SOURCE_ID"
    const val ModifiedMs = "com.bobo.auralis.playback.SOURCE_MODIFIED_MS"
    const val SizeBytes = "com.bobo.auralis.playback.SOURCE_SIZE_BYTES"
}

internal fun MediaItem.sourceEvidence(): PlaybackSourceEvidence? {
    val track = mediaId.toTrackId() ?: return null
    val extras = mediaMetadata.extras ?: return null
    val source = runCatching {
        TrackSourceId(UUID.fromString(extras.getString(PlaybackSourceMetadata.SourceId)))
    }.getOrNull() ?: return null
    if (!extras.containsKey(PlaybackSourceMetadata.ModifiedMs) || !extras.containsKey(PlaybackSourceMetadata.SizeBytes)) return null
    return PlaybackSourceEvidence(track, source,
        extras.getLong(PlaybackSourceMetadata.ModifiedMs), extras.getLong(PlaybackSourceMetadata.SizeBytes))
}

/** Access and system failures are retryable; only format/decoder evidence excludes a source. */
internal fun isPermanentPlaybackFailure(code: Int): Boolean = code in setOf(
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
)
