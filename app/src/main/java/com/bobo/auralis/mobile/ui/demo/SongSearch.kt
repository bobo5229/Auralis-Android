package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack

internal fun filterSongTracks(
    tracks: List<LibraryTrack>,
    albumsById: Map<String, LibraryAlbum>,
    query: String,
): List<LibraryTrack> {
    val term = SongSearchText.normalize(query.trim())
    if (term.isEmpty()) return tracks
    fun matches(value: String): Boolean = SongSearchText.normalize(value).contains(term, ignoreCase = true)
    return tracks.filter { track ->
        matches(track.title) ||
            track.artists.any(::matches) ||
            track.albumTitle?.let(::matches) == true ||
            track.albumId?.toString()?.let(albumsById::get)?.albumArtists.orEmpty()
                .any(::matches)
    }
}
