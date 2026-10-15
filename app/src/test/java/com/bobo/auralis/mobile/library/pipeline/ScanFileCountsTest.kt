package com.bobo.auralis.mobile.library.pipeline

import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFileCountsTest {
    private val key = SafDocumentKey("provider", "same-document")

    @Test fun `both overlapping root orders count all aliases and one successful document`() {
        // Earlier root first skips work for the later alias; the reverse order processes both.
        for (processBoth in listOf(false, true)) {
            val counts = ScanFileCounts()
            counts.discovered(key)
            counts.parsed(key)
            counts.discovered(key)
            if (processBoth) counts.parsed(key)
            assertEquals(2, counts.audioCandidatesDiscovered)
            assertEquals(1, counts.uniqueAudioCandidatesDiscovered)
            assertEquals(1, counts.parsedSuccessfully)
            assertEquals(0, counts.parseFailed)
        }
    }

    @Test fun `successful alias wins over failure in either order without losing failed attempt count`() {
        for (successFirst in listOf(false, true)) {
            val counts = ScanFileCounts()
            for (success in listOf(successFirst, !successFirst)) {
                counts.discovered(key)
                if (success) counts.parsed(key) else counts.failed(key)
            }
            assertEquals(1, counts.parsedSuccessfully)
            assertEquals(0, counts.parseFailed)
            assertEquals(1, counts.extractionFailures)
        }
    }

    @Test fun `repeated failures count one failed source and multiple extraction failures`() {
        val counts = ScanFileCounts()
        repeat(2) {
            counts.discovered(key)
            counts.failed(key)
        }
        assertEquals(2, counts.audioCandidatesDiscovered)
        assertEquals(1, counts.uniqueAudioCandidatesDiscovered)
        assertEquals(0, counts.parsedSuccessfully)
        assertEquals(1, counts.parseFailed)
        assertEquals(2, counts.extractionFailures)
    }
}
