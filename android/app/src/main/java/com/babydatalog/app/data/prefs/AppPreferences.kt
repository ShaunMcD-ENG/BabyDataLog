package com.babydatalog.app.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, CUSTOM }

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoSaveIntervalMinutes = MutableStateFlow(
        prefs.getInt(KEY_AUTOSAVE_MINUTES, DEFAULT_AUTOSAVE_MINUTES)
    )
    val autoSaveIntervalMinutes: StateFlow<Int> = _autoSaveIntervalMinutes.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _customPrimaryColorArgb = MutableStateFlow(
        prefs.getInt(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR)
    )
    val customPrimaryColorArgb: StateFlow<Int> = _customPrimaryColorArgb.asStateFlow()

    fun setAutoSaveIntervalMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_AUTOSAVE_MINUTES, minutes) }
        _autoSaveIntervalMinutes.value = minutes
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    fun setCustomPrimaryColorArgb(argb: Int) {
        prefs.edit { putInt(KEY_CUSTOM_COLOR, argb) }
        _customPrimaryColorArgb.value = argb
    }

    private fun loadThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(stored!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val PREFS_NAME = "babydatalog_app_prefs"
        private const val KEY_AUTOSAVE_MINUTES = "autosave_interval_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CUSTOM_COLOR = "custom_primary_color_argb"

        // 0 = auto-save disabled (default) — an explicit opt-in feature
        const val DEFAULT_AUTOSAVE_MINUTES = 0
        private const val DEFAULT_CUSTOM_COLOR = 0xFFB07A2E.toInt() // warm amber, close to the app's own seed color
    }
}
