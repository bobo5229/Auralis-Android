package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.thirdparty.materialcolorutilities.hct.Hct
import com.bobo.auralis.mobile.thirdparty.materialcolorutilities.quantize.QuantizerCelebi
import com.bobo.auralis.mobile.thirdparty.materialcolorutilities.score.Score

internal fun extractArtworkSeedColor(pixels: IntArray): Int? {
    // Transparent padding must not influence the artwork's representative color.
    val opaquePixels = pixels.filter { (it ushr 24) == 255 }.toIntArray()
    if (opaquePixels.isEmpty()) return null
    val colors = QuantizerCelebi.quantize(opaquePixels, 128)
    val dominant = colors.maxByOrNull { it.value }?.key ?: return null
    // Score normally falls back to Google Blue. Keep the artwork's own neutral
    // color when no chromatic candidate qualifies instead.
    return Score.score(colors, 1, dominant, true).first()
}

internal fun adjustArtworkTrackColor(argb: Int, isDark: Boolean): Int {
    val source = Hct.fromInt(argb)
    val tone = if (isDark) source.tone.coerceIn(70.0, 80.0) else source.tone.coerceIn(35.0, 45.0)
    // Preserve neutral artwork and avoid amplifying muted artwork's chroma.
    val chroma = if (source.chroma < 5.0) 0.0 else source.chroma
    val adjusted = Hct.from(source.hue, chroma, tone).toInt()
    return (argb and 0xFF000000.toInt()) or (adjusted and 0x00FFFFFF)
}
