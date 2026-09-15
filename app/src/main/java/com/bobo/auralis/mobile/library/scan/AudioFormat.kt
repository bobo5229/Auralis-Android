package com.bobo.auralis.mobile.library.scan

/**
 * Candidate audio formats for the Technical Spike scan stage.
 *
 * Auralis v1 reads MP3 / AAC / ALAC / FLAC. This spike only classifies by file extension; tag and
 * container parsing happen later, so `.m4a` is kept as its own label instead of being guessed as
 * AAC vs ALAC.
 *
 * Pure logic: intentionally free of Android dependencies so it can be covered by fast JVM tests.
 */
enum class AudioFormat(val extension: String, val label: String) {
    MP3("mp3", "MP3"),
    M4A("m4a", "M4A"),
    AAC("aac", "AAC"),
    FLAC("flac", "FLAC");

    companion object {
        /**
         * Returns the candidate format for [fileName], or null when the extension is not one of
         * the supported candidates. Comparison is case-insensitive.
         *
         * Only the final extension is considered (`song.mp3.bak` is not an MP3).
         */
        fun fromFileName(fileName: String): AudioFormat? {
            val extension = fileName.substringAfterLast('.', "")
            if (extension.isEmpty()) return null
            return entries.firstOrNull { it.extension.equals(extension, ignoreCase = true) }
        }
    }
}
