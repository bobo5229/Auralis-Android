package com.bobo.auralis.mobile.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.playbackPreferencesDataStore by preferencesDataStore(name = "playback_preferences")

class PlaybackPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.playbackPreferencesDataStore

    val playbackMode: Flow<PlaybackMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> PlaybackMode.fromKey(preferences[PlaybackModeKey]) }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        dataStore.edit { preferences -> preferences[PlaybackModeKey] = mode.key }
    }

    private companion object {
        val PlaybackModeKey = stringPreferencesKey("playback_mode")
    }
}
