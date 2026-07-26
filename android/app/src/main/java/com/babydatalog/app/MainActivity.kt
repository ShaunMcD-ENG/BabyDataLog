package com.babydatalog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.prefs.AppPreferences
import com.babydatalog.app.data.prefs.ThemeMode
import com.babydatalog.app.data.sync.SyncScheduler
import com.babydatalog.app.ui.navigation.NavGraph
import com.babydatalog.app.ui.theme.BabyDataLogTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appPreferences.themeMode.collectAsStateWithLifecycle()
            val customColorArgb by appPreferences.customPrimaryColorArgb.collectAsStateWithLifecycle()
            val customPrimaryColor = if (themeMode == ThemeMode.CUSTOM) Color(customColorArgb) else null

            BabyDataLogTheme(customPrimaryColor = customPrimaryColor) {
                NavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Sync whenever the app comes to the foreground so the phone picks up
        // the other device's records without waiting for the periodic worker.
        syncScheduler.requestSyncNow()
    }
}
