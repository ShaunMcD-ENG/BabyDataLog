package com.babydatalog.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.babydatalog.app.data.sync.PollResponse
import com.babydatalog.app.data.sync.SyncPreferences
import com.babydatalog.app.data.sync.SyncRepository
import com.babydatalog.app.data.sync.SyncResult
import com.babydatalog.app.data.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class SyncUiState {
    object NotConfigured : SyncUiState()
    data class Pending(
        val pairingCode: String,
        val deviceName: String,
        val serverUrl: String
    ) : SyncUiState()
    data class Connected(
        val deviceName: String,
        val serverUrl: String,
        val lastSyncMs: Long,
        val syncError: String? = null
    ) : SyncUiState()
    data class Syncing(
        val deviceName: String,
        val serverUrl: String,
        val lastSyncMs: Long
    ) : SyncUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : SyncUiState()
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repo: SyncRepository,
    private val prefs: SyncPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.NotConfigured)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        val serverUrl = prefs.serverUrl
        val deviceName = prefs.deviceName ?: ""
        val apiKey = prefs.apiKey
        val pairingCode = prefs.pairingCode

        when {
            serverUrl == null -> _uiState.value = SyncUiState.NotConfigured
            apiKey != null -> {
                _uiState.value = SyncUiState.Connected(deviceName, serverUrl, prefs.lastSyncMs)
                scheduleAutoSync()
            }
            pairingCode != null -> {
                _uiState.value = SyncUiState.Pending(pairingCode, deviceName, serverUrl)
                startPolling()
            }
            else -> _uiState.value = SyncUiState.NotConfigured
        }
    }

    fun connect(serverUrl: String, deviceName: String) {
        viewModelScope.launch {
            val result = repo.registerDevice(serverUrl.trimEnd('/'), deviceName.trim())
            when (result) {
                is SyncResult.Success -> {
                    val code = prefs.pairingCode ?: return@launch
                    _uiState.value = SyncUiState.Pending(code, deviceName.trim(), serverUrl.trimEnd('/'))
                    startPolling()
                }
                is SyncResult.Error -> _uiState.value = SyncUiState.Error(result.message)
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (_uiState.value is SyncUiState.Pending) {
                delay(4_000)
                val poll: PollResponse = repo.pollApproval() ?: break
                when (poll.status) {
                    "approved" -> {
                        _uiState.value = SyncUiState.Connected(
                            prefs.deviceName ?: "",
                            prefs.serverUrl ?: "",
                            prefs.lastSyncMs
                        )
                        scheduleAutoSync()
                        break
                    }
                    "rejected" -> {
                        repo.disconnect()
                        _uiState.value = SyncUiState.Error(
                            "This device was rejected by the server. You can try connecting again.",
                            canRetry = false
                        )
                        break
                    }
                }
            }
        }
    }

    fun syncNow() {
        val current = _uiState.value as? SyncUiState.Connected ?: return
        viewModelScope.launch {
            _uiState.value = SyncUiState.Syncing(current.deviceName, current.serverUrl, current.lastSyncMs)
            when (val result = repo.sync()) {
                is SyncResult.Success -> _uiState.value = SyncUiState.Connected(
                    current.deviceName, current.serverUrl, prefs.lastSyncMs
                )
                is SyncResult.Error -> _uiState.value = SyncUiState.Connected(
                    current.deviceName, current.serverUrl, current.lastSyncMs,
                    syncError = result.message
                )
            }
        }
    }

    fun disconnect() {
        cancelAutoSync()
        repo.disconnect()
        _uiState.value = SyncUiState.NotConfigured
    }

    fun dismissError() {
        _uiState.value = SyncUiState.NotConfigured
    }

    private fun scheduleAutoSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelAutoSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    companion object {
        private const val SYNC_WORK_NAME = "babydatalog_auto_sync"
    }
}
