package com.babydatalog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.babydatalog.app.data.sync.SyncScheduler
import com.babydatalog.app.ui.navigation.NavGraph
import com.babydatalog.app.ui.theme.BabyDataLogTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabyDataLogTheme {
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
