package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.repository.BabyRepository
import com.babydatalog.app.utils.floorToDay
import com.babydatalog.app.utils.syncUuidFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BabyUiState(
    val babies: List<Baby> = emptyList(),
    val selectedBaby: Baby? = null,
    val isAddingBaby: Boolean = false,
    val isEditingBaby: Boolean = false,
    val editBaby: Baby? = null,
    val newBabyName: String = "",
    val newBabyBirthDateMs: Long? = null,
    val newBabyBirthWeightGrams: Int? = null,
    val error: String? = null
)

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val babyRepository: BabyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BabyUiState())
    val uiState: StateFlow<BabyUiState> = _uiState.asStateFlow()

    val selectedBabyId: StateFlow<Long?> = _uiState
        .map { it.selectedBaby?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            babyRepository.getAllBabies().collect { babies ->
                _uiState.update { current ->
                    val selectedStillExists = babies.any { it.id == current.selectedBaby?.id }
                    val newSelected = when {
                        current.selectedBaby != null && selectedStillExists -> {
                            // Refresh selected baby data from updated list
                            babies.find { it.id == current.selectedBaby.id } ?: current.selectedBaby
                        }
                        babies.isNotEmpty() -> babies.first()
                        else -> null
                    }
                    current.copy(babies = babies, selectedBaby = newSelected)
                }
            }
        }
    }

    fun selectBaby(baby: Baby) {
        _uiState.update { it.copy(selectedBaby = baby) }
    }

    fun startAddBaby() {
        _uiState.update {
            it.copy(
                isAddingBaby = true,
                isEditingBaby = false,
                editBaby = null,
                newBabyName = "",
                newBabyBirthDateMs = null,
                newBabyBirthWeightGrams = null,
                error = null
            )
        }
    }

    fun startEditBaby(baby: Baby) {
        _uiState.update {
            it.copy(
                isEditingBaby = true,
                isAddingBaby = false,
                editBaby = baby,
                newBabyName = baby.name,
                newBabyBirthDateMs = baby.birthDateMs,
                newBabyBirthWeightGrams = baby.birthWeightGrams,
                error = null
            )
        }
    }

    fun cancelDialog() {
        _uiState.update {
            it.copy(
                isAddingBaby = false,
                isEditingBaby = false,
                editBaby = null,
                newBabyName = "",
                newBabyBirthDateMs = null,
                newBabyBirthWeightGrams = null,
                error = null
            )
        }
    }

    fun updateNewBabyName(name: String) {
        _uiState.update { it.copy(newBabyName = name, error = null) }
    }

    fun updateNewBabyBirthDate(ms: Long?) {
        _uiState.update { it.copy(newBabyBirthDateMs = ms) }
    }

    fun updateNewBabyBirthWeight(grams: Int?) {
        _uiState.update { it.copy(newBabyBirthWeightGrams = grams) }
    }

    fun saveBaby() {
        val state = _uiState.value
        if (state.newBabyName.isBlank()) {
            _uiState.update { it.copy(error = "Baby name cannot be blank") }
            return
        }

        viewModelScope.launch {
            try {
                if (state.isEditingBaby && state.editBaby != null) {
                    val updated = state.editBaby.copy(
                        name = state.newBabyName.trim(),
                        birthDateMs = state.newBabyBirthDateMs ?: state.editBaby.birthDateMs,
                        birthWeightGrams = state.newBabyBirthWeightGrams
                    )
                    babyRepository.updateBaby(updated)
                    // Keep the updated baby selected
                    _uiState.update { it.copy(selectedBaby = updated) }
                } else {
                    val now = System.currentTimeMillis()
                    val birthDateMs = state.newBabyBirthDateMs ?: now
                    val newBaby = Baby(
                        syncUuid = syncUuidFor("b", state.newBabyName.trim().lowercase(), floorToDay(birthDateMs)),
                        name = state.newBabyName.trim(),
                        birthDateMs = birthDateMs,
                        birthWeightGrams = state.newBabyBirthWeightGrams,
                        createdAtMs = now
                    )
                    val newId = babyRepository.insertBaby(newBaby)
                    // Auto-select the newly added baby
                    _uiState.update { it.copy(selectedBaby = newBaby.copy(id = newId)) }
                }
                cancelDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save baby") }
            }
        }
    }

    fun deleteBaby(baby: Baby) {
        viewModelScope.launch {
            try {
                babyRepository.deleteBaby(baby)
                // If deleted baby was selected, auto-select the next available one
                _uiState.update { current ->
                    val newSelected = if (current.selectedBaby?.id == baby.id) {
                        current.babies.firstOrNull { it.id != baby.id }
                    } else {
                        current.selectedBaby
                    }
                    current.copy(selectedBaby = newSelected)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete baby") }
            }
        }
    }
}
