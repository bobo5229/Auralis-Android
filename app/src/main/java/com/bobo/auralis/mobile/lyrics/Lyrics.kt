package com.bobo.auralis.mobile.lyrics

internal data class LyricLine(val timeMs: Long, val text: String)
internal data class Lyrics(val plainText: String, val lines: List<LyricLine>) {
    fun activeIndex(positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle - 1
        }
        return high
    }
}

private val timestamp = Regex("\\[(\\d{1,6}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
private val offsetTag = Regex("\\[offset:([+-]?\\d{1,9})]", RegexOption.IGNORE_CASE)
private val metadataTag = Regex("^\\[(?:ar|al|ti|au|by|re|ve|length|offset):.*]$", RegexOption.IGNORE_CASE)

internal fun parseLyrics(text: String): Lyrics? {
    val clean = text.removePrefix("\uFEFF").trim()
    if (clean.isBlank()) return null
    val offset = offsetTag.findAll(clean).lastOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val timed = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()
    clean.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (metadataTag.matches(line)) return@forEach
        val stamps = timestamp.findAll(line).toList()
        if (stamps.isEmpty()) {
            plain.add(line)
        } else {
            val words = timestamp.replace(line, "").trim()
            plain.add(words)
            stamps.forEach { stamp ->
                val fraction = stamp.groupValues[3].padEnd(3, '0').toLong()
                val time = stamp.groupValues[1].toLong() * 60_000 + stamp.groupValues[2].toLong() * 1000 + fraction
                timed.add(LyricLine((time + offset).coerceAtLeast(0), words))
            }
        }
    }
    val lines = timed.sortedBy { it.timeMs }.groupBy { it.timeMs }.map { (time, group) ->
        LyricLine(time, group.map { it.text }.distinct().joinToString("\n"))
    }
    if (plain.all { it.isBlank() }) return null
    return Lyrics(plain.joinToString("\n").trim(), lines)
}
