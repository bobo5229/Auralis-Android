package com.bobo.auralis.mobile.playback

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.bobo.auralis.mobile.library.model.TrackId
import java.security.SecureRandom
import java.util.Collections
import java.util.Random

enum class PlaybackMode(
    val key: String,
    val label: String,
    val description: String,
) {
    Sequential("sequential", "顺序播放", "按队列顺序播放，末尾停止"),
    RepeatAll("repeat_all", "列表循环", "播完队列后从头继续"),
    RepeatOne("repeat_one", "单曲循环", "持续重复当前歌曲"),
    ShuffleTracks("shuffle_tracks", "随机播放", "每轮无权重洗牌，一轮内不重复"),
    ShuffleAlbums("shuffle_albums", "随机专辑", "随机专辑顺序，专辑内按碟号和音轨号播放");

    val usesRandomRounds: Boolean
        get() = this == ShuffleTracks || this == ShuffleAlbums

    companion object {
        fun fromKey(key: String?): PlaybackMode =
            entries.firstOrNull { it.key == key } ?: Sequential
    }
}

internal data class PlaybackQueueTrack(
    val trackId: TrackId,
    val albumKey: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val queueIndex: Int? = null,
)

internal fun restoreQueueOrder(tracks: List<PlaybackQueueTrack>): List<PlaybackQueueTrack> =
    tracks.sortedBy { it.queueIndex ?: Int.MAX_VALUE }

internal fun MediaItem.toPlaybackQueueTrack(): PlaybackQueueTrack? {
    val resolvedTrackId = mediaId.toTrackId() ?: return null
    val extras = mediaMetadata.extras
    return PlaybackQueueTrack(
        resolvedTrackId, extras?.getString(PlaybackModeMetadata.AlbumKey),
        extras?.takeIf { it.containsKey(PlaybackModeMetadata.DiscNumber) }?.getInt(PlaybackModeMetadata.DiscNumber),
        extras?.takeIf { it.containsKey(PlaybackModeMetadata.TrackNumber) }?.getInt(PlaybackModeMetadata.TrackNumber),
        extras?.takeIf { it.containsKey(PlaybackModeMetadata.QueueIndex) }?.getInt(PlaybackModeMetadata.QueueIndex),
    )
}

internal fun playbackRepeatMode(mode: PlaybackMode): Int = when (mode) {
    PlaybackMode.Sequential,
    PlaybackMode.ShuffleTracks,
    PlaybackMode.ShuffleAlbums,
    -> Player.REPEAT_MODE_OFF
    PlaybackMode.RepeatAll -> Player.REPEAT_MODE_ALL
    PlaybackMode.RepeatOne -> Player.REPEAT_MODE_ONE
}

internal fun buildModePlaybackQueue(
    selectedTrackId: TrackId,
    candidates: List<PlaybackQueueTrack>,
    mode: PlaybackMode,
    random: Random = SecureRandom(),
): PlaybackQueue {
    val distinct = candidates.distinctBy { it.trackId }.toMutableList()
    if (distinct.none { it.trackId == selectedTrackId }) {
        distinct.add(0, PlaybackQueueTrack(selectedTrackId, null, null, null))
    }
    val ordered = when (mode) {
        PlaybackMode.ShuffleTracks -> {
            val selected = distinct.first { it.trackId == selectedTrackId }
            val remaining = distinct.filterNot { it.trackId == selectedTrackId }.toMutableList()
            Collections.shuffle(remaining, random)
            listOf(selected) + remaining
        }
        PlaybackMode.ShuffleAlbums -> randomizedAlbumBlocks(
            tracks = distinct,
            random = random,
            leadingAlbumKey = distinct.first { it.trackId == selectedTrackId }.albumGroupKey,
        )
        else -> distinct
    }
    return PlaybackQueue(
        tracks = ordered,
        startIndex = ordered.indexOfFirst { it.trackId == selectedTrackId },
    )
}

internal fun buildNextRandomRound(
    candidates: List<PlaybackQueueTrack>,
    mode: PlaybackMode,
    random: Random = SecureRandom(),
): List<PlaybackQueueTrack> {
    val distinct = candidates.distinctBy { it.trackId }
    return when (mode) {
        PlaybackMode.ShuffleTracks -> distinct.toMutableList().also { Collections.shuffle(it, random) }
        PlaybackMode.ShuffleAlbums -> randomizedAlbumBlocks(distinct, random, leadingAlbumKey = null)
        else -> distinct
    }
}

private fun randomizedAlbumBlocks(
    tracks: List<PlaybackQueueTrack>,
    random: Random,
    leadingAlbumKey: String?,
): List<PlaybackQueueTrack> {
    val groups = tracks.groupBy { it.albumGroupKey }
    val remainingKeys = groups.keys.filterNot { it == leadingAlbumKey }.toMutableList()
    Collections.shuffle(remainingKeys, random)
    val orderedKeys = listOfNotNull(leadingAlbumKey?.takeIf(groups::containsKey)) + remainingKeys
    return orderedKeys.flatMap { key ->
        // Stable sorting preserves input order for equal or missing positions.
        groups.getValue(key).sortedWith(
            compareBy<PlaybackQueueTrack>(
                { it.discNumber ?: Int.MAX_VALUE },
                { it.trackNumber ?: Int.MAX_VALUE },
            ),
        )
    }
}

internal val PlaybackQueueTrack.albumGroupKey: String
    get() = albumKey ?: "track:${trackId}"

internal object PlaybackModeMetadata {
    const val QueueIndex = "com.bobo.auralis.playback.QUEUE_INDEX"
    const val Mode = "com.bobo.auralis.playback.MODE"
    const val AlbumKey = "com.bobo.auralis.playback.ALBUM_KEY"
    const val DiscNumber = "com.bobo.auralis.playback.DISC_NUMBER"
    const val TrackNumber = "com.bobo.auralis.playback.TRACK_NUMBER"
}
