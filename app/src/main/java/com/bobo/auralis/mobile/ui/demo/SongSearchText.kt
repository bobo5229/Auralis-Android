package com.bobo.auralis.mobile.ui.demo

/** Search-only folding; never used for metadata, display text or identity keys. */
internal object SongSearchText {
    private val simplifiedCharacters: Map<Int, String> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/opencc/TSCharacters.txt")) {
            "Missing bundled OpenCC search dictionary"
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
                val columns = line.split('\t', limit = 2)
                columns[0].codePointAt(0) to columns[1].substringBefore(' ')
            }
        }
    }

    fun normalize(value: String): String = buildString(value.length) {
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            val simplified = simplifiedCharacters[codePoint]
            if (simplified != null) append(simplified) else appendCodePoint(codePoint)
            offset += Character.charCount(codePoint)
        }
    }
}
