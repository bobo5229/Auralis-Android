package com.bobo.auralis.mobile.playback

import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackEvidenceWriterTest {
    private val evidence = PlaybackSourceEvidence(TrackId.random(), TrackSourceId.random(), 100, 200)
    private val decoderError = "DECODING_FAILED"

    @Test fun `error waits for earlier suspended ready write and remains the final evidence`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val readyStarted = CompletableDeferred<Unit>()
        val releaseReady = CompletableDeferred<Unit>()
        val errorStarted = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val unexpected = CompletableDeferred<Exception>()
        val writes = mutableListOf<String?>()
        val writer = PlaybackEvidenceWriter<Pair<PlaybackSourceEvidence, String?>>(scope, { (actual, error) ->
            assertEquals(evidence, actual)
            if (error == null) {
                readyStarted.complete(Unit)
                releaseReady.await()
            } else {
                errorStarted.complete(Unit)
                // This assertion runs inside the worker, before the second commit.
                assertEquals(listOf<String?>(null), writes)
            }
            writes += error
            if (error != null) finished.complete(Unit)
        }, { unexpected.complete(it) })
        try {
            withTimeout(5_000) {
                writer.record(evidence to null)
                readyStarted.await()
                writer.record(evidence to decoderError)
                // Give a wrongly concurrent writer a chance to start while READY is blocked.
                assertNull(withTimeoutOrNull(100) { errorStarted.await() })
                releaseReady.complete(Unit)
                finished.await()
                assertFalse(unexpected.isCompleted)
                assertEquals(listOf(null, decoderError), writes)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test fun `one failed persistence operation does not drop later evidence`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failure = IllegalStateException("database write failed")
        val reported = CompletableDeferred<Exception>()
        val finished = CompletableDeferred<String?>()
        val writer = PlaybackEvidenceWriter<Pair<PlaybackSourceEvidence, String?>>(scope, { (_, error) ->
            if (error == null) throw failure
            finished.complete(error)
        }, { reported.complete(it) })
        try {
            withTimeout(5_000) {
                writer.record(evidence to null)
                writer.record(evidence to decoderError)
                assertEquals(failure, reported.await())
                assertEquals(decoderError, finished.await())
            }
        } finally {
            scope.cancel()
        }
    }
}
