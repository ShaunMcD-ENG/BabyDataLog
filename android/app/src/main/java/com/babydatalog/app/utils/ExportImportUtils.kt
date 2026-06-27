package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import com.babydatalog.app.data.database.entity.LatchQuality
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Export exception ─────────────────────────────────────────────────────────

class ImportException(message: String) : Exception(message)

// ─── Serializable wrapper classes ─────────────────────────────────────────────

@Serializable
data class BabyExport(
    val id: Long,
    val syncUuid: String,
    val name: String,
    val birthDateMs: Long,
    val birthWeightGrams: Int?,
    val createdAtMs: Long
)

@Serializable
data class FeedingExport(
    val id: Long,
    val syncUuid: String,
    val babyId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val durationMinutes: Float?,
    val breastSide: String,
    val babyState: String?,
    val latchQuality: String?,
    val notes: String?,
    val createdAtMs: Long
)

@Serializable
data class NappyExport(
    val id: Long,
    val syncUuid: String,
    val babyId: Long,
    val timestampMs: Long,
    val type: String,
    val amount: String,
    val pooColour: String?,
    val notes: String?,
    val createdAtMs: Long
)

@Serializable
data class MilestoneExport(
    val id: Long,
    val syncUuid: String,
    val babyId: Long,
    val timestampMs: Long,
    val title: String,
    val description: String?,
    val category: String,
    val photoUri: String?,
    val createdAtMs: Long
)

@Serializable
data class GrowthExport(
    val id: Long,
    val syncUuid: String,
    val babyId: Long,
    val timestampMs: Long,
    val weightGrams: Int?,
    val heightCm: Float?,
    val headCircumferenceCm: Float?,
    val footSizeMm: Int?,
    val handSizeMm: Int?,
    val legLengthCm: Float?,
    val armLengthCm: Float?,
    val backLengthCm: Float?,
    val notes: String?,
    val createdAtMs: Long
)

@Serializable
data class ExportData(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long,
    val babies: List<BabyExport>,
    val feedingSessions: List<FeedingExport>,
    val nappyChanges: List<NappyExport>,
    val milestones: List<MilestoneExport>,
    val growthMeasurements: List<GrowthExport> = emptyList()
)

// ─── Entity → Export extension functions ──────────────────────────────────────

fun Baby.toExport(): BabyExport = BabyExport(
    id = id,
    syncUuid = syncUuid,
    name = name,
    birthDateMs = birthDateMs,
    birthWeightGrams = birthWeightGrams,
    createdAtMs = createdAtMs
)

fun BabyExport.toEntity(): Baby = Baby(
    id = id,
    syncUuid = syncUuid,
    name = name,
    birthDateMs = birthDateMs,
    birthWeightGrams = birthWeightGrams,
    createdAtMs = createdAtMs
)

fun FeedingSession.toExport(): FeedingExport = FeedingExport(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    durationMinutes = durationMinutes,
    breastSide = breastSide.name,
    babyState = babyState?.name,
    latchQuality = latchQuality?.name,
    notes = notes,
    createdAtMs = createdAtMs
)

fun FeedingExport.toEntity(): FeedingSession = FeedingSession(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    durationMinutes = durationMinutes,
    breastSide = BreastSide.valueOf(breastSide),
    babyState = babyState?.let { BabyState.valueOf(it) },
    latchQuality = latchQuality?.let { LatchQuality.valueOf(it) },
    notes = notes,
    createdAtMs = createdAtMs
)

fun NappyChange.toExport(): NappyExport = NappyExport(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    type = type.name,
    amount = amount.name,
    pooColour = pooColour?.name,
    notes = notes,
    createdAtMs = createdAtMs
)

fun NappyExport.toEntity(): NappyChange = NappyChange(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    type = NappyType.valueOf(type),
    amount = NappyAmount.valueOf(amount),
    pooColour = pooColour?.let { PooColour.valueOf(it) },
    notes = notes,
    createdAtMs = createdAtMs
)

fun Milestone.toExport(): MilestoneExport = MilestoneExport(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    title = title,
    description = description,
    category = category.name,
    photoUri = photoUri,
    createdAtMs = createdAtMs
)

fun MilestoneExport.toEntity(): Milestone = Milestone(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    title = title,
    description = description,
    category = MilestoneCategory.valueOf(category),
    photoUri = photoUri,
    createdAtMs = createdAtMs
)

fun GrowthMeasurement.toExport(): GrowthExport = GrowthExport(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    weightGrams = weightGrams,
    heightCm = heightCm,
    headCircumferenceCm = headCircumferenceCm,
    footSizeMm = footSizeMm,
    handSizeMm = handSizeMm,
    legLengthCm = legLengthCm,
    armLengthCm = armLengthCm,
    backLengthCm = backLengthCm,
    notes = notes,
    createdAtMs = createdAtMs
)

fun GrowthExport.toEntity(): GrowthMeasurement = GrowthMeasurement(
    id = id,
    syncUuid = syncUuid,
    babyId = babyId,
    timestampMs = timestampMs,
    weightGrams = weightGrams,
    heightCm = heightCm,
    headCircumferenceCm = headCircumferenceCm,
    footSizeMm = footSizeMm,
    handSizeMm = handSizeMm,
    legLengthCm = legLengthCm,
    armLengthCm = armLengthCm,
    backLengthCm = backLengthCm,
    notes = notes,
    createdAtMs = createdAtMs
)

// ─── JSON serializer ───────────────────────────────────────────────────────────

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

// ─── ISO date formatter for CSV ────────────────────────────────────────────────

private val ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val ISO_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun Long.toIsoDate(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(ISO_DATE_FORMATTER)

private fun Long.toIsoTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(ISO_TIME_FORMATTER)

// ─── JSON export ───────────────────────────────────────────────────────────────

suspend fun exportToJson(
    babies: List<Baby>,
    feedings: List<FeedingSession>,
    nappies: List<NappyChange>,
    milestones: List<Milestone>,
    growth: List<GrowthMeasurement>
): String = withContext(Dispatchers.IO) {
    val exportData = ExportData(
        exportedAtMs = System.currentTimeMillis(),
        babies = babies.map { it.toExport() },
        feedingSessions = feedings.map { it.toExport() },
        nappyChanges = nappies.map { it.toExport() },
        milestones = milestones.map { it.toExport() },
        growthMeasurements = growth.map { it.toExport() }
    )
    json.encodeToString(exportData)
}

// ─── CSV export (human-readable, per-table files) ─────────────────────────────

private fun escapeCsv(value: String?): String {
    if (value == null) return ""
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}

suspend fun exportToCsv(
    babies: List<Baby>,
    feedings: List<FeedingSession>,
    nappies: List<NappyChange>,
    milestones: List<Milestone>,
    growth: List<GrowthMeasurement>
): Map<String, String> = withContext(Dispatchers.IO) {
    val result = mutableMapOf<String, String>()

    // babies.csv
    result["babies.csv"] = buildString {
        appendLine("id,name,birthDate,birthWeightGrams")
        babies.forEach { baby ->
            appendLine(
                "${baby.id}," +
                "${escapeCsv(baby.name)}," +
                "${baby.birthDateMs.toIsoDate()}," +
                "${baby.birthWeightGrams ?: ""}"
            )
        }
    }

    // feedings.csv
    result["feedings.csv"] = buildString {
        appendLine("id,babyId,date,time,breastSide,durationMinutes,babyState,latchQuality,notes")
        feedings.forEach { f ->
            appendLine(
                "${f.id}," +
                "${f.babyId}," +
                "${f.startTimeMs.toIsoDate()}," +
                "${f.startTimeMs.toIsoTime()}," +
                "${f.breastSide.name}," +
                "${f.durationMinutes ?: ""}," +
                "${f.babyState?.name ?: ""}," +
                "${f.latchQuality?.name ?: ""}," +
                escapeCsv(f.notes)
            )
        }
    }

    // nappies.csv
    result["nappies.csv"] = buildString {
        appendLine("id,babyId,date,time,type,amount,pooColour,notes")
        nappies.forEach { n ->
            appendLine(
                "${n.id}," +
                "${n.babyId}," +
                "${n.timestampMs.toIsoDate()}," +
                "${n.timestampMs.toIsoTime()}," +
                "${n.type.name}," +
                "${n.amount.name}," +
                "${n.pooColour?.name ?: ""}," +
                escapeCsv(n.notes)
            )
        }
    }

    // milestones.csv
    result["milestones.csv"] = buildString {
        appendLine("id,babyId,date,time,title,category,description")
        milestones.forEach { m ->
            appendLine(
                "${m.id}," +
                "${m.babyId}," +
                "${m.timestampMs.toIsoDate()}," +
                "${m.timestampMs.toIsoTime()}," +
                "${escapeCsv(m.title)}," +
                "${m.category.name}," +
                escapeCsv(m.description)
            )
        }
    }

    // growth.csv
    result["growth.csv"] = buildString {
        appendLine("id,babyId,date,time,weightGrams,heightCm,headCircumferenceCm,footSizeMm,handSizeMm,legLengthCm,armLengthCm,backLengthCm,notes")
        growth.forEach { g ->
            appendLine(
                "${g.id}," +
                "${g.babyId}," +
                "${g.timestampMs.toIsoDate()}," +
                "${g.timestampMs.toIsoTime()}," +
                "${g.weightGrams ?: ""}," +
                "${g.heightCm ?: ""}," +
                "${g.headCircumferenceCm ?: ""}," +
                "${g.footSizeMm ?: ""}," +
                "${g.handSizeMm ?: ""}," +
                "${g.legLengthCm ?: ""}," +
                "${g.armLengthCm ?: ""}," +
                "${g.backLengthCm ?: ""}," +
                escapeCsv(g.notes)
            )
        }
    }

    result
}

// ─── JSON import ───────────────────────────────────────────────────────────────

suspend fun importFromJson(jsonString: String): ExportData = withContext(Dispatchers.IO) {
    val data = try {
        json.decodeFromString<ExportData>(jsonString)
    } catch (e: Exception) {
        throw ImportException("Failed to parse JSON: ${e.message}")
    }

    if (data.schemaVersion != 1) {
        throw ImportException(
            "Unsupported schema version: ${data.schemaVersion}. Expected 1."
        )
    }

    data
}
