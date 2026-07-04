package com.babydatalog.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central place for enqueuing sync work.
 *
 * Two mechanisms keep devices in sync:
 *  - a 30-minute periodic worker as the safety net (survives reboots), and
 *  - a debounced one-shot worker fired when the app opens or a record is
 *    written, so changes reach the server within seconds of being made
 *    rather than waiting for the next periodic slot (which Doze can defer
 *    for hours).
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SyncPreferences
) {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            // UPDATE (not KEEP) so constraint/backoff changes in app updates take effect
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Requests a sync soon. Debounced: rapid consecutive writes share the one
     * pending request (KEEP) which runs [DEBOUNCE_SECONDS] after the first.
     * No-op when the device isn't paired with a server.
     */
    fun requestSyncSoon() {
        if (prefs.apiKey == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Immediate sync (no debounce), e.g. when the app comes to the foreground. */
    fun requestSyncNow() {
        if (prefs.apiKey == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_WORK_NAME)
        wm.cancelUniqueWork(ONESHOT_WORK_NAME)
    }

    companion object {
        const val PERIODIC_WORK_NAME = "babydatalog_auto_sync"
        const val ONESHOT_WORK_NAME = "babydatalog_sync_oneshot"
        private const val DEBOUNCE_SECONDS = 10L
    }
}
