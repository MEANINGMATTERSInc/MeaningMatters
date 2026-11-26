package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meaningmatters_studio_prefs"
)

data class StudioUiPrefs(
    val darkModeOverride: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val editorFontSizeSp: Int = 17,
    val focusModeDefault: Boolean = false,
    val autoSaveEnabled: Boolean = true
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val DARK_MODE = stringPreferencesKey("dark_mode_override")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size_sp")
        val FOCUS_MODE = booleanPreferencesKey("focus_mode_default")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_enabled")
    }

    val prefsFlow: Flow<StudioUiPrefs> = context.studioDataStore.data.map { prefs ->
        StudioUiPrefs(
            darkModeOverride = prefs[Keys.DARK_MODE] ?: "SYSTEM",
            editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 17).coerceIn(13, 28),
            focusModeDefault = prefs[Keys.FOCUS_MODE] ?: false,
            autoSaveEnabled = prefs[Keys.AUTO_SAVE] ?: true
        )
    }

    suspend fun setDarkModeOverride(mode: String) {
        context.studioDataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    suspend fun setEditorFontSize(sizeSp: Int) {
        context.studioDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(13, 28) }
    }

    suspend fun setFocusModeDefault(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.FOCUS_MODE] = enabled }
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }
}
