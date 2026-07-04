package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.sync.DeferredRecord
import com.babydatalog.app.data.sync.PollResponse
import com.babydatalog.app.data.sync.SyncPreferences
import com.babydatalog.app.data.sync.SyncRepository
import com.babydatalog.app.data.sync.SyncResult
import com.babydatalog.app.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BabyOption(val id: Long, val name: String)

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
        val syncError: String? = null,
        val deferred: List<DeferredRecord> = emptyList(),
        val babies: List<BabyOption> = emptyList()
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
    private val scheduler: SyncScheduler
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
                setConnected()
                scheduleAutoSync()
            }
            pairingCode != null -> {
                _uiState.value = SyncUiState.Pending(pairingCode, deviceName, serverUrl)
                startPolling()
            }
            else -> _uiState.value = SyncUiState.NotConfigured
        }
    }

    /** Rebuilds the Connected state including the deferred list and baby options. */
    private fun setConnected(syncError: String? = null) {
        viewModelScope.launch {
            val babies = repo.localBabies().map { BabyOption(it.id, it.name) }
            _uiState.value = SyncUiState.Connected(
                deviceName = prefs.deviceName ?: "",
                serverUrl = prefs.serverUrl ?: "",
                lastSyncMs = prefs.lastSyncMs,
                syncError = syncError,
                deferred = repo.deferredRecords(),
                babies = babies
            )
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
                        setConnected()
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
                is SyncResult.Success -> setConnected()
                is SyncResult.Error -> setConnected(syncError = result.message)
            }
        }
    }

    fun wipeAndResync() {
        val current = _uiState.value as? SyncUiState.Connected ?: return
        viewModelScope.launch {
            _uiState.value = SyncUiState.Syncing(current.deviceName, current.serverUrl, current.lastSyncMs)
            when (val result = repo.wipeAndResync()) {
                is SyncResult.Success -> setConnected()
                is SyncResult.Error -> setConnected(syncError = result.message)
            }
        }
    }

    /** Attaches a deferred record to the chosen baby, then re-syncs to propagate the fix. */
    fun assignDeferredToBaby(record: DeferredRecord, babyId: Long) {
        viewModelScope.launch {
            when (val result = repo.assignDeferredToBaby(record, babyId)) {
                is SyncResult.Success -> syncNow()
                is SyncResult.Error -> setConnected(syncError = result.message)
            }
        }
    }

    fun dismissDeferred(record: DeferredRecord) {
        repo.dismissDeferred(record)
        setConnected()
    }

    fun disconnect() {
        cancelAutoSync()
        repo.disconnect()
        _uiState.value = SyncUiState.NotConfigured
    }

    fun dismissError() {
        _uiState.value = SyncUiState.NotConfigured
    }

    private fun scheduleAutoSync() = scheduler.schedulePeriodic()

    private fun cancelAutoSync() = scheduler.cancelAll()
}
