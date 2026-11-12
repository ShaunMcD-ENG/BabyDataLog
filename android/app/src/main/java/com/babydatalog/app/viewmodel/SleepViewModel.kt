package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.data.repository.SleepRepository
import com.babydatalog.app.utils.CurrentSleepStatus
import com.babydatalog.app.utils.SleepAwakeSummary
import com.babydatalog.app.utils.WakeWindowStats
import com.babydatalog.app.utils.computeWakeWindows
import com.babydatalog.app.utils.minuteTicker
import com.babydatalog.app.utils.computeLast24HourSleepAwake
import com.babydatalog.app.utils.currentSleepStatus
import com.babydatalog.app.utils.defaultSleepTypeForStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long? = null,
    val stillAsleep: Boolean = false,
    val type: SleepType = SleepType.NAP,
    val notes: String = "",
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleepRepository: SleepRepository
) : ViewModel() {

    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _formState = MutableStateFlow(SleepFormState())
    val formState: StateFlow<SleepFormState> = _formState.asStateFlow()

    val sleepSessions: StateFlow<List<SleepSession>> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) sleepRepository.getSleepSessionsForBaby(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Driven by the most recently started session only (see currentSleepStatus), so an
    // older unfinished or mis-dated entry can't hijack the live asleep/awake timer.
    val sleepStatus: StateFlow<CurrentSleepStatus> = sleepSessions
        .map { currentSleepStatus(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentSleepStatus(null, null))

    // Recomputed every minute while on screen, so the trailing-24h totals and the ongoing
    // wake window keep moving even when no new entry is logged.
    val last24HourSummary: StateFlow<SleepAwakeSummary> = combine(sleepSessions, minuteTicker()) { sessions, now ->
        computeLast24HourSleepAwake(sessions, now)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeLast24HourSleepAwake(emptyList()))

    // Wake windows that ended in the last 24h (plus the ongoing one), split Day vs Night.
    val last24HourWakeWindows: StateFlow<WakeWindowStats> = combine(sleepSessions, minuteTicker()) { sessions, now ->
        computeWakeWindows(sessions, now - 86_400_000L, now)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeWakeWindows(emptyList(), 0L, 0L))

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) {
            _activeBabyId.value = id
            _formState.update { it.copy(babyId = id) }
        }
    }

    fun loadSleep(id: Long) {
        viewModelScope.launch {
            val session = sleepRepository.getSleepById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = session.id,
                    syncUuid = session.syncUuid,
                    babyId = session.babyId,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = session.endTimeMs,
                    stillAsleep = session.endTimeMs == null,
                    type = session.type,
                    notes = session.notes ?: "",
                    createdAtMs = session.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    // Primary contextual action — "Went to sleep" (spec §4.1). Persists immediately.
    fun quickStart() {
        val babyId = _activeBabyId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sleepRepository.startSleep(babyId, defaultSleepTypeForStart(now), now)
        }
    }

    // Primary contextual action — "Woke up" (spec §4.1). Callable regardless of
    // which device started the open session.
    fun quickEnd() {
        val open = sleepStatus.value.openSession ?: return
        viewModelScope.launch { sleepRepository.endSleep(open) }
    }

    fun updateType(type: SleepType) {
        _formState.update { it.copy(type = type) }
    }

    fun updateNotes(notes: String) {
        _formState.update { it.copy(notes = notes) }
    }

    fun updateStartTime(ms: Long) {
        _formState.update { it.copy(startTimeMs = ms) }
    }

    fun updateEndTime(ms: Long?) {
        _formState.update { it.copy(endTimeMs = ms) }
    }

    fun updateStillAsleep(stillAsleep: Boolean) {
        _formState.update { it.copy(stillAsleep = stillAsleep, endTimeMs = if (stillAsleep) null else it.endTimeMs) }
    }

    fun saveSleep() {
        val state = _formState.value
        val endTime = if (state.stillAsleep) null else state.endTimeMs

        if (endTime != null && endTime <= state.startTimeMs) {
            _formState.update { it.copy(error = "End time must be after start time") }
            return
        }

        // A future time is almost always a wrong date picked in the picker, and would
        // corrupt the live awake/asleep timers and 24h totals. Allow a minute of slack
        // for clock drift between the two parents' phones.
        val latestAllowedMs = System.currentTimeMillis() + 60_000L
        if (state.startTimeMs > latestAllowedMs || (endTime != null && endTime > latestAllowedMs)) {
            _formState.update { it.copy(error = "Start and end times can't be in the future — check the date") }
            return
        }

        if (!state.stillAsleep && state.endTimeMs == null) {
            _formState.update { it.copy(error = "Please set an end time, or check \"Still asleep\"") }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val session = SleepSession(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    startTimeMs = state.startTimeMs,
                    endTimeMs = endTime,
                    type = state.type,
                    notes = state.notes.ifBlank { null },
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                sleepRepository.upsertSleep(session)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save sleep session") }
            }
        }
    }

    fun deleteSleep(session: SleepSession) {
        viewModelScope.launch {
            try {
                sleepRepository.deleteSleep(session)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete sleep session") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        val now = System.currentTimeMillis()
        _formState.value = SleepFormState(babyId = babyId, type = defaultSleepTypeForStart(now))
    }
}
