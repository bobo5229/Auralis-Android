package com.bobo.auralis.mobile.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bobo.auralis.mobile.library.saf.SafSiblingFiles
import com.bobo.auralis.mobile.library.saf.SafSiblingDocument
import com.bobo.auralis.mobile.library.model.LibraryAudioSource
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
    private val siblings = SafSiblingFiles(resolver)
    private val extractor = MetadataExtractor.from(context.applicationContext)

    suspend fun load(source: LibraryAudioSource): Lyrics? = withContext(Dispatchers.IO) {
        val result = extractor.extract(MetadataTarget(Uri.parse(source.audioUri), source.fileName))
        if (result == MetadataResult.ProviderFailed) throw IOException("无法读取音频文件")
        val embedded = (result as? MetadataResult.Success)?.metadata?.interpret()?.embeddedLyrics
        embedded?.let(::parseLyrics)?.let { return@withContext it }
        val tree = source.treeUri?.let(Uri::parse) ?: return@withContext null
        val directoryId = siblings.directoryId(tree, source.relativePath) {
            throw IOException("无法读取歌词目录")
        } ?: throw IOException("歌词所在目录不可访问")
        val children = siblings.children(tree, directoryId) ?: throw IOException("无法读取歌词目录")
        val file = chooseLyricsCandidate(source.fileName, children) ?: return@withContext null
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

}

internal fun chooseLyricsCandidate(audioFileName: String, children: List<SafSiblingDocument>): SafSiblingDocument? {
    val name = audioFileName.substringBeforeLast('.', audioFileName) + ".lrc"
    return children.filter { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
        .sortedWith(compareBy<SafSiblingDocument> { it.name != name }.thenBy { it.name }.thenBy { it.id })
        .firstOrNull()
}

internal fun decodeLyrics(bytes: ByteArray): String {
    val utf16 = bytes.size >= 2 && (
        (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
            (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
        )
    val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
    return charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
}
