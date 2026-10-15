package com.bobo.auralis.mobile.library.browser

import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.dao.ActiveTrackSourceRow
import com.bobo.auralis.mobile.library.db.dao.AlbumArtistNameRow
import com.bobo.auralis.mobile.library.db.dao.TrackArtistNameRow
import com.bobo.auralis.mobile.library.db.dao.TrackGenreNameRow
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LibraryBrowserRepository(database: AuralisDatabase) {
    private val dao = database.libraryBrowserDao()

    private val tracksSnapshot: Flow<LibrarySnapshot> = combine(
        dao.observeAlbums(),
        dao.observeTracks(),
        dao.observeAlbumArtists(),
        dao.observeTrackArtists(),
        dao.observeActiveSources(),
    ) { albums, tracks, albumArtistRows, trackArtistRows, activeRows ->
        projectLibrarySnapshot(albums, tracks, albumArtistRows, trackArtistRows, activeRows)
    }

    val snapshot: Flow<LibrarySnapshot> = combine(tracksSnapshot, dao.observeGenres()) { library, genres ->
        projectGenreSnapshot(library, genres)
    }
}

internal fun projectGenreSnapshot(library: LibrarySnapshot, rows: List<TrackGenreNameRow>): LibrarySnapshot {
    val byTrack = rows.groupBy { it.trackId }
    val tracks = library.tracks.map { track ->
        track.copy(genres = byTrack[track.trackId].orEmpty().sortedBy { it.position }.map { it.displayName })
    }
    val tracksById = tracks.associateBy { it.trackId }
    return library.copy(
        tracks = tracks,
        albums = library.albums.map { album ->
            album.copy(tracks = album.tracks.mapNotNull { tracksById[it.trackId] })
        },
        genres = projectLibraryGenres(tracks, rows),
    )
}

internal fun sortLibraryGenres(genres: List<LibraryGenre>): List<LibraryGenre> =
    genres.sortedWith(compareBy<LibraryGenre> { pinyinAlphabeticalSortKey(it.displayName) }
        .thenBy { it.displayName }.thenBy { it.genreId.toString() })

internal fun projectLibraryGenres(
    tracks: List<LibraryTrack>,
    rows: List<TrackGenreNameRow>,
): List<LibraryGenre> {
    val genresByTrack = rows.groupBy { it.trackId }
    val tracksByGenre = mutableMapOf<com.bobo.auralis.mobile.library.model.GenreId, MutableList<LibraryTrack>>()
    tracks.forEach { track ->
        genresByTrack[track.trackId].orEmpty().map { it.genreId }.distinct().forEach { genreId ->
            tracksByGenre.getOrPut(genreId) { mutableListOf() }.add(track)
        }
    }
    return sortLibraryGenres(rows.distinctBy { it.genreId }.mapNotNull { row ->
        tracksByGenre[row.genreId]?.let { LibraryGenre(row.genreId, row.displayName, it) }
    })
}

internal fun projectLibrarySnapshot(
    albums: List<AlbumEntity>,
    tracks: List<TrackEntity>,
    albumArtistRows: List<AlbumArtistNameRow>,
    trackArtistRows: List<TrackArtistNameRow>,
    activeRows: List<ActiveTrackSourceRow>,
): LibrarySnapshot {
    val albumArtists = albumArtistRows.groupBy { it.albumId }
        .mapValues { (_, rows) -> rows.sortedBy { it.position }.map { it.displayName } }
    val trackArtists = trackArtistRows.groupBy { it.trackId }
        .mapValues { (_, rows) -> rows.sortedBy { it.position }.map { it.displayName } }
    val activeByTrack = activeRows.associateBy { it.trackId }
    val albumTitles = albums.associate { it.albumId to it.title }
    val songAlbumSortMetadata = albums.associate { album ->
        album.albumId to SongAlbumSortMetadata(
            artistKeys = albumArtists[album.albumId].orEmpty().map(::pinyinAlphabeticalSortKey),
            date = album.date,
            titleKey = pinyinAlphabeticalSortKey(album.title),
            stableId = album.albumId.toString(),
        )
    }

    val projectedTracks = tracks.map { track ->
        track.toLibraryTrack(
            artists = trackArtists[track.trackId].orEmpty(),
            albumTitle = track.albumId?.let(albumTitles::get),
            active = activeByTrack[track.trackId],
        )
    }
    val tracksByAlbum = projectedTracks.filter { it.albumId != null }.groupBy { it.albumId }
    return LibrarySnapshot(
        albums = albums.map { album ->
            val albumTracks = tracksByAlbum[album.albumId].orEmpty().sortedWith(albumTrackComparator)
            LibraryAlbum(
                albumId = album.albumId,
                title = album.title,
                albumArtists = albumArtists[album.albumId].orEmpty(),
                date = album.date,
                tracks = albumTracks,
                artworkSource = albumTracks.firstOrNull { it.artworkSource?.hasEmbeddedArtwork == true }?.artworkSource
                    ?: albumTracks.firstNotNullOfOrNull { it.artworkSource },
            )
        },
        tracks = projectedTracks.sortedWith(songTrackComparator(songAlbumSortMetadata)),
    )
}

private data class SongAlbumSortMetadata(
    val artistKeys: List<String>,
    val date: String?,
    val titleKey: String,
    val stableId: String,
)

private fun songTrackComparator(
    albums: Map<AlbumId, SongAlbumSortMetadata>,
): Comparator<LibraryTrack> = Comparator { left, right ->
    val albumComparison = compareSongAlbums(
        left = left.albumId?.let(albums::get),
        right = right.albumId?.let(albums::get),
    )
    if (albumComparison != 0) return@Comparator albumComparison

    compareValues(left.discNumber ?: Int.MAX_VALUE, right.discNumber ?: Int.MAX_VALUE)
        .takeIf { it != 0 }
        ?: compareValues(left.trackNumber ?: Int.MAX_VALUE, right.trackNumber ?: Int.MAX_VALUE)
            .takeIf { it != 0 }
        ?: pinyinAlphabeticalSortKey(left.title).compareTo(pinyinAlphabeticalSortKey(right.title))
            .takeIf { it != 0 }
        ?: left.trackId.toString().compareTo(right.trackId.toString())
}

private fun compareSongAlbums(left: SongAlbumSortMetadata?, right: SongAlbumSortMetadata?): Int {
    if (left == null || right == null) {
        return when {
            left == null && right == null -> 0
            left == null -> 1
            else -> -1
        }
    }

    compareArtistKeys(left.artistKeys, right.artistKeys).takeIf { it != 0 }?.let { return it }
    compareNullableMissingLast(left.date, right.date).takeIf { it != 0 }?.let { return it }
    left.titleKey.compareTo(right.titleKey).takeIf { it != 0 }?.let { return it }
    return left.stableId.compareTo(right.stableId)
}

private fun compareArtistKeys(left: List<String>, right: List<String>): Int {
    if (left.isEmpty() || right.isEmpty()) {
        return when {
            left.isEmpty() && right.isEmpty() -> 0
            left.isEmpty() -> 1
            else -> -1
        }
    }
    repeat(minOf(left.size, right.size)) { index ->
        left[index].compareTo(right[index]).takeIf { it != 0 }?.let { return it }
    }
    return left.size.compareTo(right.size)
}

private fun <T : Comparable<T>> compareNullableMissingLast(left: T?, right: T?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right)
}

private val albumTrackComparator = compareBy<LibraryTrack>(
    { it.discNumber ?: Int.MAX_VALUE },
    { it.trackNumber ?: Int.MAX_VALUE },
    { it.title },
    { it.trackId.toString() },
)

private fun TrackEntity.toLibraryTrack(
    artists: List<String>,
    albumTitle: String?,
    active: ActiveTrackSourceRow?,
): LibraryTrack {
    val unavailableReason = when {
        active == null -> "没有可用的音频源"
        active.availabilityState != SourceAvailabilityState.AVAILABLE -> "音频文件当前不可访问"
        active.parseState != ParseState.PARSED -> "音频文件尚未成功解析"
        active.playabilityState == PlayabilityState.UNPLAYABLE -> "播放器无法打开该文件"
        else -> null
    }
    return LibraryTrack(
        trackId = trackId,
        albumId = albumId,
        title = title ?: active?.fileName ?: "未知曲目",
        artists = artists,
        albumTitle = albumTitle,
        durationMs = active?.durationMs,
        discNumber = discNumber,
        trackNumber = trackNumber,
        isPlayable = unavailableReason == null,
        unavailableReason = unavailableReason,
        artworkSource = active?.let {
            LibraryArtworkSource(
                sourceId = it.sourceId,
                audioUri = it.uri,
                treeUri = it.treeUri,
                relativePath = it.relativePath,
                fileName = it.fileName,
                modifiedMs = it.modifiedMs,
                sizeBytes = it.sizeBytes,
                hasEmbeddedArtwork = it.hasEmbeddedArtwork == true,
                rootLastSuccessfulScanAt = it.rootLastSuccessfulScanAt,
            )
        },
    )
}
