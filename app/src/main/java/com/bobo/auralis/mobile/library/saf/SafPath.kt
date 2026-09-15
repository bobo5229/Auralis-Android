/*
 * Copyright (c) 2024 Auxio Project
 * This file is derived from Path.kt in Auxio
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

/**
 * A path relative to a SAF library root.
 *
 * The path consists of a [SafRoot] (identifying the storage volume or opaque provider the tree
 * belongs to) and the [SafComponents] relative to that root. Keeping both parts means overlapping
 * tree selections (for example `primary:Music` and `primary:Music/Albums`) resolve the same
 * physical file to the same path, which scanning and de-duplication rely on.
 *
 * Adapted from `org.oxycblt.musikr.fs.Path` in Auxio/Musikr.
 */
data class SafPath(val root: SafRoot, val components: SafComponents) {
    /** The name of the file/directory, or null for the root path. */
    val name: String?
        get() = components.name

    /** The parent directory of the path, or itself if it is the root path. */
    val directory: SafPath
        get() = SafPath(root, components.parent())

    /** Appends a file/directory name to this path. */
    fun file(fileName: String) = SafPath(root, components.child(fileName))

    override fun toString() =
        if (components.unixString.isEmpty()) root.id else "${root.id}/${components.unixString}"
}

/**
 * The root part of a [SafPath].
 *
 * Auralis v1 only targets phone internal storage, so no StorageManager/MediaStore volume
 * resolution is performed: a tree whose document ID contains a prefix (`primary:Music`) is treated
 * as a volume-relative path, while anything else (for example provider-opaque document IDs) uses
 * the picked tree URI as an opaque root identity.
 */
sealed interface SafRoot {
    /** Stable identity of this root, used for equality and diagnostics. */
    val id: String

    /** A tree on a SAF storage volume, where [id] is the document ID prefix (`primary`, UUID, ...). */
    data class Volume(override val id: String) : SafRoot {
        override fun toString() = id
    }

    /** A tree from a provider whose document IDs are not storage-volume paths. */
    data class Opaque(override val id: String) : SafRoot {
        override fun toString() = id
    }
}

/**
 * The components of a path, separated by the unix file separator `/`.
 *
 * This allows paths to be manipulated without repeatedly parsing separator characters.
 * Adapted from `org.oxycblt.musikr.fs.Components` in Auxio/Musikr.
 */
@JvmInline
value class SafComponents private constructor(val components: List<String>) {
    /** The name of the file/directory, or null for the root path. */
    val name: String?
        get() = components.lastOrNull()

    /** Formats these components using the unix file separator (`/`). */
    val unixString: String
        get() = components.joinToString("/")

    override fun toString() = unixString

    /** Returns a new instance with the last component removed. */
    fun parent() = SafComponents(components.dropLast(1))

    /**
     * Returns a new instance with the given name appended as a child component. Empty names are
     * ignored, matching the behaviour of the original implementation.
     */
    fun child(name: String) =
        if (name.isNotEmpty()) {
            SafComponents(components + name.trimSlashes())
        } else {
            this
        }

    /** Removes the first [n] components, effectively producing a path `n` levels deep. */
    fun depth(n: Int) = SafComponents(components.drop(n))

    /** Concatenates this instance with another. */
    fun child(other: SafComponents) = SafComponents(components + other.components)

    /**
     * Returns true if [other] starts with this instance, i.e. is contained in this path's
     * directory tree.
     */
    fun contains(other: SafComponents): Boolean {
        if (other.components.size < components.size) {
            return false
        }

        return components == other.components.take(components.size)
    }

    /** Returns the part of [other] that is below this instance. */
    fun containing(other: SafComponents) = SafComponents(other.components.drop(components.size))

    companion object {
        /** Parses a unix-style path string into components. Empty components are discarded. */
        fun parseUnix(path: String) =
            SafComponents(path.trimSlashes().split('/').filter { it.isNotEmpty() })

        /** The root (empty) components. */
        fun root() = SafComponents(emptyList())

        private fun String.trimSlashes() = trimStart('/').trimEnd('/')
    }
}
