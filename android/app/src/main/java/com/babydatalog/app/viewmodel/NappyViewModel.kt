package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour
import com.babydatalog.app.data.repository.NappyRepository
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
import java.util.UUID
import javax.inject.Inject

enum class NappySortOrder { NEWEST_FIRST, OLDEST_FIRST, POO_FIRST, PEE_FIRST }

data class NappyFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val type: NappyType = NappyType.PEE,
    val amount: NappyAmount = NappyAmount.SMALL,
    val pooColour: PooColour? = null,
    val notes: String = "",
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NappyViewModel @Inject constructor(
    private val nappyRepository: NappyRepository
) : ViewModel() {

    // babyId is now injected via a StateFlow<Long?> passed from BabyViewModel
    // The NavGraph passes it in; we observe it to keep formState.babyId in sync
    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(NappySortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<NappySortOrder> = _sortOrder.asStateFlow()

    private val _formState = MutableStateFlow(NappyFormState())
    val formState: StateFlow<NappyFormState> = _formState.asStateFlow()

    val nappies: StateFlow<List<NappyChange>> = combine(_activeBabyId, _sortOrder) { id, sort -> id to sort }
        .flatMapLatest { (id, sort) ->
            if (id != null) nappyRepository.getNappiesForBaby(id).map { list ->
                when (sort) {
                    NappySortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestampMs }
                    NappySortOrder.OLDEST_FIRST -> list.sortedBy { it.timestampMs }
                    NappySortOrder.POO_FIRST -> list.sortedWith(
                        compareBy { nappy ->
                            when (nappy.type) {
                                NappyType.POO -> 0
                                NappyType.BOTH -> 1
                                NappyType.PEE -> 2
                            }
                        }
                    )
                    NappySortOrder.PEE_FIRST -> list.sortedWith(
                        compareBy { nappy ->
                            when (nappy.type) {
                                NappyType.PEE -> 0
                                NappyType.BOTH -> 1
                                NappyType.POO -> 2
                            }
                        }
                    )
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

    fun setSortOrder(order: NappySortOrder) {
        _sortOrder.value = order
    }

    fun loadNappy(id: Long) {
        viewModelScope.launch {
            val nappy = nappyRepository.getNappyById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = nappy.id,
                    syncUuid = nappy.syncUuid,
                    babyId = nappy.babyId,
                    timestampMs = nappy.timestampMs,
                    type = nappy.type,
                    amount = nappy.amount,
                    pooColour = nappy.pooColour,
                    notes = nappy.notes ?: "",
                    createdAtMs = nappy.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    fun updateType(type: NappyType) {
        _formState.update {
            it.copy(
                type = type,
                pooColour = if (type == NappyType.PEE) null else it.pooColour
            )
        }
    }

    fun updateAmount(amount: NappyAmount) {
        _formState.update { it.copy(amount = amount) }
    }

    fun updatePooColour(colour: PooColour?) {
        _formState.update { it.copy(pooColour = colour) }
    }

    fun updateNotes(notes: String) {
        _formState.update { it.copy(notes = notes) }
    }

    fun updateTimestamp(ms: Long) {
        _formState.update { it.copy(timestampMs = ms) }
    }

    fun saveNappy() {
        val state = _formState.value

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val nappy = NappyChange(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    timestampMs = state.timestampMs,
                    type = state.type,
                    amount = state.amount,
                    pooColour = state.pooColour,
                    notes = state.notes.ifBlank { null },
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                nappyRepository.upsertNappy(nappy)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save nappy change")
                }
            }
        }
    }

    fun deleteNappy(nappy: NappyChange) {
        viewModelScope.launch {
            try {
                nappyRepository.deleteNappy(nappy)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete nappy change") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        _formState.value = NappyFormState(babyId = babyId)
    }
}
