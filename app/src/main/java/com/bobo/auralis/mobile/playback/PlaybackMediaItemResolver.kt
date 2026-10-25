package com.bobo.auralis.mobile.playback

import android.net.Uri
import android.os.Bundle
import androidx.room.withTransaction
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.LibraryAudioSource
import java.util.UUID

/** Thin Auralis identity adapter: TrackId -> active physical source -> playable MediaItem. */
class PlaybackMediaItemResolver(
    private val database: AuralisDatabase,
) {
    suspend fun resolve(request: MediaItem): MediaItem = database.withTransaction {
        val trackId = request.mediaId.toTrackId()
            ?: throw PlaybackResolutionException("无效 TrackId: ${request.mediaId}")
        val track = database.trackDao().findById(trackId)
            ?: throw PlaybackResolutionException("曲目不存在: $trackId")
        val active = database.sourceDao().findActiveSource(trackId)
            ?: throw PlaybackResolutionException("曲目没有可用的 Active Source: $trackId")
        val source = database.sourceDao().findById(active.sourceId)
            ?: throw PlaybackResolutionException("Active Source 不存在: ${active.sourceId}")
        if (source.trackId != trackId) {
            throw PlaybackResolutionException("Active Source 不属于当前曲目: $trackId")
        }

        if (
            source.availabilityState != SourceAvailabilityState.AVAILABLE ||
            source.parseState != ParseState.PARSED ||
            source.playabilityState == PlayabilityState.UNPLAYABLE
        ) {
            throw PlaybackResolutionException("Active Source 当前不可播放: ${source.sourceId}")
        }

        val graphDao = database.graphDao()
        val artists = graphDao.trackArtists(trackId)
            .mapNotNull { graphDao.findArtistById(it.artistId)?.displayName }
        val albumTitle = track.albumId?.let { graphDao.findAlbumById(it)?.title }
        val root = source.rootId?.let { database.libraryRootDao().findById(it) }
        val snapshot = PlaybackTrack(
            trackId = trackId,
            title = track.title ?: source.fileName,
            artists = artists,
            albumId = track.albumId,
            albumTitle = albumTitle,
            durationMs = source.durationMs,
            discNumber = track.discNumber,
            trackNumber = track.trackNumber,
            audioSource = LibraryAudioSource(
                source.sourceId, source.uri, root?.treeUri, source.relativePath, source.fileName,
                source.modifiedMs, source.sizeBytes, root?.lastSuccessfulScanAt,
            ),
            hasEmbeddedArtwork = database.sourceDao().findMetadata(source.sourceId)?.hasEmbeddedArtwork == true,
        )
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title ?: source.fileName)
            .setArtist(artists.takeIf { it.isNotEmpty() }?.joinToString(" ; "))
            .setAlbumTitle(albumTitle)
            .setIsPlayable(true)
            .setExtras(Bundle(request.mediaMetadata.extras ?: Bundle()).apply {
                putString(PlaybackSourceMetadata.SourceId, source.sourceId.toString())
                putLong(PlaybackSourceMetadata.ModifiedMs, source.modifiedMs)
                putLong(PlaybackSourceMetadata.SizeBytes, source.sizeBytes)
                putPlaybackTrack(snapshot)
            })
            .build()

        MediaItem.Builder()
            .setMediaId(trackId.toString())
            .setUri(Uri.parse(source.uri))
            .setMimeType(source.mimeType.takeUnless { it == "audio/unknown" })
            .setMediaMetadata(metadata)
            .build()
    }
}

internal fun String.toTrackId(): TrackId? =
    runCatching { TrackId(UUID.fromString(this)) }.getOrNull()

class PlaybackResolutionException(message: String) : IllegalStateException(message)
