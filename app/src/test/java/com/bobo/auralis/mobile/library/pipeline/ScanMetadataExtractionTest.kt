package com.bobo.auralis.mobile.library.pipeline

import com.bobo.auralis.mobile.library.metadata.MetadataResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ScanMetadataExtractionTest {
    @Test
    fun `file access failures retain diagnostics and allow the next file to be extracted`() = runBlocking {
        val errors = listOf(
            FileNotFoundException("removed after discovery"),
            SecurityException("permission revoked"),
            IOException("broken file"),
        )
        for (error in errors) {
            val failed = extractMetadataForScan { throw error }
            assertSame(MetadataResult.ProviderFailed, failed.result)
            assertEquals(error.javaClass.simpleName, failed.failureCode)
            assertEquals(error.message, failed.failureMessage)

            val nextFile = MetadataResult.Success(null)
            assertSame(nextFile, extractMetadataForScan { nextFile }.result)
        }
    }

    @Test
    fun `reported native failure keeps its diagnostic code`() = runBlocking {
        val failed = extractMetadataForScan { MetadataResult.NotAudio }
        assertSame(MetadataResult.NotAudio, failed.result)
        assertEquals("NotAudio", failed.failureCode)
    }

    @Test
    fun `cancellation propagates without being converted to a file failure`() = runBlocking {
        val cancellation = CancellationException("scan cancelled")
        try {
            extractMetadataForScan { throw cancellation }
            fail("Cancellation must stop the scan")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `fatal errors propagate instead of pretending the scan completed`() = runBlocking {
        val fatal = AssertionError("unexpected engine failure")
        try {
            extractMetadataForScan { throw fatal }
            fail("Fatal error must propagate")
        } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
    }
}
