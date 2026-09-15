package com.bobo.auralis.mobile.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bobo.auralis.mobile.library.saf.SafLocation

/**
 * Temporary Technical Spike persistence for selected SAF roots.
 *
 * Auralis should eventually store roots in Room (see `ARCHITECTURE.md`), but Room is explicitly out
 * of scope for this spike, so a small SharedPreferences-backed list is used instead. The list is
 * ordered: it is also the future duplicate-candidate priority order.
 *
 * Deleting a root releases its persisted read permission when no other configured root uses the
 * same URI, so the app does not keep access to a directory the user removed.
 */
class SafRootStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Reads the persisted roots, preserving their addition order. */
    fun load(): List<SafLocation.Opened> =
        prefs
            .getString(KEY_ROOTS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { SafLocation.restore(Uri.parse(it)) }
            .distinctBy { it.uri }
            .toList()

    /**
     * Adds a root.
     *
     * @return false when a root with the same URI is already configured.
     */
    fun add(root: SafLocation.Opened): Boolean {
        val current = load()
        if (current.any { it.uri == root.uri }) {
            return false
        }

        save(current + root)
        return true
    }

    /**
     * Removes a root and releases its persisted read permission if it is no longer referenced by
     * any remaining root.
     *
     * @return false when the root was not configured.
     */
    fun remove(root: SafLocation.Opened): Boolean {
        val current = load()
        val remaining = current.filterNot { it.uri == root.uri }
        if (remaining.size == current.size) {
            return false
        }

        save(remaining)
        if (remaining.none { it.uri == root.uri }) {
            releasePersistedAccess(root.uri)
        }
        return true
    }

    private fun save(roots: List<SafLocation.Opened>) {
        prefs
            .edit()
            .putString(KEY_ROOTS, roots.joinToString("\n") { it.uri.toString() })
            .apply()
    }

    private fun releasePersistedAccess(uri: Uri) {
        val permission =
            appContext.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
                ?: return

        var flags = 0
        if (permission.isReadPermission) {
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        if (permission.isWritePermission) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (flags == 0) {
            return
        }

        try {
            appContext.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // The permission may already have been revoked by the system or the provider; there is
            // nothing useful to do for a debug spike beyond not crashing.
        }
    }

    private companion object {
        const val PREFS_NAME = "auralis_saf_spike"
        const val KEY_ROOTS = "roots"
    }
}
