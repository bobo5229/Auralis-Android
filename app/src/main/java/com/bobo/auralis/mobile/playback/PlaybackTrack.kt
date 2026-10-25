package com.bobo.auralis.mobile.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.LibraryAudioSource
import com.bobo.auralis.mobile.library.model.TrackId
import java.util.UUID

/** Display and file-access snapshot of the item accepted into the Media3 queue. */
data class PlaybackTrack(
    val trackId: TrackId,
    val title: String,
    val artists: List<String>,
    val albumId: AlbumId?,
    val albumTitle: String?,
    val durationMs: Long?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val audioSource: LibraryAudioSource?,
    val hasEmbeddedArtwork: Boolean,
)

internal fun Bundle.putPlaybackTrack(track: PlaybackTrack) {
    // A re-resolved item may legitimately lose optional fields from its previous snapshot.
    listOf(PlaybackTrackMetadata.Duration, PlaybackTrackMetadata.Disc, PlaybackTrackMetadata.Track,
        PlaybackTrackMetadata.ScanAt).forEach(::remove)
    putStringArrayList(PlaybackTrackMetadata.Artists, ArrayList(track.artists))
    putString(PlaybackTrackMetadata.AlbumId, track.albumId?.toString())
    track.durationMs?.let { putLong(PlaybackTrackMetadata.Duration, it) }
    track.discNumber?.let { putInt(PlaybackTrackMetadata.Disc, it) }
    track.trackNumber?.let { putInt(PlaybackTrackMetadata.Track, it) }
    putBoolean(PlaybackTrackMetadata.HasArtwork, track.hasEmbeddedArtwork)
    track.audioSource?.let { source ->
        putString(PlaybackTrackMetadata.Uri, source.audioUri)
        putString(PlaybackTrackMetadata.TreeUri, source.treeUri)
        putString(PlaybackTrackMetadata.Path, source.relativePath)
        putString(PlaybackTrackMetadata.FileName, source.fileName)
        source.rootLastSuccessfulScanAt?.let { putLong(PlaybackTrackMetadata.ScanAt, it) }
    }
}

internal fun MediaItem.playbackTrack(): PlaybackTrack? {
    val trackId = mediaId.toTrackId() ?: return null
    val metadata = mediaMetadata
    val extras = metadata.extras
    val evidence = sourceEvidence()
    val audioUri = extras?.getString(PlaybackTrackMetadata.Uri)
    val audio = if (evidence != null && audioUri != null) LibraryAudioSource(
        sourceId = evidence.sourceId,
        audioUri = audioUri,
        treeUri = extras.getString(PlaybackTrackMetadata.TreeUri),
        relativePath = extras.getString(PlaybackTrackMetadata.Path).orEmpty(),
        fileName = extras.getString(PlaybackTrackMetadata.FileName).orEmpty(),
        modifiedMs = evidence.modifiedMs,
        sizeBytes = evidence.sizeBytes,
        rootLastSuccessfulScanAt = extras.optionalLong(PlaybackTrackMetadata.ScanAt),
    ) else null
    return PlaybackTrack(
        trackId = trackId,
        title = metadata.title?.toString() ?: "未知曲目",
        artists = extras?.getStringArrayList(PlaybackTrackMetadata.Artists)?.toList().orEmpty(),
        albumId = extras?.getString(PlaybackTrackMetadata.AlbumId)?.let {
            runCatching { AlbumId(UUID.fromString(it)) }.getOrNull()
        },
        albumTitle = metadata.albumTitle?.toString(),
        durationMs = extras?.optionalLong(PlaybackTrackMetadata.Duration),
        discNumber = extras?.optionalInt(PlaybackTrackMetadata.Disc),
        trackNumber = extras?.optionalInt(PlaybackTrackMetadata.Track),
        audioSource = audio,
        hasEmbeddedArtwork = extras?.getBoolean(PlaybackTrackMetadata.HasArtwork) == true,
    )
}

private fun Bundle.optionalLong(key: String): Long? = if (containsKey(key)) getLong(key) else null
private fun Bundle.optionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null

private object PlaybackTrackMetadata {
    private const val Prefix = "com.bobo.auralis.playback.DISPLAY_"
    const val Artists = Prefix + "ARTISTS"
    const val AlbumId = Prefix + "ALBUM_ID"
    const val Duration = Prefix + "DURATION"
    const val Disc = Prefix + "DISC"
    const val Track = Prefix + "TRACK"
    const val HasArtwork = Prefix + "HAS_ARTWORK"
    const val Uri = Prefix + "URI"
    const val TreeUri = Prefix + "TREE_URI"
    const val Path = Prefix + "PATH"
    const val FileName = Prefix + "FILE_NAME"
    const val ScanAt = Prefix + "SCAN_AT"
}
