package com.bobo.auralis.mobile.library.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

internal data class SafSiblingDocument(val id: String, val name: String, val mimeType: String?) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

/** Only locates documents; callers own candidate priority and failure presentation. */
internal class SafSiblingFiles(private val resolver: ContentResolver) {
    fun directoryId(tree: Uri, relativePath: String, onQueryUnavailable: () -> Unit = {}): String? {
        val segments = siblingDirectorySegments(SafLocation.restore(tree).path.toString(), relativePath)
        var directoryId = DocumentsContract.getTreeDocumentId(tree)
        for (segment in segments) {
            val children = children(tree, directoryId) ?: run {
                onQueryUnavailable()
                return null
            }
            directoryId = children.firstOrNull { it.name == segment && it.isDirectory }?.id ?: return null
        }
        return directoryId
    }

    fun children(tree: Uri, directoryId: String): List<SafSiblingDocument>? {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directoryId)
        return resolver.query(uri, columns, null, null, null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(columns[0])
            val name = cursor.getColumnIndexOrThrow(columns[1])
            val mime = cursor.getColumnIndexOrThrow(columns[2])
            buildList {
                while (cursor.moveToNext()) {
                    add(SafSiblingDocument(cursor.getString(id), cursor.getString(name), cursor.getString(mime)))
                }
            }
        }
    }
}

internal fun siblingDirectorySegments(rootPath: String, relativePath: String): List<String> {
    val belowRoot = relativePath.removePrefix(rootPath).trimStart('/')
    return belowRoot.split('/').filter { it.isNotEmpty() }.dropLast(1)
}
