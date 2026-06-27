package com.babydatalog.app.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.BuildConfig
import com.babydatalog.app.data.repository.BabyRepository
import com.babydatalog.app.data.repository.FeedingRepository
import com.babydatalog.app.data.repository.GrowthRepository
import com.babydatalog.app.data.repository.MilestoneRepository
import com.babydatalog.app.data.repository.NappyRepository
import com.babydatalog.app.utils.BackupException
import com.babydatalog.app.utils.ImportException
import com.babydatalog.app.utils.exportBackup
import com.babydatalog.app.utils.exportToCsv
import com.babydatalog.app.utils.exportToJson
import com.babydatalog.app.utils.importFromJson
import com.babydatalog.app.utils.parseBackup
import com.babydatalog.app.utils.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ExportImportState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val babyRepository: BabyRepository,
    private val feedingRepository: FeedingRepository,
    private val nappyRepository: NappyRepository,
    private val milestoneRepository: MilestoneRepository,
    private val growthRepository: GrowthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExportImportState())
    val state: StateFlow<ExportImportState> = _state.asStateFlow()

    fun exportJson(contentResolver: ContentResolver, uri: Uri) {
        _state.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }

        viewModelScope.launch {
            try {
                val allBabies = babyRepository.getAllBabies().first()
                val allFeedings = allBabies.flatMap { feedingRepository.getFeedingsForBaby(it.id).first() }
                val allNappies = allBabies.flatMap { nappyRepository.getNappiesForBaby(it.id).first() }
                val allMilestones = allBabies.flatMap { milestoneRepository.getMilestonesForBaby(it.id).first() }
                val allGrowth = allBabies.flatMap { growthRepository.getMeasurementsForBaby(it.id).first() }

                val jsonString = exportToJson(allBabies, allFeedings, allNappies, allMilestones, allGrowth)

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("Could not open output stream for URI")
                }

                _state.update {
                    it.copy(isLoading = false, successMessage = "Data exported successfully as JSON")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Export failed"
                    )
                }
            }
        }
    }

    fun exportCsv(contentResolver: ContentResolver, baseUri: Uri) {
        _state.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }

        viewModelScope.launch {
            try {
                val allBabies = babyRepository.getAllBabies().first()
                val allFeedings = allBabies.flatMap { feedingRepository.getFeedingsForBaby(it.id).first() }
                val allNappies = allBabies.flatMap { nappyRepository.getNappiesForBaby(it.id).first() }
                val allMilestones = allBabies.flatMap { milestoneRepository.getMilestonesForBaby(it.id).first() }
                val allGrowth = allBabies.flatMap { growthRepository.getMeasurementsForBaby(it.id).first() }

                val csvMap = exportToCsv(allBabies, allFeedings, allNappies, allMilestones, allGrowth)

                withContext(Dispatchers.IO) {
                    val combinedContent = csvMap.entries.joinToString(separator = "\n\n") { (name, content) ->
                        "=== $name ===\n$content"
                    }
                    contentResolver.openOutputStream(baseUri)?.use { outputStream ->
                        outputStream.write(combinedContent.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("Could not open output stream for URI")
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Data exported successfully as CSV (${csvMap.size} tables)"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "CSV export failed"
                    )
                }
            }
        }
    }

    fun importJson(contentResolver: ContentResolver, uri: Uri) {
        _state.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }

        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw Exception("Could not open input stream for URI")
                }

                val exportData = importFromJson(jsonString)

                var babiesImported = 0
                var feedingsImported = 0
                var nappiesImported = 0
                var milestonesImported = 0
                var growthImported = 0

                // Insert babies first and build old-ID → new-ID map so FK references stay valid
                val babyIdMap = mutableMapOf<Long, Long>()
                exportData.babies.forEach { babyExport ->
                    try {
                        val newId = babyRepository.insertBaby(babyExport.toEntity().copy(id = 0L))
                        babyIdMap[babyExport.id] = newId
                        babiesImported++
                    } catch (_: Exception) { }
                }

                exportData.feedingSessions.forEach { feedingExport ->
                    try {
                        val newBabyId = babyIdMap[feedingExport.babyId] ?: feedingExport.babyId
                        feedingRepository.upsertFeeding(
                            feedingExport.toEntity().copy(id = 0L, babyId = newBabyId)
                        )
                        feedingsImported++
                    } catch (_: Exception) { }
                }

                exportData.nappyChanges.forEach { nappyExport ->
                    try {
                        val newBabyId = babyIdMap[nappyExport.babyId] ?: nappyExport.babyId
                        nappyRepository.upsertNappy(
                            nappyExport.toEntity().copy(id = 0L, babyId = newBabyId)
                        )
                        nappiesImported++
                    } catch (_: Exception) { }
                }

                exportData.milestones.forEach { milestoneExport ->
                    try {
                        val newBabyId = babyIdMap[milestoneExport.babyId] ?: milestoneExport.babyId
                        milestoneRepository.upsertMilestone(
                            milestoneExport.toEntity().copy(id = 0L, babyId = newBabyId)
                        )
                        milestonesImported++
                    } catch (_: Exception) { }
                }

                exportData.growthMeasurements.forEach { growthExport ->
                    try {
                        val newBabyId = babyIdMap[growthExport.babyId] ?: growthExport.babyId
                        growthRepository.upsertMeasurement(
                            growthExport.toEntity().copy(id = 0L, babyId = newBabyId)
                        )
                        growthImported++
                    } catch (_: Exception) { }
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Import complete: $babiesImported babies, " +
                            "$feedingsImported feedings, $nappiesImported nappy changes, " +
                            "$milestonesImported milestones, $growthImported growth records"
                    )
                }
            } catch (e: ImportException) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Import error: ${e.message}")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Import failed")
                }
            }
        }
    }

    fun exportBackupCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            try {
                val allBabies = babyRepository.getAllBabies().first()
                val allFeedings = allBabies.flatMap { feedingRepository.getFeedingsForBaby(it.id).first() }
                val allNappies = allBabies.flatMap { nappyRepository.getNappiesForBaby(it.id).first() }
                val allMilestones = allBabies.flatMap { milestoneRepository.getMilestonesForBaby(it.id).first() }
                val allGrowth = allBabies.flatMap { growthRepository.getMeasurementsForBaby(it.id).first() }

                val csv = exportBackup(allBabies, allFeedings, allNappies, allMilestones, allGrowth, BuildConfig.VERSION_CODE)

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open output stream")
                }
                _state.update { it.copy(isLoading = false, successMessage = "Backup exported successfully") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun importBackupCsv(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                        ?: error("Could not open file")
                }

                val parsed = parseBackup(content)

                // Insert babies first and build old-ID → new-ID map so FK references stay valid
                val babyIdMap = mutableMapOf<Long, Long>()
                parsed.babies.forEach { baby ->
                    val oldId = baby.id
                    val newId = babyRepository.insertBaby(baby.copy(id = 0))
                    if (oldId != 0L) babyIdMap[oldId] = newId
                }

                parsed.feedings.forEach { feeding ->
                    val newBabyId = babyIdMap[feeding.babyId] ?: feeding.babyId
                    feedingRepository.upsertFeeding(feeding.copy(id = 0, babyId = newBabyId))
                }
                parsed.nappies.forEach { nappy ->
                    val newBabyId = babyIdMap[nappy.babyId] ?: nappy.babyId
                    nappyRepository.upsertNappy(nappy.copy(id = 0, babyId = newBabyId))
                }
                parsed.milestones.forEach { milestone ->
                    val newBabyId = babyIdMap[milestone.babyId] ?: milestone.babyId
                    milestoneRepository.upsertMilestone(milestone.copy(id = 0, babyId = newBabyId))
                }
                parsed.growthMeasurements.forEach { growth ->
                    val newBabyId = babyIdMap[growth.babyId] ?: growth.babyId
                    growthRepository.upsertMeasurement(growth.copy(id = 0, babyId = newBabyId))
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Backup restored: ${parsed.babies.size} babies, " +
                            "${parsed.feedings.size} feedings, " +
                            "${parsed.nappies.size} nappies, ${parsed.growthMeasurements.size} growth records, " +
                            "${parsed.milestones.size} milestones"
                    )
                }
            } catch (e: BackupException) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
