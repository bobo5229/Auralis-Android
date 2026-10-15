package com.bobo.auralis.mobile.diagnostics

import java.time.Instant

enum class DiagnosticExportFormat(
    val fileExtension: String,
    val mimeType: String,
) {
    TEXT("txt", "text/plain"),
    JSON("json", "application/json"),
}

object DiagnosticReportFormatter {
    fun format(report: DiagnosticReport, format: DiagnosticExportFormat): String = when (format) {
        DiagnosticExportFormat.TEXT -> toText(report)
        DiagnosticExportFormat.JSON -> toJson(report)
    }

    fun suggestedFileName(format: DiagnosticExportFormat, generatedAt: Long): String =
        "auralis-diagnostics-${Instant.ofEpochMilli(generatedAt).toString().replace(':', '-')}.${format.fileExtension}"

    fun toText(report: DiagnosticReport): String = buildString {
        appendLine("Auralis Mobile Diagnostics")
        appendLine("Generated: ${timestamp(report.generatedAt)}")
        appendLine("Roots: ${report.roots.size} (unavailable: ${report.unavailableRootCount})")
        appendLine("Sources: ${report.sourceCount} (issues: ${report.sourceIssues.size})")

        report.latestScan?.let { scan ->
            appendLine()
            appendLine("Latest scan")
            appendLine("  scanId: ${scan.scanId}")
            appendLine("  startedAt: ${timestamp(scan.startedAt)}")
            appendLine("  elapsedMs: ${scan.elapsedMs}")
            appendLine("  files/audio aliases/unique audio: ${scan.totalFilesDiscovered}/${scan.audioCandidatesDiscovered}/${scan.uniqueAudioCandidatesDiscovered}")
            appendLine("  unique parsed/failed sources: ${scan.parsedSuccessfully}/${scan.parseFailed}")
            appendLine("  cache hydrations/extraction attempts/failures: ${scan.cacheHits}/${scan.cacheMisses}/${scan.extractionFailures}")
            appendLine("  roots completed/attempted: ${scan.rootsCompleted}/${scan.rootsAttempted}")
            appendLine("  unavailableDirectories: ${scan.unavailableDirectories}")
        }
        report.latestScanFailure?.let { appendLine("Latest scan failure: $it") }

        appendLine()
        appendLine("Library roots")
        report.roots.forEachIndexed { index, root ->
            appendLine("  ${index + 1}. ${root.displayPath}")
            appendLine("     uri: ${root.treeUri}")
            appendLine("     availability: ${root.availability}")
            appendLine("     lastAttempted: ${timestamp(root.lastAttemptedScanAt)}")
            appendLine("     lastSuccessful: ${timestamp(root.lastSuccessfulScanAt)}")
        }

        val directoryFailures = report.latestScan?.directoryFailures.orEmpty()
        appendLine()
        appendLine("Directory access issues (${directoryFailures.size})")
        directoryFailures.forEachIndexed { index, failure ->
            appendLine("  ${index + 1}. ${failure.path}")
            appendLine("     root: ${failure.rootPath} (${failure.rootUri})")
            appendLine("     error: ${failure.code}${failure.message?.let { ": $it" }.orEmpty()}")
        }

        appendLine()
        appendLine("Source issues (${report.sourceIssues.size})")
        report.sourceIssues.forEachIndexed { index, source ->
            appendLine("  ${index + 1}. ${source.relativePath.ifBlank { source.fileName }}")
            appendLine("     uri: ${source.uri}")
            appendLine("     states: ${source.availability}/${source.parseState}/${source.playability}")
            appendLine("     failure: ${listOfNotNull(source.failureStage, source.failureCode, source.failureMessage).joinToString(" | ").ifEmpty { "none recorded" }}")
        }
    }

    fun toJson(report: DiagnosticReport): String = buildString {
        append("{\n")
        field("generatedAt", timestamp(report.generatedAt), 1)
        append(",\n")
        append("  \"summary\": {\"rootCount\": ${report.roots.size}, \"unavailableRootCount\": ${report.unavailableRootCount}, \"sourceCount\": ${report.sourceCount}, \"sourceIssueCount\": ${report.sourceIssues.size}},\n")
        append("  \"latestScan\": ")
        val scan = report.latestScan
        if (scan == null) {
            append("null")
        } else {
            append("{\n")
            numberField("scanId", scan.scanId, 2)
            append(",\n")
            field("startedAt", timestamp(scan.startedAt), 2)
            append(",\n")
            numberField("elapsedMs", scan.elapsedMs, 2)
            append(",\n")
            numberField("totalFilesDiscovered", scan.totalFilesDiscovered, 2)
            append(",\n")
            numberField("audioCandidatesDiscovered", scan.audioCandidatesDiscovered, 2)
            append(",\n")
            numberField("uniqueAudioCandidatesDiscovered", scan.uniqueAudioCandidatesDiscovered, 2)
            append(",\n")
            numberField("parsedSuccessfully", scan.parsedSuccessfully, 2)
            append(",\n")
            numberField("parseFailed", scan.parseFailed, 2)
            append(",\n")
            numberField("cacheHits", scan.cacheHits, 2)
            append(",\n")
            numberField("cacheMisses", scan.cacheMisses, 2)
            append(",\n")
            numberField("extractionFailures", scan.extractionFailures, 2)
            append(",\n")
            numberField("rootsAttempted", scan.rootsAttempted, 2)
            append(",\n")
            numberField("rootsCompleted", scan.rootsCompleted, 2)
            append(",\n")
            numberField("unavailableDirectories", scan.unavailableDirectories, 2)
            append("\n  }")
        }
        append(",\n")
        nullableField("latestScanFailure", report.latestScanFailure, 1)
        append(",\n  \"roots\": [")
        report.roots.forEachIndexed { index, root ->
            if (index > 0) append(',')
            append("\n    {")
            inlineField("displayPath", root.displayPath)
            append(", ")
            inlineField("treeUri", root.treeUri)
            append(", ")
            inlineField("availability", root.availability)
            append(", ")
            nullableInlineField("lastAttemptedScanAt", root.lastAttemptedScanAt?.let(::timestamp))
            append(", ")
            nullableInlineField("lastSuccessfulScanAt", root.lastSuccessfulScanAt?.let(::timestamp))
            append('}')
        }
        if (report.roots.isNotEmpty()) append('\n').append("  ")
        append("],\n  \"directoryIssues\": [")
        report.latestScan?.directoryFailures.orEmpty().forEachIndexed { index, failure ->
            if (index > 0) append(',')
            append("\n    {")
            inlineField("rootUri", failure.rootUri)
            append(", ")
            inlineField("rootPath", failure.rootPath)
            append(", ")
            inlineField("path", failure.path)
            append(", ")
            inlineField("code", failure.code)
            append(", ")
            nullableInlineField("message", failure.message)
            append('}')
        }
        if (report.latestScan?.directoryFailures.orEmpty().isNotEmpty()) append('\n').append("  ")
        append("],\n  \"sourceIssues\": [")
        report.sourceIssues.forEachIndexed { index, source ->
            if (index > 0) append(',')
            append("\n    {")
            inlineField("fileName", source.fileName)
            append(", ")
            inlineField("relativePath", source.relativePath)
            append(", ")
            inlineField("uri", source.uri)
            append(", ")
            inlineField("availability", source.availability)
            append(", ")
            inlineField("parseState", source.parseState)
            append(", ")
            inlineField("playability", source.playability)
            append(", ")
            nullableInlineField("failureStage", source.failureStage)
            append(", ")
            nullableInlineField("failureCode", source.failureCode)
            append(", ")
            nullableInlineField("failureMessage", source.failureMessage)
            append('}')
        }
        if (report.sourceIssues.isNotEmpty()) append('\n').append("  ")
        append("]\n}")
    }

    private fun timestamp(value: Long?): String = value?.let { Instant.ofEpochMilli(it).toString() } ?: "never"

    private fun StringBuilder.field(name: String, value: String, indent: Int) {
        append("  ".repeat(indent))
        inlineField(name, value)
    }

    private fun StringBuilder.numberField(name: String, value: Number, indent: Int) {
        append("  ".repeat(indent)).append('"').append(name).append("\": ").append(value)
    }

    private fun StringBuilder.nullableField(name: String, value: String?, indent: Int) {
        append("  ".repeat(indent))
        nullableInlineField(name, value)
    }

    private fun StringBuilder.inlineField(name: String, value: String) {
        append('"').append(name).append("\": \"").appendJsonEscaped(value).append('"')
    }

    private fun StringBuilder.nullableInlineField(name: String, value: String?) {
        append('"').append(name).append("\": ")
        if (value == null) append("null") else append('"').appendJsonEscaped(value).append('"')
    }

    private fun StringBuilder.appendJsonEscaped(value: String): StringBuilder = apply {
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }
}
