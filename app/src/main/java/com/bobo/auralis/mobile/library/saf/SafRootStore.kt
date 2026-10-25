package com.bobo.auralis.mobile.library.saf

import android.content.Context
import android.net.Uri

/** Legacy Technical Spike roots, read only during migration to Room. */
class SafRootStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    /** Called after Room commits; URI grants now belong to the Room roots. */
    fun clearMigratedRoots() {
        if (!prefs.contains(KEY_ROOTS)) return
        check(prefs.edit().remove(KEY_ROOTS).commit()) { "无法清理旧版曲库目录记录，请重试" }
    }

    private companion object {
        const val PREFS_NAME = "auralis_saf_spike"
        const val KEY_ROOTS = "roots"
    }
}
