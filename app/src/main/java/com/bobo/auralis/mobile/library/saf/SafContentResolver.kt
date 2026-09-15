/*
 * Copyright (c) 2022 Auxio Project
 * This file is derived from QueryUtil.kt in Auxio
 * (https://github.com/OxygenCobalt/Auxio, commit c05cebc52fe2393bf3a988068e4060fd6dba408f).
 * See THIRD_PARTY_NOTICES.md.
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
import android.database.Cursor
import android.net.Uri

/**
 * An application-scoped [ContentResolver].
 *
 * Mirrors Auxio's `contentResolverSafe`: using the application context's resolver avoids
 * device-specific wrapping/mangling that can happen with other resolver instances.
 */
internal val Context.contentResolverSafe: ContentResolver
    get() = applicationContext.contentResolver

/**
 * A shortcut for querying the [ContentResolver] database that fails loudly when the provider
 * returns no cursor instead of silently treating it as an empty result.
 *
 * @throws IllegalStateException if the [ContentResolver] did not return the queried [Cursor].
 * @see ContentResolver.query
 */
internal fun ContentResolver.safeQuery(
    uri: Uri,
    projection: Array<out String>,
    selector: String? = null,
    args: Array<String>? = null,
) = requireNotNull(query(uri, projection, selector, args, null)) { "ContentResolver query failed" }

/**
 * A shortcut for [safeQuery] with [use] applied, closing the [Cursor] when the block returns.
 *
 * @see safeQuery
 */
internal inline fun <reified R> ContentResolver.useQuery(
    uri: Uri,
    projection: Array<out String>,
    selector: String? = null,
    args: Array<String>? = null,
    block: (Cursor) -> R,
) = safeQuery(uri, projection, selector, args).use(block)
