package com.bobo.auralis.mobile.library.pipeline

import com.bobo.auralis.mobile.library.metadata.MetadataResult
import kotlinx.coroutines.CancellationException

/** Per-file extraction boundary; persistence failures stay outside this boundary. */
internal data class ScanMetadataExtraction(
    val result: MetadataResult,
    val failureCode: String = result::class.java.simpleName,
    val failureMessage: String? = null,
)

internal suspend fun extractMetadataForScan(
    extract: suspend () -> MetadataResult,
): ScanMetadataExtraction = try {
    ScanMetadataExtraction(extract())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    ScanMetadataExtraction(
        result = MetadataResult.ProviderFailed,
        failureCode = error.javaClass.simpleName,
        failureMessage = error.message,
    )
}
