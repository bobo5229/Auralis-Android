package com.bobo.auralis.mobile.library.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException

/** Only volume-relative document IDs establish ancestry; opaque IDs require a later scan. */
internal fun treeContainsDocument(treeDocumentId: String, documentId: String): Boolean {
    val tree = SafTreePathParser.parse(treeDocumentId) ?: return false
    val document = SafTreePathParser.parse(documentId) ?: return false
    return tree.root == document.root && tree.components.contains(document.components)
}

internal fun ContentResolver.canReadDocument(uri: Uri, documentId: String): Boolean = try {
    query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
        it.moveToFirst() && it.getString(0) == documentId
    } == true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
