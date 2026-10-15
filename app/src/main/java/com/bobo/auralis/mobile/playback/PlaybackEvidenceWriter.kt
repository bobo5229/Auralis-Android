package com.bobo.auralis.mobile.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Enqueue on the player thread; one worker finishes each write before starting the next. */
internal class PlaybackEvidenceWriter<T>(
    scope: CoroutineScope,
    write: suspend (T) -> Unit,
    onFailure: (Exception) -> Unit,
) {
    private val events = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            try {
                for (event in events) {
                    try {
                        write(event)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        onFailure(error)
                    }
                }
            } finally {
                events.cancel()
            }
        }
    }

    fun record(event: T) {
        events.trySend(event)
    }
}
