package com.bobo.auralis.mobile.library.browser

import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

data class LibraryArtworkSource(
    val sourceId: TrackSourceId,
    val audioUri: String,
    val treeUri: String?,
    val relativePath: String,
    val fileName: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
    val hasEmbeddedArtwork: Boolean,
    val rootLastSuccessfulScanAt: Long?,
) {
    val cacheKey: String
        get() = "auralis-artwork:$sourceId:$modifiedMs:$sizeBytes:${rootLastSuccessfulScanAt ?: 0L}"
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
    val artworkSource: LibraryArtworkSource?,
    val genres: List<String> = emptyList(),
) {
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
