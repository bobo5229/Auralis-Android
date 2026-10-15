package com.bobo.auralis.mobile.library.pipeline

import com.bobo.auralis.mobile.library.saf.SafDocumentKey

/** Discovery counts aliases; final parse outcomes count unique physical documents. */
internal class ScanFileCounts {
    private val outcomes = mutableMapOf<SafDocumentKey, Boolean?>()
    var audioCandidatesDiscovered: Int = 0
        private set
    var extractionFailures: Int = 0
        private set
    val uniqueAudioCandidatesDiscovered: Int get() = outcomes.size
    val parsedSuccessfully: Int get() = outcomes.values.count { it == true }
    val parseFailed: Int get() = outcomes.values.count { it == false }

    fun discovered(key: SafDocumentKey) {
        audioCandidatesDiscovered++
        if (key !in outcomes) outcomes[key] = null
    }

    fun parsed(key: SafDocumentKey) {
        outcomes[key] = true
    }

    fun failed(key: SafDocumentKey) {
        extractionFailures++
        // A failed alias cannot invalidate another alias that parsed the document.
        if (outcomes[key] != true) outcomes[key] = false
    }
}
