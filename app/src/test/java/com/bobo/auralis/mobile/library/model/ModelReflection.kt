package com.bobo.auralis.mobile.library.model

import java.lang.reflect.Modifier

/**
 * Instance property names of a Kotlin class.
 *
 * Filters out everything the compiler adds rather than the author declaring:
 * static members such as a `Companion` object, the `$stable` marker the Compose
 * plugin emits, and any synthetic member.
 */
internal fun instanceFieldNames(type: Class<*>): Set<String> = type.declaredFields
    .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) || it.name.startsWith('$') }
    .map { it.name.lowercase() }
    .toSet()
