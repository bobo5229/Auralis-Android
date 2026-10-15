/*
 * Service/session lifecycle follows the AndroidX Media3 session_service demo.
 * Copyright 2023 The Android Open Source Project, licensed under Apache-2.0.
 * Auralis-specific adaptation is distributed under GPL-3.0-or-later.
 */
package com.bobo.auralis.mobile.playback

import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import java.security.SecureRandom

class AuralisPlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private data class EvidenceEvent(val evidence: PlaybackSourceEvidence, val error: PlaybackException? = null)
    private lateinit var evidenceWriter: PlaybackEvidenceWriter<EvidenceEvent>
    private val random = SecureRandom()

    override fun onCreate() {
        super.onCreate()
        val database = AuralisDatabase.get(this)
        val resolver = PlaybackMediaItemResolver(database)
        val sourceStates = PlaybackSourceStateRepository(database)
        evidenceWriter = PlaybackEvidenceWriter(
            scope = serviceScope,
            write = { (evidence, error) ->
                if (error == null) sourceStates.markPlayable(evidence)
                else sourceStates.markUnplayable(evidence, error)
            },
            onFailure = { Log.e("PlaybackEvidence", "Unable to persist playback evidence", it) },
        )

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(PlaybackEvidenceListener()) }

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            AuralisLibraryCallback(resolver),
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class AuralisLibraryCallback(
        private val resolver: PlaybackMediaItemResolver,
    ) : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = serviceScope.future(Dispatchers.IO) {
            mediaItems.map { resolver.resolve(it) }
        }
    }

    private inner class PlaybackEvidenceListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                val mode = PlaybackMode.fromKey(player.currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackModeMetadata.Mode))
                player.repeatMode = playbackRepeatMode(mode)
                player.shuffleModeEnabled = false
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val evidence = player.currentMediaItem?.sourceEvidence() ?: return
                evidenceWriter.record(EvidenceEvent(evidence))
            }
            if (playbackState == Player.STATE_ENDED) startNextRandomRoundIfNeeded()
        }

        override fun onPlayerError(error: PlaybackException) {
            val evidence = player.currentMediaItem?.sourceEvidence() ?: return
            evidenceWriter.record(EvidenceEvent(evidence, error))
        }
    }

    private fun startNextRandomRoundIfNeeded() {
        val mode = PlaybackMode.fromKey(
            player.currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackModeMetadata.Mode),
        )
        if (!mode.usesRandomRounds || player.mediaItemCount == 0) return
        val mediaItems = List(player.mediaItemCount, player::getMediaItemAt)
        val tracksById = mediaItems.associateBy { it.mediaId }
        val queueTracks = mediaItems.mapNotNull(MediaItem::toPlaybackQueueTrack)
        val nextRound = buildNextRandomRound(queueTracks, mode, random)
        if (nextRound.isEmpty()) return
        player.setMediaItems(nextRound.mapNotNull { tracksById[it.trackId.toString()] })
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.prepare()
        player.play()
    }
}
