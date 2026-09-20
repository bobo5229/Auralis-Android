package com.bobo.auralis.mobile.diagnostics

import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.pipeline.ScanSessionReport

data class DiagnosticReport(
    val generatedAt: Long,
    val roots: List<DiagnosticRoot>,
    val sourceCount: Int,
    val sourceIssues: List<DiagnosticSourceIssue>,
    val latestScan: ScanSessionReport?,
    val latestScanFailure: String? = null,
)

data class DiagnosticRoot(
    val displayPath: String,
    val treeUri: String,
    val availability: String,
    val lastAttemptedScanAt: Long?,
    val lastSuccessfulScanAt: Long?,
)

data class DiagnosticSourceIssue(
    val fileName: String,
    val relativePath: String,
    val uri: String,
    val availability: String,
    val parseState: String,
    val playability: String,
    val failureStage: String?,
    val failureCode: String?,
    val failureMessage: String?,
)

fun buildDiagnosticReport(
    generatedAt: Long,
    roots: List<LibraryRootEntity>,
    sources: List<TrackSourceEntity>,
    latestScan: ScanSessionReport?,
    latestScanFailure: String? = null,
): DiagnosticReport = DiagnosticReport(
    generatedAt = generatedAt,
    roots = roots.map { root ->
        DiagnosticRoot(
            displayPath = root.displayPath,
            treeUri = root.treeUri,
            availability = root.availabilityState.name,
            lastAttemptedScanAt = root.lastAttemptedScanAt,
            lastSuccessfulScanAt = root.lastSuccessfulScanAt,
        )
    },
    sourceCount = sources.size,
    sourceIssues = sources
        .filter { source ->
            source.availabilityState != SourceAvailabilityState.AVAILABLE ||
                source.parseState != ParseState.PARSED ||
                source.playabilityState == PlayabilityState.UNPLAYABLE ||
                source.failureStage != null ||
                source.failureCode != null ||
                source.failureMessage != null
        }
        .map { source ->
            DiagnosticSourceIssue(
                fileName = source.fileName,
                relativePath = source.relativePath,
                uri = source.uri,
                availability = source.availabilityState.name,
                parseState = source.parseState.name,
                playability = source.playabilityState.name,
                failureStage = source.failureStage,
                failureCode = source.failureCode,
                failureMessage = source.failureMessage,
            )
        },
    latestScan = latestScan,
    latestScanFailure = latestScanFailure,
)

val DiagnosticReport.unavailableRootCount: Int
    get() = roots.count { it.availability == RootAvailabilityState.UNAVAILABLE.name }
