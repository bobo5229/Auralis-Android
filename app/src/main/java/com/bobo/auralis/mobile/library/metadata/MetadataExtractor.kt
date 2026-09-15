package com.bobo.auralis.mobile.library.metadata

import android.content.ContentResolver
import android.content.Context
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MetadataExtractor {
    suspend fun extract(target: MetadataTarget): MetadataResult

    companion object {
        fun from(context: Context): MetadataExtractor =
            MetadataExtractorImpl(context.contentResolver)
    }
}

private class MetadataExtractorImpl(private val contentResolver: ContentResolver) :
    MetadataExtractor {
    override suspend fun extract(target: MetadataTarget): MetadataResult =
        withContext(Dispatchers.IO) {
            contentResolver.openFileDescriptor(target.uri, "r")?.use { fd ->
                val fis = FileInputStream(fd.fileDescriptor)
                TagLibJNI.open(target, fis).also { fis.close() }
            } ?: MetadataResult.ProviderFailed
        }
}
