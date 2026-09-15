package com.bobo.auralis.mobile.library.scan

import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDocumentCollectorTest {
    @Test
    fun parentAndChildRootOverlapIsDeduplicated() {
        val collector = ScanDocumentCollector()
        val documentKey =
            SafDocumentKey(
                provider = EXTERNAL_STORAGE_PROVIDER,
                documentId = "primary:Music/Albums/track.flac",
            )

        val fromParentRoot =
            collector.accept(documentKey, "track.flac", 100, "primary/Music")
        val fromChildRoot =
            collector.accept(documentKey, "track.flac", 100, "primary/Music/Albums")

        assertTrue(fromParentRoot is DocumentAcceptResult.NewDocument)
        assertEquals(DocumentAcceptResult.Duplicate, fromChildRoot)
        assertEquals(1, collector.documentCount)
        assertEquals(1, collector.candidateCount)
    }

    @Test
    fun sameDocumentIdFromDifferentProvidersIsNotDeduplicated() {
        val collector = ScanDocumentCollector()

        val first = collector.accept(SafDocumentKey("provider.a", "1"), "song.mp3", 10, "root A")
        val second = collector.accept(SafDocumentKey("provider.b", "1"), "song.mp3", 20, "root B")

        assertTrue(first is DocumentAcceptResult.NewDocument)
        assertTrue(second is DocumentAcceptResult.NewDocument)
        assertEquals(2, collector.documentCount)
        assertEquals(2, collector.candidateCount)
    }

    @Test
    fun nonAudioDocumentsAreCountedButNotCandidates() {
        val collector = ScanDocumentCollector()
        collector.accept(SafDocumentKey("p", "cover"), "cover.jpg", 10, "root")
        collector.accept(SafDocumentKey("p", "notes"), "notes.txt", 10, "root")
        val result = collector.accept(SafDocumentKey("p", "song"), "song.mp3", 30, "root")

        assertEquals(3, collector.documentCount)
        assertEquals(1, collector.candidateCount)
        val candidate = (result as DocumentAcceptResult.NewDocument).candidate
        assertEquals("song.mp3", candidate?.fileName)
        assertEquals(AudioFormat.MP3, candidate?.format)
        assertEquals(30L, candidate?.size)
        assertEquals("root", candidate?.rootLabel)
    }

    @Test
    fun duplicateDocumentIsReportedAsDuplicateAndNotCountedTwice() {
        val collector = ScanDocumentCollector()
        val key = SafDocumentKey("p", "song")

        collector.accept(key, "song.flac", 1, "root")
        val duplicate = collector.accept(key, "song.flac", 1, "root")

        assertEquals(DocumentAcceptResult.Duplicate, duplicate)
        assertEquals(1, collector.documentCount)
        assertEquals(1, collector.candidateCount)
    }

    private companion object {
        const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"
    }
}
