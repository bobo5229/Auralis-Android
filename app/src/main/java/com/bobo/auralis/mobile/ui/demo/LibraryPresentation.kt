package com.bobo.auralis.mobile.ui.demo


internal fun shouldShowPinnedLibraryContentTitle(firstVisibleItemIndex: Int): Boolean =
    firstVisibleItemIndex > 0

internal fun formatSongArtistCredits(artists: List<String>): String = when (artists.size) {
    0 -> "未知艺术家"
    1 -> artists.single()
    else -> artists.dropLast(1).joinToString(", ") + " & " + artists.last()
}

internal fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0) return "—:—"
    val totalSeconds = durationMs / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
