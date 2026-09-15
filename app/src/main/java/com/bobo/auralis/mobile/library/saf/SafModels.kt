/*
 * Copyright (c) 2025 Auxio Project
 * This file is derived from FS.kt in Auxio
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

import android.net.Uri
import kotlinx.coroutines.Deferred

/** An entry discovered while walking a SAF document tree. */
sealed interface SafEntry {
    /** The content URI that can be used to open this entry. */
    val uri: Uri

    /** The path relative to the library root this entry was discovered under. */
    val path: SafPath
}

/**
 * A visited document directory.
 *
 * [parent] completes when the parent directory query finishes. It allows later pipeline stages to
 * resolve sibling files (for example a directory `cover` image or `.lrc` file) without re-walking
 * the tree.
 */
data class SafDirectory(
    override val uri: Uri,
    override val path: SafPath,
    val parent: Deferred<SafDirectory>?,
    val children: List<SafFile>,
) : SafEntry

/**
 * Stable identity of a SAF document during a scan.
 *
 * [documentId] is unique within one provider, and [provider] is the URI authority identifying that
 * provider. This is deliberately not the full document URI: the same physical document can be
 * reached through different tree roots (for example selecting `primary:Music` and
 * `primary:Music/Albums`) while the provider still reports the same document ID for it. Combining
 * provider + document ID makes de-duplication independent of which root discovered the file.
 */
data class SafDocumentKey(val provider: String, val documentId: String)

/**
 * A document file discovered during a scan.
 *
 * The scanner reports every file regardless of MIME type: audio filtering, metadata parsing and
 * artwork/lyrics sibling lookups belong to later library stages. [root] records which user-selected
 * tree produced this file so duplicate candidates across overlapping roots can be ordered by root
 * addition order.
 */
data class SafFile(
    override val uri: Uri,
    override val path: SafPath,
    val documentKey: SafDocumentKey,
    val mimeType: String,
    val size: Long,
    val modifiedMs: Long,
    val parent: Deferred<SafDirectory>?,
    val root: SafLocation.Opened,
) : SafEntry

/** A single observation produced by [SafScanner.scan]. */
sealed interface SafScanEvent {
    /** A file was found. */
    data class Found(val file: SafFile) : SafScanEvent

    /**
     * A directory could not be queried (permission revoked, provider error, I/O failure, ...).
     *
     * This is not fatal: the scan continues with the remaining roots and directories. The library
     * layer should keep previously known entries and record this state for diagnostics instead of
     * deleting the affected tracks.
     */
    data class DirectoryUnavailable(
        val root: SafLocation.Opened,
        val path: SafPath,
        val cause: Throwable,
    ) : SafScanEvent
}
