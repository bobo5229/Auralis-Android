package com.bobo.auralis.mobile.artwork

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.saf.SafLocation
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

class LibraryArtworkKeyer : Keyer<LibraryArtworkSource> {
    override fun key(data: LibraryArtworkSource, options: Options): String = data.cacheKey
}

class LibraryArtworkFetcher private constructor(
    private val data: LibraryArtworkSource,
    private val options: Options,
    private val resolver: LibraryArtworkResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        return when (val resolved = resolver.resolve(data)) {
            is ResolvedArtwork.Embedded -> SourceFetchResult(
                source = ImageSource(
                    source = ByteArrayInputStream(resolved.bytes).source().buffer(),
                    fileSystem = options.fileSystem,
                ),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
            is ResolvedArtwork.Sibling -> {
                val descriptor = resolver.contentResolver.openAssetFileDescriptor(resolved.uri, "r") ?: return null
                SourceFetchResult(
                    source = ImageSource(
                        source = descriptor.createInputStream().source().buffer(),
                        fileSystem = options.fileSystem,
                    ),
                    mimeType = resolved.mimeType,
                    dataSource = DataSource.DISK,
                )
            }
            null -> null
        }
    }

    class Factory(context: Context) : Fetcher.Factory<LibraryArtworkSource> {
        private val resolver = LibraryArtworkResolver(context)

        override fun create(
            data: LibraryArtworkSource,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = LibraryArtworkFetcher(data, options, resolver)
    }
}

internal sealed interface ResolvedArtwork {
    data class Embedded(val bytes: ByteArray) : ResolvedArtwork
    data class Sibling(val uri: Uri, val mimeType: String?) : ResolvedArtwork
}

internal class LibraryArtworkResolver(context: Context) {
    val contentResolver: ContentResolver = context.contentResolver
    private val extractor = MetadataExtractor.from(context)

    suspend fun resolve(source: LibraryArtworkSource): ResolvedArtwork? = withContext(Dispatchers.IO) {
        if (source.hasEmbeddedArtwork) {
            val result = extractor.extract(MetadataTarget(Uri.parse(source.audioUri), source.fileName))
            val embedded = (result as? MetadataResult.Success)?.metadata?.cover
            if (embedded != null && embedded.isNotEmpty() && isDecodableImage(embedded)) {
                return@withContext ResolvedArtwork.Embedded(embedded)
            }
        }

        val treeUri = source.treeUri?.let(Uri::parse) ?: return@withContext null
        val directoryId = resolveDirectoryId(treeUri, siblingDirectorySegments(treeUri, source.relativePath))
            ?: return@withContext null
        resolveSiblingCover(treeUri, directoryId)
    }

    private fun resolveDirectoryId(treeUri: Uri, segments: List<String>): String? {
        var directoryId = DocumentsContract.getTreeDocumentId(treeUri)
        for (segment in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
            directoryId = contentResolver.query(
                childrenUri,
                DIRECTORY_PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                var match: String? = null
                while (cursor.moveToNext() && match == null) {
                    if (cursor.getString(nameIndex) == segment &&
                        cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        match = cursor.getString(idIndex)
                    }
                }
                match
            } ?: return null
        }
        return directoryId
    }

    private fun resolveSiblingCover(treeUri: Uri, directoryId: String): ResolvedArtwork.Sibling? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
        val candidates = contentResolver.query(childrenUri, FILE_PROJECTION, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeIndex)
                    if (isSupportedImage(name, mimeType)) {
                        add(CoverCandidate(cursor.getString(idIndex), name, mimeType))
                    }
                }
            }
        }.orEmpty()
        val chosen = orderCoverCandidates(candidates).firstOrNull { candidate ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, candidate.documentId)
            runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    options.outWidth > 0 && options.outHeight > 0
                } == true
            }.getOrDefault(false)
        } ?: return null
        return ResolvedArtwork.Sibling(
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, chosen.documentId),
            mimeType = chosen.mimeType,
        )
    }

    companion object {
        private val DIRECTORY_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        private val FILE_PROJECTION = DIRECTORY_PROJECTION
    }
}

internal data class CoverCandidate(val documentId: String, val fileName: String, val mimeType: String?)

internal fun chooseCoverCandidate(candidates: List<CoverCandidate>): CoverCandidate? =
    orderCoverCandidates(candidates).firstOrNull()

internal fun orderCoverCandidates(candidates: List<CoverCandidate>): List<CoverCandidate> =
    candidates.filter { fileStem(it.fileName) == "cover" }.sortedBy { it.fileName } +
        candidates.filter { fileStem(it.fileName) == "COVER" }.sortedBy { it.fileName }

internal fun siblingDirectorySegments(treeUri: Uri, relativePath: String): List<String> {
    val rootPath = SafLocation.restore(treeUri).path.toString()
    return siblingDirectorySegments(rootPath, relativePath)
}

internal fun siblingDirectorySegments(rootPath: String, relativePath: String): List<String> {
    val belowRoot = relativePath.removePrefix(rootPath).trimStart('/')
    return belowRoot.split('/').filter { it.isNotEmpty() }.dropLast(1)
}

private fun fileStem(fileName: String): String = fileName.substringBeforeLast('.', fileName)

private fun isSupportedImage(fileName: String, mimeType: String?): Boolean {
    if (mimeType?.startsWith("image/") == true) return true
    return fileName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
}

private fun isDecodableImage(bytes: ByteArray): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return options.outWidth > 0 && options.outHeight > 0
}
