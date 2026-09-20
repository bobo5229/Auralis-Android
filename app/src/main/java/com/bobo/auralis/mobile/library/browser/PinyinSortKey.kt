package com.bobo.auralis.mobile.library.browser

import java.text.Normalizer
import java.util.Locale
import me.majiajie.tinypinyin.Pinyin

private val combiningMarks = Regex("\\p{M}+")
private val repeatedWhitespace = Regex("\\s+")

/** Produces a display-independent Latin key for mixed Chinese/Latin alphabetical sorting. */
internal fun pinyinAlphabeticalSortKey(value: String): String =
    Normalizer.normalize(Pinyin.toPinyin(value, ""), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .trim()
        .replace(repeatedWhitespace, " ")
        .lowercase(Locale.ROOT)
