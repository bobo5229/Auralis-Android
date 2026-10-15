package com.bobo.auralis.mobile.diagnostics

import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.library.pipeline.DirectoryScanFailure
import com.bobo.auralis.mobile.library.pipeline.ScanSessionReport
import com.bobo.auralis.mobile.library.scan.AudioFormat
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun `report contains only source states that require diagnosis`() {
        val healthy = source(
            name = "healthy.flac",
            availability = SourceAvailabilityState.AVAILABLE,
            parse = ParseState.PARSED,
            playability = PlayabilityState.PLAYABLE,
        )
        val failed = source(
            name = "broken.mp3",
            availability = SourceAvailabilityState.AVAILABLE,
            parse = ParseState.FAILED,
            playability = PlayabilityState.UNKNOWN,
            failureStage = "extraction",
            failureCode = "NotAudio",
        )

        val report = buildDiagnosticReport(
            generatedAt = 1_000L,
            roots = listOf(root()),
            sources = listOf(healthy, failed),
            latestScan = null,
        )

        assertEquals(2, report.sourceCount)
        assertEquals(listOf("broken.mp3"), report.sourceIssues.map { it.fileName })
    }

    @Test
    fun `text and json exports retain scan access and playback diagnostics`() {
        val report = DiagnosticReport(
            generatedAt = 0L,
            roots = listOf(
                DiagnosticRoot("Music", "content://root", "UNAVAILABLE", 0L, null),
            ),
            sourceCount = 1,
            sourceIssues = listOf(
                DiagnosticSourceIssue(
                    fileName = "a\"b.mp3",
                    relativePath = "Music/a\"b.mp3",
                    uri = "content://audio/1",
                    availability = "AVAILABLE",
                    parseState = "PARSED",
                    playability = "UNPLAYABLE",
                    failureStage = "playback",
                    failureCode = "ERROR_CODE_IO_FILE_NOT_FOUND",
                    failureMessage = "line1\nline2",
                ),
            ),
            latestScan = ScanSessionReport(
                scanId = 7L,
                startedAt = 0L,
                elapsedMs = 12L,
                totalFilesDiscovered = 2,
                audioCandidatesDiscovered = 2,
                uniqueAudioCandidatesDiscovered = 1,
                cacheMisses = 2,
                extractionFailures = 1,
                parsedSuccessfully = 1,
                parseFailed = 0,
                rootsAttempted = 1,
                rootsCompleted = 0,
                unavailableDirectories = 1,
                directoryFailures = listOf(
                    DirectoryScanFailure(
                        rootUri = "content://root",
                        rootPath = "Music",
                        path = "Music/Locked",
                        code = "SecurityException",
                        message = "permission revoked",
                    ),
                ),
            ),
        )

        val text = DiagnosticReportFormatter.toText(report)
        val json = DiagnosticReportFormatter.toJson(report)

        assertTrue(text.contains("Directory access issues (1)"))
        assertTrue(text.contains("files/audio aliases/unique audio: 2/2/1"))
        assertTrue(text.contains("unique parsed/failed sources: 1/0"))
        assertTrue(text.contains("cache hydrations/extraction attempts/failures: 0/2/1"))
        assertTrue(json.contains("\"uniqueAudioCandidatesDiscovered\": 1"))
        assertTrue(json.contains("\"extractionFailures\": 1"))
        assertTrue(text.contains("ERROR_CODE_IO_FILE_NOT_FOUND"))
        assertTrue(json.contains("\"directoryIssues\""))
        assertTrue(json.contains("a\\\"b.mp3"))
        assertTrue(json.contains("line1\\nline2"))
        assertFalse(json.contains("line1\nline2"))
    }

    private fun root() = LibraryRootEntity(
        rootId = LibraryRootId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        treeUri = "content://root",
        displayPath = "Music",
        priority = 0,
        availabilityState = RootAvailabilityState.AVAILABLE,
        lastSuccessfulScanAt = null,
        lastAttemptedScanAt = null,
        createdAt = 0L,
    )

    private fun source(
        name: String,
        availability: SourceAvailabilityState,
        parse: ParseState,
        playability: PlayabilityState,
        failureStage: String? = null,
        failureCode: String? = null,
    ) = TrackSourceEntity(
        sourceId = TrackSourceId.random(),
        trackId = TrackId.random(),
        provider = "provider",
        documentId = name,
        rootId = null,
        uri = "content://audio/$name",
        relativePath = "Music/$name",
        fileName = name,
        format = AudioFormat.MP3,
        mimeType = "audio/mpeg",
        sizeBytes = 1L,
        modifiedMs = 1L,
        durationMs = null,
        bitrateKbps = null,
        sampleRateHz = null,
        availabilityState = availability,
        parseState = parse,
        playabilityState = playability,
        lastSeenScanId = null,
        lastSeenAt = null,
        failureStage = failureStage,
        failureCode = failureCode,
        failureMessage = null,
    )
}
