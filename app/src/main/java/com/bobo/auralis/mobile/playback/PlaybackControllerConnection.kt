package com.bobo.auralis.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bobo.auralis.mobile.library.model.TrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackConnectionState(
    val isConnected: Boolean = false,
    val currentMediaId: String? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

/** UI-side MediaController connection; Compose never owns an ExoPlayer instance. */
class PlaybackControllerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, AuralisPlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(PlaybackConnectionState())
    val state: StateFlow<PlaybackConnectionState> = mutableState.asStateFlow()

    private val positionPublisher = object : Runnable {
        override fun run() {
            publish(controller)
            mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onPlayerError(error: PlaybackException) {
            publish(controller, "${error.errorCodeName}: ${error.message ?: "未知播放错误"}")
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess {
                        controller = it
                        it.addListener(playerListener)
                        publish(it)
                        mainHandler.post(positionPublisher)
                    }
                    .onFailure {
                        mutableState.value = PlaybackConnectionState(
                            error = "无法连接播放服务: ${it.message ?: it.javaClass.simpleName}",
                        )
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(trackId: TrackId) {
        val activeController = controller ?: return
        activeController.setMediaItem(MediaItem.Builder().setMediaId(trackId.toString()).build())
        activeController.prepare()
        activeController.play()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun stop() {
        controller?.stop()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun release() {
        mainHandler.removeCallbacks(positionPublisher)
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun publish(player: Player?, error: String? = null) {
        mutableState.value = PlaybackConnectionState(
            isConnected = player != null,
            currentMediaId = player?.currentMediaItem?.mediaId,
            isPlaying = player?.isPlaying == true,
            playbackState = player?.playbackState ?: Player.STATE_IDLE,
            positionMs = player?.currentPosition?.coerceAtLeast(0) ?: 0,
            durationMs = player?.duration?.takeIf { it > 0 } ?: 0,
            error = error,
        )
    }


    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}
