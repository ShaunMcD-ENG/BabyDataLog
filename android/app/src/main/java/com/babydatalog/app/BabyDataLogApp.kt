package com.babydatalog.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babydatalog.app.data.sync.SyncPreferences
import com.babydatalog.app.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BabyDataLogApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var syncPrefs: SyncPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Re-assert the periodic schedule on every process start so auto-sync
        // works even if the user never revisits the Sync screen.
        if (syncPrefs.apiKey != null) syncScheduler.schedulePeriodic()
    }
}
