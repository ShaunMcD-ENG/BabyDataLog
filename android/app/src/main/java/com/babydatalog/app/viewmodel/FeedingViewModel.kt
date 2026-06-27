package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.LatchQuality
import com.babydatalog.app.data.repository.FeedingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class FeedingSortOrder {
    NEWEST_FIRST, OLDEST_FIRST, LONGEST_FIRST, SHORTEST_FIRST
}

data class FeedingFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long? = null,
    val durationMinutes: Float? = null,
    val breastSide: BreastSide = BreastSide.LEFT,
    val babyState: BabyState? = null,
    val latchQuality: LatchQuality? = null,
    val notes: String = "",
    val isTimerRunning: Boolean = false,
    val timerStartMs: Long? = null,
    val accumulatedSeconds: Long = 0L,
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FeedingViewModel @Inject constructor(
    private val feedingRepository: FeedingRepository
) : ViewModel() {

    // babyId is now injected via a StateFlow<Long?> passed from BabyViewModel
    // The NavGraph passes it in; we observe it to keep formState.babyId in sync
    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(FeedingSortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<FeedingSortOrder> = _sortOrder.asStateFlow()

    private val _formState = MutableStateFlow(FeedingFormState())
    val formState: StateFlow<FeedingFormState> = _formState.asStateFlow()

    val feedings: StateFlow<List<FeedingSession>> = combine(_activeBabyId, _sortOrder) { id, sort -> id to sort }
        .flatMapLatest { (id, sort) ->
            if (id != null) feedingRepository.getFeedingsForBaby(id).map { list ->
                when (sort) {
                    FeedingSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.startTimeMs }
                    FeedingSortOrder.OLDEST_FIRST -> list.sortedBy { it.startTimeMs }
                    FeedingSortOrder.LONGEST_FIRST -> list.sortedByDescending { it.durationMinutes ?: 0f }
                    FeedingSortOrder.SHORTEST_FIRST -> list.filter { it.durationMinutes != null }.sortedBy { it.durationMinutes }
                }
            }
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) {
            _activeBabyId.value = id
            _formState.update { it.copy(babyId = id) }
        }
    }

    fun setSortOrder(order: FeedingSortOrder) {
        _sortOrder.value = order
    }

    fun loadFeeding(id: Long) {
        viewModelScope.launch {
            val feeding = feedingRepository.getFeedingById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = feeding.id,
                    syncUuid = feeding.syncUuid,
                    babyId = feeding.babyId,
                    startTimeMs = feeding.startTimeMs,
                    endTimeMs = feeding.endTimeMs,
                    durationMinutes = feeding.durationMinutes,
                    breastSide = feeding.breastSide,
                    babyState = feeding.babyState,
                    latchQuality = feeding.latchQuality,
                    notes = feeding.notes ?: "",
                    isTimerRunning = false,
                    timerStartMs = null,
                    accumulatedSeconds = ((feeding.durationMinutes ?: 0f) * 60f).toLong(),
                    createdAtMs = feeding.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    // Fresh start — resets all accumulated time
    fun startTimer() {
        val now = System.currentTimeMillis()
        _formState.update {
            it.copy(
                timerStartMs = now,
                startTimeMs = now,
                isTimerRunning = true,
                endTimeMs = null,
                durationMinutes = null,
                accumulatedSeconds = 0L
            )
        }
    }

    // Pause — freezes the running timer, saves accumulated seconds, keeps duration visible
    fun pauseTimer() {
        val state = _formState.value
        val timerStart = state.timerStartMs ?: return
        val now = System.currentTimeMillis()
        val newAccumulated = state.accumulatedSeconds + (now - timerStart) / 1000L
        _formState.update {
            it.copy(
                endTimeMs = now,
                durationMinutes = newAccumulated / 60f,
                isTimerRunning = false,
                timerStartMs = null,
                accumulatedSeconds = newAccumulated
            )
        }
    }

    // Continue — resumes from wherever accumulated seconds left off
    fun continueTimer() {
        val now = System.currentTimeMillis()
        _formState.update {
            it.copy(
                timerStartMs = now,
                isTimerRunning = true
            )
        }
    }

    // Reset — clears all timer state back to zero
    fun resetTimer() {
        _formState.update {
            it.copy(
                isTimerRunning = false,
                timerStartMs = null,
                accumulatedSeconds = 0L,
                durationMinutes = null,
                endTimeMs = null
            )
        }
    }

    // Update accumulated seconds directly (from manual tab edits)
    fun setAccumulatedSeconds(secs: Long) {
        _formState.update {
            it.copy(
                accumulatedSeconds = secs,
                durationMinutes = if (secs > 0L) secs / 60f else null
            )
        }
    }

    fun updateBreastSide(side: BreastSide) {
        _formState.update { it.copy(breastSide = side) }
    }

    fun updateBabyState(state: BabyState?) {
        _formState.update { it.copy(babyState = state) }
    }

    fun updateLatchQuality(quality: LatchQuality?) {
        _formState.update { it.copy(latchQuality = quality) }
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

    fun updateDurationMinutes(min: Float?) {
        _formState.update { it.copy(durationMinutes = min) }
    }

    fun saveFeeding() {
        val state = _formState.value

        // Validate
        val endTime = state.endTimeMs
        if (endTime != null && endTime <= state.startTimeMs) {
            _formState.update {
                it.copy(error = "End time must be after start time")
            }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val feeding = FeedingSession(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    startTimeMs = state.startTimeMs,
                    endTimeMs = state.endTimeMs,
                    durationMinutes = state.durationMinutes,
                    breastSide = state.breastSide,
                    babyState = state.babyState,
                    latchQuality = state.latchQuality,
                    notes = state.notes.ifBlank { null },
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                feedingRepository.upsertFeeding(feeding)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save feeding")
                }
            }
        }
    }

    fun deleteFeeding(feeding: FeedingSession) {
        viewModelScope.launch {
            try {
                feedingRepository.deleteFeeding(feeding)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete feeding") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        _formState.value = FeedingFormState(babyId = babyId)
    }
}
