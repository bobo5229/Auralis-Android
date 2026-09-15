package com.bobo.auralis.mobile.library.scan

import android.net.Uri
import com.bobo.auralis.mobile.library.saf.SafDocumentKey

/** A file that passed the spike's extension filter. */
data class CandidateAudio(
    val documentKey: SafDocumentKey,
    val uri: Uri? = null,
    val fileName: String,
    val format: AudioFormat,
    val size: Long,
    val rootLabel: String,
)

/** Result of accepting one scanned document. */
sealed interface DocumentAcceptResult {
    /** The document was already seen under another root/provider path and must not be counted. */
    data object Duplicate : DocumentAcceptResult

    /**
     * A new document. [candidate] is non-null when its extension matches an [AudioFormat].
     */
    data class NewDocument(val candidate: CandidateAudio?) : DocumentAcceptResult
}

/**
 * De-duplicates scanned documents by [SafDocumentKey] and filters candidate audio by extension.
 *
 * The key design is what makes the parent-root + child-root overlap safe: when the same physical
 * document is discovered through `primary:Music` and through `primary:Music/Albums`, the provider
 * reports the same document ID for both, so both events map to the same key and only the first one
 * is counted.
 *
 * Pure logic: no Android types are used in processing, so this is covered by fast JVM tests.
 */
class ScanDocumentCollector {
    private val seenDocuments = mutableSetOf<SafDocumentKey>()
    private val candidates = mutableListOf<CandidateAudio>()

    /** Number of unique documents accepted so far (audio and non-audio). */
    var documentCount = 0
        private set

    /** Number of unique candidate audio files accepted so far. */
    val candidateCount: Int
        get() = candidates.size

    fun accept(
        documentKey: SafDocumentKey,
        fileName: String,
        size: Long,
        rootLabel: String,
        uri: Uri? = null,
    ): DocumentAcceptResult {
        if (!seenDocuments.add(documentKey)) {
            return DocumentAcceptResult.Duplicate
        }

        documentCount++
        val format = AudioFormat.fromFileName(fileName)
        if (format == null) {
            return DocumentAcceptResult.NewDocument(candidate = null)
        }

        val candidate =
            CandidateAudio(
                documentKey = documentKey,
                uri = uri,
                fileName = fileName,
                format = format,
                size = size,
                rootLabel = rootLabel,
            )
        candidates.add(candidate)
        return DocumentAcceptResult.NewDocument(candidate)
    }
}
