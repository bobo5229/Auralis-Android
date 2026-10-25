package com.bobo.auralis.mobile.library.browser

import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.LibraryAudioSource

data class LibraryArtworkSource(
    val audioSource: LibraryAudioSource,
    val hasEmbeddedArtwork: Boolean,
) {
    val cacheKey: String
        get() = "auralis-artwork:${audioSource.sourceId}:${audioSource.modifiedMs}:${audioSource.sizeBytes}:${audioSource.rootLastSuccessfulScanAt ?: 0L}"
}

data class LibraryTrack(
    val trackId: TrackId,
    val albumId: AlbumId?,
    val title: String,
    val artists: List<String>,
    val albumTitle: String?,
    val durationMs: Long?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val isPlayable: Boolean,
    val unavailableReason: String?,
    val audioSource: LibraryAudioSource?,
    val hasEmbeddedArtwork: Boolean = false,
    val genres: List<String> = emptyList(),
) {
    val artworkSource: LibraryArtworkSource?
        get() = audioSource?.let { LibraryArtworkSource(it, hasEmbeddedArtwork) }

    val artistLabel: String
        get() = artists.takeIf { it.isNotEmpty() }?.joinToString("；") ?: "未知艺术家"
}

data class LibraryAlbum(
    val albumId: AlbumId,
    val title: String,
    val albumArtists: List<String>,
    val date: String?,
    val tracks: List<LibraryTrack>,
    val artworkSource: LibraryArtworkSource?,
) {
    val albumArtistLabel: String
        get() = albumArtists.takeIf { it.isNotEmpty() }?.joinToString("；") ?: "未知 Album Artist"
}

data class LibraryGenre(
    val genreId: GenreId,
    val displayName: String,
    val tracks: List<LibraryTrack> = emptyList(),
)

data class LibrarySnapshot(
    val albums: List<LibraryAlbum> = emptyList(),
    val tracks: List<LibraryTrack> = emptyList(),
    val genres: List<LibraryGenre> = emptyList(),
) {
    val tracksById: Map<TrackId, LibraryTrack>
        get() = tracks.associateBy { it.trackId }
}
