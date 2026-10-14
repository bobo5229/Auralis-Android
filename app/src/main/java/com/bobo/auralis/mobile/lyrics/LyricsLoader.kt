package com.bobo.auralis.mobile.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bobo.auralis.mobile.artwork.siblingDirectorySegments
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.interpret
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LyricsLoader(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val extractor = MetadataExtractor.from(context.applicationContext)

    suspend fun load(source: LibraryArtworkSource): Lyrics? = withContext(Dispatchers.IO) {
        val result = extractor.extract(MetadataTarget(Uri.parse(source.audioUri), source.fileName))
        if (result == MetadataResult.ProviderFailed) throw IOException("无法读取音频文件")
        val embedded = (result as? MetadataResult.Success)?.metadata?.interpret()?.embeddedLyrics
        embedded?.let(::parseLyrics)?.let { return@withContext it }
        val tree = source.treeUri?.let(Uri::parse) ?: return@withContext null
        var directoryId = DocumentsContract.getTreeDocumentId(tree)
        for (segment in siblingDirectorySegments(tree, source.relativePath)) {
            directoryId = children(tree, directoryId).firstOrNull {
                it.name == segment && it.isDirectory
            }?.id ?: throw IOException("歌词所在目录不可访问")
        }
        val name = source.fileName.substringBeforeLast('.', source.fileName) + ".lrc"
        val file = children(tree, directoryId).filter { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
            .sortedWith(compareBy<Document> { it.name != name }.thenBy { it.name }.thenBy { it.id })
            .firstOrNull() ?: return@withContext null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id)
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > 2 * 1024 * 1024) throw IOException("歌词文件超过 2 MB")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("无法打开歌词文件")
        parseLyrics(decodeLyrics(bytes))
    }

    private data class Document(val id: String, val name: String, val isDirectory: Boolean)

    private fun children(tree: Uri, directoryId: String): List<Document> {
        val columns = arrayOf("document_id", "_display_name", "mime_type")
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directoryId)
        return resolver.query(uri, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Document(cursor.getString(0), cursor.getString(1), cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } ?: throw IOException("无法读取歌词目录")
    }
}

internal fun decodeLyrics(bytes: ByteArray): String {
    val utf16 = bytes.size >= 2 && (
        (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
            (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
        )
    val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
    return charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
}
