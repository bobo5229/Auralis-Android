/*
 * Copyright (c) 2024 Auxio Project
 * This file is derived from Location.kt in Auxio
 * (https://github.com/OxygenCobalt/Auxio, commit c05cebc52fe2393bf3a988068e4060fd6dba408f)
 * and has been trimmed for Auralis Mobile. See THIRD_PARTY_NOTICES.md.
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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * A user-selected SAF library root.
 *
 * A location exists in two states: [Unopened] (the URI was received from the document picker but
 * the app does not have persisted access yet) and [Opened] (read access has been persisted and the
 * tree can be scanned).
 *
 * Adapted from `org.oxycblt.musikr.fs.Location` in Auxio/Musikr. Unlike Auxio, Auralis only
 * requests persisted read access, because v1 never writes to the user's audio files.
 */
sealed class SafLocation(val uri: Uri, val path: SafPath) {
    override fun equals(other: Any?) = other is SafLocation && uri == other.uri

    override fun hashCode() = 31 * uri.hashCode()

    override fun toString(): String = uri.toString()

    /**
     * A picked tree URI without persisted access.
     *
     * Call [open] with the result of `ActivityResultContracts.OpenDocumentTree()` to persist the
     * permission and obtain an [Opened] location. The equivalent Auxio flow is implemented in
     * `LocationsDialog.addDocumentTreeUriToDirs()` (Auxio commit
     * `c05cebc52fe2393bf3a988068e4060fd6dba408f`).
     */
    class Unopened private constructor(uri: Uri, path: SafPath) : SafLocation(uri, path) {
        /**
         * Persists read access to this tree and returns an [Opened] location, or null when the
         * system does not grant the permission. The caller should surface a recoverable error and
         * let the user re-select the directory instead of treating the library as deleted.
         */
        fun open(context: Context): Opened? {
            if (isUnopened(context, uri)) {
                try {
                    context.contentResolverSafe.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    return null
                }

                if (isUnopened(context, uri)) {
                    return null
                }
            }
            return Opened(uri, path)
        }

        private fun isUnopened(context: Context, uri: Uri) =
            context.contentResolverSafe.persistedUriPermissions.none {
                it.uri == uri && it.isReadPermission
            }

        companion object {
            /**
             * Wraps a URI received from `ActivityResultContracts.OpenDocumentTree()`.
             *
             * This never throws for a confusing provider URI: unknown providers fall back to an
             * opaque root identity so the URI is still persisted and can be re-selected later.
             */
            fun from(uri: Uri) = Unopened(uri, safPathFor(uri))
        }
    }

    /** A SAF tree with persisted read access that can be handed to `SafScanner`. */
    class Opened internal constructor(uri: Uri, path: SafPath) : SafLocation(uri, path) {
        override fun equals(other: Any?) = other is Opened && uri == other.uri && path == other.path

        override fun hashCode() = 31 * uri.hashCode() + path.hashCode()
    }

    companion object {
        /**
         * Restores a root that was persisted in a previous app session.
         *
         * This deliberately does not require the persisted URI permission to still exist: an
         * unavailable root must stay in the user's root list and be reported by the scan instead of
         * silently disappearing. `SafScanner` emits [SafScanEvent.DirectoryUnavailable] if access
         * was revoked.
         */
        fun restore(uri: Uri): Opened = Opened(uri, safPathFor(uri))

        /**
         * Converts the nullable result of `ActivityResultContracts.OpenDocumentTree()` into an
         * [Opened] location.
         *
         * Usage:
         * ```
         * val launcher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
         *     uri -> SafLocation.fromPickerResult(context, uri)?.let { roots::add }
         * }
         * launcher.launch(null)
         * ```
         */
        fun fromPickerResult(context: Context, uri: Uri?): Opened? =
            uri?.let { Unopened.from(it).open(context) }
    }
}

/**
 * Reconstructs the display/identity path of a tree URI without touching the provider.
 *
 * A URI that is not a valid tree or whose provider document ID is opaque falls back to an opaque
 * root identified by the URI string.
 */
private fun safPathFor(uri: Uri): SafPath {
    val parsed =
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                SafTreePathParser.parse(DocumentsContract.getTreeDocumentId(uri))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    return parsed ?: SafPath(SafRoot.Opaque(uri.toString()), SafComponents.root())
}

/**
 * Parses a SAF tree document ID such as `primary:Music/Albums` into a [SafPath].
 *
 * This intentionally replaces Auxio's `DocumentPathFactory`, which additionally resolved
 * MediaStore paths and StorageManager volumes. Auralis never uses MediaStore as the library source,
 * so only the document ID's volume prefix and relative path are needed.
 *
 * @return the parsed path, or null when [documentId] has no usable volume prefix and should be
 *   treated as an opaque provider root.
 */
internal object SafTreePathParser {
    fun parse(documentId: String?): SafPath? {
        if (documentId.isNullOrEmpty()) return null

        val split = documentId.split(':', limit = 2)
        if (split.size != 2 || split[0].isEmpty()) return null

        return SafPath(SafRoot.Volume(split[0]), SafComponents.parseUnix(split[1]))
    }
}
