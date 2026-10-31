package com.vztechcore.vztune.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vztechcore.vztune.core.domain.settings.AppSettings
import com.vztechcore.vztune.core.domain.settings.AudioQuality
import com.vztechcore.vztune.core.domain.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vztune_settings")

/**
 * Persistent app settings backed by DataStore.
 * The only class that knows about DataStore keys; everyone else uses AppSettings.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs.toAppSettings() }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.dataStore.edit { it[AUDIO_QUALITY] = quality.name }
    }

    suspend fun setLastSearchTab(tab: String) {
        context.dataStore.edit { it[LAST_SEARCH_TAB] = tab }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        themeMode = this[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AMOLED,
        audioQuality = this[AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.HIGH,
        lastSearchTab = this[LAST_SEARCH_TAB] ?: "ALL",
    )

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val LAST_SEARCH_TAB = stringPreferencesKey("last_search_tab")
    }
}