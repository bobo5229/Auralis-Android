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
import com.bobo.auralis.mobile.library.saf.SafSiblingFiles
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
    private val siblings = SafSiblingFiles(contentResolver)
    private val extractor = MetadataExtractor.from(context)

    suspend fun resolve(source: LibraryArtworkSource): ResolvedArtwork? = withContext(Dispatchers.IO) {
        val audio = source.audioSource
        if (source.hasEmbeddedArtwork) {
            val result = extractor.extract(MetadataTarget(Uri.parse(audio.audioUri), audio.fileName))
            val embedded = (result as? MetadataResult.Success)?.metadata?.cover
            if (embedded != null && embedded.isNotEmpty() && isDecodableImage(embedded)) {
                return@withContext ResolvedArtwork.Embedded(embedded)
            }
        }

        val treeUri = audio.treeUri?.let(Uri::parse) ?: return@withContext null
        val directoryId = siblings.directoryId(treeUri, audio.relativePath)
            ?: return@withContext null
        resolveSiblingCover(treeUri, directoryId)
    }

    private fun resolveSiblingCover(treeUri: Uri, directoryId: String): ResolvedArtwork.Sibling? {
        val candidates = siblings.children(treeUri, directoryId).orEmpty()
            .filter { isSupportedImage(it.name, it.mimeType) }
            .map { CoverCandidate(it.id, it.name, it.mimeType) }
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

}

internal data class CoverCandidate(val documentId: String, val fileName: String, val mimeType: String?)

internal fun chooseCoverCandidate(candidates: List<CoverCandidate>): CoverCandidate? =
    orderCoverCandidates(candidates).firstOrNull()

internal fun orderCoverCandidates(candidates: List<CoverCandidate>): List<CoverCandidate> =
    candidates.filter { fileStem(it.fileName) == "cover" }.sortedBy { it.fileName } +
        candidates.filter { fileStem(it.fileName) == "COVER" }.sortedBy { it.fileName }

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
