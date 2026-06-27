package com.babydatalog.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import com.babydatalog.app.data.repository.GrowthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

enum class GrowthSortOrder { NEWEST_FIRST, OLDEST_FIRST, HEAVIEST_FIRST, LIGHTEST_FIRST }

enum class WeightUnit { GRAMS, KG }

data class GrowthFormState(
    val id: Long = 0L,
    val syncUuid: String = "",
    val babyId: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val weightGrams: Int? = null,       // canonical storage — always grams
    val weightUnit: WeightUnit = WeightUnit.GRAMS,
    val heightCm: Float? = null,
    val headCircumferenceCm: Float? = null,
    val footSizeMm: Int? = null,
    val handSizeMm: Int? = null,
    val legLengthCm: Float? = null,
    val armLengthCm: Float? = null,
    val backLengthCm: Float? = null,
    val notes: String = "",
    val createdAtMs: Long = 0L,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

private const val PREFS_NAME = "babydatalog_prefs"
private const val KEY_WEIGHT_UNIT = "weight_unit"

@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val growthRepository: GrowthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeBabyId = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(GrowthSortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<GrowthSortOrder> = _sortOrder.asStateFlow()

    private val _formState = MutableStateFlow(
        GrowthFormState(weightUnit = loadWeightUnitPref())
    )
    val formState: StateFlow<GrowthFormState> = _formState.asStateFlow()

    val measurements: StateFlow<List<GrowthMeasurement>> = combine(_activeBabyId, _sortOrder) { id, sort -> id to sort }
        .flatMapLatest { (id, sort) ->
            if (id != null) growthRepository.getMeasurementsForBaby(id).map { list ->
                when (sort) {
                    GrowthSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestampMs }
                    GrowthSortOrder.OLDEST_FIRST -> list.sortedBy { it.timestampMs }
                    GrowthSortOrder.HEAVIEST_FIRST -> list.sortedWith(
                        compareByDescending<GrowthMeasurement> { it.weightGrams ?: Int.MIN_VALUE }
                    )
                    GrowthSortOrder.LIGHTEST_FIRST -> list.sortedWith(
                        compareBy<GrowthMeasurement> { it.weightGrams ?: Int.MAX_VALUE }
                    )
                }
            }
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestMeasurement: StateFlow<GrowthMeasurement?> = _activeBabyId
        .flatMapLatest { id ->
            if (id != null) growthRepository.getLatestMeasurement(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveBabyId(id: Long) {
        if (_activeBabyId.value != id) {
            _activeBabyId.value = id
            _formState.update { it.copy(babyId = id) }
        }
    }

    fun setSortOrder(order: GrowthSortOrder) {
        _sortOrder.value = order
    }

    fun loadMeasurement(id: Long) {
        viewModelScope.launch {
            val measurement = growthRepository.getMeasurementById(id).first() ?: return@launch
            _formState.update {
                it.copy(
                    id = measurement.id,
                    syncUuid = measurement.syncUuid,
                    babyId = measurement.babyId,
                    timestampMs = measurement.timestampMs,
                    weightGrams = measurement.weightGrams,
                    // keep the user's current preferred unit when editing
                    heightCm = measurement.heightCm,
                    headCircumferenceCm = measurement.headCircumferenceCm,
                    footSizeMm = measurement.footSizeMm,
                    handSizeMm = measurement.handSizeMm,
                    legLengthCm = measurement.legLengthCm,
                    armLengthCm = measurement.armLengthCm,
                    backLengthCm = measurement.backLengthCm,
                    notes = measurement.notes ?: "",
                    createdAtMs = measurement.createdAtMs,
                    isSaving = false,
                    saveSuccess = false,
                    error = null
                )
            }
        }
    }

    fun updateWeightUnit(unit: WeightUnit) {
        prefs.edit().putString(KEY_WEIGHT_UNIT, unit.name).apply()
        _formState.update { it.copy(weightUnit = unit) }
    }

    fun updateTimestamp(ms: Long) {
        _formState.update { it.copy(timestampMs = ms) }
    }

    fun updateWeightGrams(g: Int?) {
        _formState.update { it.copy(weightGrams = g) }
    }

    fun updateHeightCm(cm: Float?) {
        _formState.update { it.copy(heightCm = cm) }
    }

    fun updateHeadCircumferenceCm(cm: Float?) {
        _formState.update { it.copy(headCircumferenceCm = cm) }
    }

    fun updateFootSizeMm(mm: Int?) { _formState.update { it.copy(footSizeMm = mm) } }
    fun updateHandSizeMm(mm: Int?) { _formState.update { it.copy(handSizeMm = mm) } }
    fun updateLegLengthCm(cm: Float?) { _formState.update { it.copy(legLengthCm = cm) } }
    fun updateArmLengthCm(cm: Float?) { _formState.update { it.copy(armLengthCm = cm) } }
    fun updateBackLengthCm(cm: Float?) { _formState.update { it.copy(backLengthCm = cm) } }

    fun updateNotes(s: String) {
        _formState.update { it.copy(notes = s) }
    }

    fun saveMeasurement() {
        val state = _formState.value

        if (state.weightGrams == null && state.heightCm == null &&
            state.footSizeMm == null && state.handSizeMm == null &&
            state.legLengthCm == null && state.armLengthCm == null && state.backLengthCm == null) {
            _formState.update { it.copy(error = "Please enter at least one measurement") }
            return
        }

        _formState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val measurement = GrowthMeasurement(
                    id = state.id,
                    syncUuid = state.syncUuid,
                    babyId = state.babyId,
                    timestampMs = state.timestampMs,
                    weightGrams = state.weightGrams,
                    heightCm = state.heightCm,
                    headCircumferenceCm = state.headCircumferenceCm,
                    footSizeMm = state.footSizeMm,
                    handSizeMm = state.handSizeMm,
                    legLengthCm = state.legLengthCm,
                    armLengthCm = state.armLengthCm,
                    backLengthCm = state.backLengthCm,
                    notes = state.notes.ifBlank { null },
                    createdAtMs = if (state.id == 0L) System.currentTimeMillis() else state.createdAtMs
                )
                growthRepository.upsertMeasurement(measurement)
                _formState.update { it.copy(isSaving = false, saveSuccess = true, error = null) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to save measurement")
                }
            }
        }
    }

    fun deleteMeasurement(m: GrowthMeasurement) {
        viewModelScope.launch {
            try {
                growthRepository.deleteMeasurement(m)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete measurement") }
            }
        }
    }

    fun resetForm() {
        val babyId = _activeBabyId.value ?: 0L
        _formState.value = GrowthFormState(babyId = babyId, weightUnit = loadWeightUnitPref())
    }

    private fun loadWeightUnitPref(): WeightUnit {
        val stored = prefs.getString(KEY_WEIGHT_UNIT, WeightUnit.GRAMS.name)
        return runCatching { WeightUnit.valueOf(stored!!) }.getOrDefault(WeightUnit.GRAMS)
    }
}
