/*
 * Copyright (c) 2025 Auxio Project
 * This file is derived from SAF.kt in Auxio
 * (https://github.com/OxygenCobalt/Auxio, commit c05cebc52fe2393bf3a988068e4060fd6dba408f)
 * and has been trimmed/adapted for Auralis Mobile. See THIRD_PARTY_NOTICES.md.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.bobo.auralis.mobile.library.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * Recursively scans one or more SAF document trees selected through
 * `ActivityResultContracts.OpenDocumentTree()`.
 *
 * The traversal algorithm is derived from Auxio/Musikr's SAF implementation
 * (`musikr/src/main/java/org/oxycblt/musikr/fs/saf/SAF.kt`, Auxio commit
 * `c05cebc52fe2393bf3a988068e4060fd6dba408f`), with the following Auralis adaptations:
 *
 * - results are emitted as a cold [Flow] of [SafScanEvent] instead of being written to a channel;
 * - a directory that cannot be queried emits [SafScanEvent.DirectoryUnavailable] and does not abort
 *   the rest of the scan, because Auralis must keep temporarily unavailable directories and their
 *   tracks recoverable;
 * - the MediaStore "date added" lookup, content-observer based `track()` support, user exclude
 *   directories and the Musikr `FS` abstraction were removed.
 *
 * Auralis v1 updates the library with a manual rescan only, so there is intentionally no
 * continuous observation in this scanner.
 */
class SafScanner
private constructor(
    private val contentResolver: ContentResolver,
    private val query: SafQuery,
) {
    /**
     * Lazily walks [SafQuery.source] and emits one [SafScanEvent] per discovered file or
     * unreachable directory. Collecting the flow performs the scan; cancelling the collection
     * cancels the scan.
     *
     * Files are only buffered by the flow's channel, so consumers should collect promptly. The
     * flow completes when every queued directory has been visited.
     */
    fun scan(): Flow<SafScanEvent> = channelFlow {
        val tasks =
            query.source.map { location ->
                exploreDirectory(
                    root = location,
                    rootUri = location.uri,
                    parentDocumentId = null,
                    relativePath = location.path,
                    parent = null,
                )
            }
        tasks.awaitAll()
    }

    private fun ProducerScope<SafScanEvent>.exploreDirectory(
        root: SafLocation.Opened,
        rootUri: Uri,
        parentDocumentId: String?,
        relativePath: SafPath,
        parent: Deferred<SafDirectory>?,
    ): Deferred<Unit> = async(Dispatchers.IO) {
        // Emitted files capture this deferred, so complete it even when the query fails partway
        // through instead of leaving consumers waiting forever on a broken directory.
        val children = mutableListOf<SafFile>()
        val directoryDeferred = CompletableDeferred<SafDirectory>()
        var directoryUri = rootUri
        var recursive: MutableList<Deferred<Unit>>? = null

        try {
            // The document ID of a root is not available from the tree URI alone in all
            // providers, so resolve it lazily here. Children already carry their document ID.
            val treeDocumentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(rootUri)
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, treeDocumentId)
            directoryUri = uri

            if (query.multithread) {
                recursive = mutableListOf()
            }

            contentResolver.useQuery(uri, PROJECTION) { cursor ->
                val childUriIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val displayNameIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val lastModifiedIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(childUriIndex)
                    val displayName = cursor.getString(displayNameIndex)

                    // Hidden files/directories are skipped by default, matching Auxio's scanner.
                    if (!query.withHidden && displayName.startsWith(".")) {
                        continue
                    }

                    val newPath = relativePath.file(displayName)
                    val mimeType = cursor.getString(mimeTypeIndex)
                    val lastModified = cursor.getLong(lastModifiedIndex)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, childId)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subtask =
                            exploreDirectory(
                                root = root,
                                rootUri = rootUri,
                                parentDocumentId = childId,
                                relativePath = newPath,
                                parent = directoryDeferred,
                            )
                        if (recursive != null) {
                            recursive.add(subtask)
                        } else {
                            // Parallel traversal is disabled: visit depth-first, one directory at
                            // a time.
                            subtask.await()
                        }
                    } else {
                        val size = cursor.getLong(sizeIndex)
                        val file =
                            SafFile(
                                uri = childUri,
                                path = newPath,
                                documentKey =
                                    SafDocumentKey(
                                        provider = rootUri.authority ?: rootUri.toString(),
                                        documentId = childId,
                                    ),
                                mimeType = mimeType,
                                size = size,
                                modifiedMs = lastModified,
                                parent = directoryDeferred,
                                root = root,
                            )
                        children.add(file)
                        send(SafScanEvent.Found(file))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed directory is reportable state in Auralis, not a fatal scan error. Keep
            // walking the other roots/directories so a single unavailable folder cannot hide the
            // rest of the library.
            send(SafScanEvent.DirectoryUnavailable(root, relativePath, e))
        } finally {
            directoryDeferred.complete(SafDirectory(directoryUri, relativePath, parent, children))
        }

        recursive?.awaitAll()
    }

    companion object {
        /** Creates a scanner bound to the application context's content resolver. */
        fun from(context: Context, query: SafQuery): SafScanner {
            val appContext = context.applicationContext
            return SafScanner(appContext.contentResolver, query)
        }

        private val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
    }
}

/**
 * Configuration for [SafScanner].
 *
 * @param source opened library roots, in user addition order. The scanner preserves [SafFile.root]
 *   so callers can implement stable duplicate-candidate priority.
 * @param withHidden whether hidden files/directories (names starting with `.`) are scanned.
 * @param multithread whether sibling directories can be queried in parallel.
 */
data class SafQuery(
    val source: List<SafLocation.Opened>,
    val withHidden: Boolean = false,
    val multithread: Boolean = true,
)
