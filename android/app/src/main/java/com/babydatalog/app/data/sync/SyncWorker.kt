package com.babydatalog.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!syncRepository.isConnected) return Result.success()
        return when (syncRepository.sync()) {
            is SyncResult.Success -> Result.success()
            // Cap retries: unbounded Result.retry() lets exponential backoff
            // climb to 5 hours and starves the periodic schedule. After two
            // attempts, give up and let the next periodic run try again.
            is SyncResult.Error ->
                if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
