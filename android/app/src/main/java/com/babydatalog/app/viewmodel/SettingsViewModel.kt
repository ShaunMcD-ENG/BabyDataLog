package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import com.babydatalog.app.data.prefs.AppPreferences
import com.babydatalog.app.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val autoSaveIntervalMinutes: StateFlow<Int> = appPreferences.autoSaveIntervalMinutes
    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
    val customPrimaryColorArgb: StateFlow<Int> = appPreferences.customPrimaryColorArgb

    fun setAutoSaveIntervalMinutes(minutes: Int) = appPreferences.setAutoSaveIntervalMinutes(minutes)

    fun useSystemColor() = appPreferences.setThemeMode(ThemeMode.SYSTEM)

    fun useCustomColor(argb: Int) {
        appPreferences.setCustomPrimaryColorArgb(argb)
        appPreferences.setThemeMode(ThemeMode.CUSTOM)
    }
}
