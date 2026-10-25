package com.bobo.auralis.mobile.ui.theme

enum class GlassStyle(val key: String, val label: String) {
    Liquid("liquid", "Liquid Glass"),
    Frosted("frosted", "磨砂玻璃");

    companion object {
        fun fromKey(key: String?): GlassStyle = entries.firstOrNull { it.key == key } ?: Liquid
    }
}
