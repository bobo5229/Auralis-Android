package com.bobo.auralis.mobile.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class AuralisThemeMode(
    val key: String,
    val label: String,
) {
    System(key = "system", label = "跟随系统"),
    Light(key = "light", label = "浅色"),
    Dark(key = "dark", label = "深色");

    companion object {
        fun fromKey(key: String?): AuralisThemeMode =
            entries.firstOrNull { it.key == key } ?: System
    }
}

enum class AlbumLayoutMode(
    val key: String,
    val label: String,
    val gridColumns: Int?,
) {
    Grid1(key = "grid_1", label = "1 列", gridColumns = 1),
    Grid2(key = "grid_2", label = "2 列", gridColumns = 2),
    Grid3(key = "grid_3", label = "3 列", gridColumns = 3),
    Grid4(key = "grid_4", label = "4 列", gridColumns = 4),
    Grid5(key = "grid_5", label = "5 列", gridColumns = 5),
    List(key = "list", label = "列表", gridColumns = null),
    Coverless(key = "coverless", label = "无封面", gridColumns = null);

    val isGrid: Boolean
        get() = gridColumns != null

    fun shiftDensity(steps: Int): AlbumLayoutMode {
        val currentIndex = DensityOrder.indexOf(this)
        return DensityOrder[(currentIndex + steps).coerceIn(DensityOrder.indices)]
    }

    companion object {
        private val DensityOrder = listOf(Grid1, Grid2, Grid3, Grid4, Grid5, List, Coverless)

        fun fromKey(key: String?): AlbumLayoutMode =
            entries.firstOrNull { it.key == key } ?: Grid2
    }
}

enum class AlbumLayoutAnimationStyle(
    val key: String,
    val label: String,
) {
    Fade(key = "fade", label = "淡入淡出"),
    Reflow(key = "reflow", label = "连续重排");

    companion object {
        fun fromKey(key: String?): AlbumLayoutAnimationStyle =
            entries.firstOrNull { it.key == key } ?: Fade
    }
}

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

class AppearancePreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.appearanceDataStore

    val themeMode: Flow<AuralisThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AuralisThemeMode.fromKey(preferences[ThemeModeKey]) }

    val albumLayoutMode: Flow<AlbumLayoutMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AlbumLayoutMode.fromKey(preferences[AlbumLayoutModeKey]) }

    val albumLayoutAnimationStyle: Flow<AlbumLayoutAnimationStyle> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AlbumLayoutAnimationStyle.fromKey(preferences[AlbumLayoutAnimationStyleKey])
        }

    val glassStyle: Flow<GlassStyle> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> GlassStyle.fromKey(preferences[GlassStyleKey]) }

    suspend fun setGlassStyle(style: GlassStyle) {
        dataStore.edit { preferences -> preferences[GlassStyleKey] = style.key }
    }

    val liquidGlassBlurDp: Flow<Float> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> LiquidGlassBlur.normalize(preferences[LiquidGlassBlurDpKey] ?: LiquidGlassBlur.DefaultDp) }

    suspend fun setLiquidGlassBlurDp(dp: Float) {
        dataStore.edit { preferences -> preferences[LiquidGlassBlurDpKey] = LiquidGlassBlur.normalize(dp) }
    }

    suspend fun setThemeMode(mode: AuralisThemeMode) {
        dataStore.edit { preferences -> preferences[ThemeModeKey] = mode.key }
    }

    suspend fun setAlbumLayoutMode(mode: AlbumLayoutMode) {
        dataStore.edit { preferences -> preferences[AlbumLayoutModeKey] = mode.key }
    }

    suspend fun setAlbumLayoutAnimationStyle(style: AlbumLayoutAnimationStyle) {
        dataStore.edit { preferences -> preferences[AlbumLayoutAnimationStyleKey] = style.key }
    }

    private companion object {
        val GlassStyleKey = stringPreferencesKey("glass_style")
        val LiquidGlassBlurDpKey = floatPreferencesKey("liquid_glass_blur_dp")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val AlbumLayoutModeKey = stringPreferencesKey("album_layout_mode")
        val AlbumLayoutAnimationStyleKey = stringPreferencesKey("album_layout_animation_style")
    }
}
