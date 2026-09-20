/*
 * Service/session lifecycle follows the AndroidX Media3 session_service demo.
 * Copyright 2023 The Android Open Source Project, licensed under Apache-2.0.
 * Auralis-specific adaptation is distributed under GPL-3.0-or-later.
 */
package com.bobo.auralis.mobile.playback

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
import kotlinx.coroutines.launch

class AuralisPlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sourceStates: PlaybackSourceStateRepository

    override fun onCreate() {
        super.onCreate()
        val database = AuralisDatabase.get(this)
        val resolver = PlaybackMediaItemResolver(database)
        sourceStates = PlaybackSourceStateRepository(database)

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
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val mediaId = player.currentMediaItem?.mediaId ?: return
                serviceScope.launch(Dispatchers.IO) { sourceStates.markPlayable(mediaId) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val mediaId = player.currentMediaItem?.mediaId ?: return
            serviceScope.launch(Dispatchers.IO) { sourceStates.markUnplayable(mediaId, error) }
        }
    }
}
