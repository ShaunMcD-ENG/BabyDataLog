package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.data.repository.MilestoneRepository
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

enum class MilestoneSortOrder { NEWEST_FIRST, OLDEST_FIRST, BY_CATEGORY }

data class MilestoneFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val title: String = "",
    val description: String = "",
    val category: MilestoneCategory = MilestoneCategory.DEVELOPMENT,
    val photoUri: String? = null,
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MilestoneViewModel @Inject constructor(
    private val milestoneRepository: MilestoneRepository
) : ViewModel() {

    // babyId is now injected via a StateFlow<Long?> passed from BabyViewModel
    // The NavGraph passes it in; we observe it to keep formState.babyId in sync
    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(MilestoneSortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<MilestoneSortOrder> = _sortOrder.asStateFlow()

    private val _formState = MutableStateFlow(MilestoneFormState())
    val formState: StateFlow<MilestoneFormState> = _formState.asStateFlow()

    val milestones: StateFlow<List<Milestone>> = combine(_activeBabyId, _sortOrder) { id, sort -> id to sort }
        .flatMapLatest { (id, sort) ->
            if (id != null) milestoneRepository.getMilestonesForBaby(id).map { list ->
                when (sort) {
                    MilestoneSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestampMs }
                    MilestoneSortOrder.OLDEST_FIRST -> list.sortedBy { it.timestampMs }
                    MilestoneSortOrder.BY_CATEGORY -> list.sortedWith(
                        compareBy<Milestone> { it.category.name }
                            .thenByDescending { it.timestampMs }
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

    fun setSortOrder(order: MilestoneSortOrder) {
        _sortOrder.value = order
    }

    fun loadMilestone(id: Long) {
        viewModelScope.launch {
            val milestone = milestoneRepository.getMilestoneById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = milestone.id,
                    syncUuid = milestone.syncUuid,
                    babyId = milestone.babyId,
                    timestampMs = milestone.timestampMs,
                    title = milestone.title,
                    description = milestone.description ?: "",
                    category = milestone.category,
                    photoUri = milestone.photoUri,
                    createdAtMs = milestone.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _formState.update { it.copy(title = title) }
    }

    fun updateDescription(desc: String) {
        _formState.update { it.copy(description = desc) }
    }

    fun updateCategory(cat: MilestoneCategory) {
        _formState.update { it.copy(category = cat) }
    }

    fun updatePhotoUri(uri: String?) {
        _formState.update { it.copy(photoUri = uri) }
    }

    fun updateTimestamp(ms: Long) {
        _formState.update { it.copy(timestampMs = ms) }
    }

    fun saveMilestone() {
        val state = _formState.value

        if (state.title.isBlank()) {
            _formState.update { it.copy(error = "Title cannot be blank") }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val milestone = Milestone(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    timestampMs = state.timestampMs,
                    title = state.title.trim(),
                    description = state.description.ifBlank { null },
                    category = state.category,
                    photoUri = state.photoUri,
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                milestoneRepository.upsertMilestone(milestone)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save milestone")
                }
            }
        }
    }

    fun deleteMilestone(milestone: Milestone) {
        viewModelScope.launch {
            try {
                milestoneRepository.deleteMilestone(milestone)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete milestone") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        _formState.value = MilestoneFormState(babyId = babyId)
    }
}
