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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

// ─── Constants ────────────────────────────────────────────────────────────────

const val BACKUP_FORMAT_VERSION = 1

// ─── Data classes ─────────────────────────────────────────────────────────────

data class BackupHeader(
    val backupFormatVersion: Int,
    val appVersion: Int,
    val exportedAt: String
)

data class ParsedBackup(
    val header: BackupHeader,
    val babies: List<Baby>,
    val feedings: List<FeedingSession>,
    val nappies: List<NappyChange>,
    val milestones: List<Milestone>,
    val growthMeasurements: List<GrowthMeasurement>
)

class BackupException(message: String) : Exception(message)

// ─── ISO-8601 UTC formatter ───────────────────────────────────────────────────

private val ISO_UTC_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

private fun nowIso8601Utc(): String = ISO_UTC_FORMATTER.format(Instant.now())

// ─── CSV escape (same logic as ExportImportUtils) ────────────────────────────

private fun escapeCsv(value: String?): String {
    if (value == null) return ""
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}

// ─── Export ───────────────────────────────────────────────────────────────────

suspend fun exportBackup(
    babies: List<Baby>,
    feedings: List<FeedingSession>,
    nappies: List<NappyChange>,
    milestones: List<Milestone>,
    growth: List<GrowthMeasurement>,
    appVersion: Int
): String = withContext(Dispatchers.IO) {
    buildString {
        // Header
        appendLine("BABYDATALOG_BACKUP_VERSION=$BACKUP_FORMAT_VERSION")
        appendLine("APP_VERSION=$appVersion")
        appendLine("EXPORTED_AT=${nowIso8601Utc()}")

        // [BABIES]
        appendLine("[BABIES]")
        appendLine("id,syncUuid,name,birthDateMs,birthWeightGrams,createdAtMs")
        babies.forEach { b ->
            appendLine(
                "${b.id}," +
                "${escapeCsv(b.syncUuid)}," +
                "${escapeCsv(b.name)}," +
                "${b.birthDateMs}," +
                "${b.birthWeightGrams ?: ""}," +
                "${b.createdAtMs}"
            )
        }

        // [FEEDINGS]
        appendLine("[FEEDINGS]")
        appendLine("id,syncUuid,babyId,startTimeMs,endTimeMs,durationMinutes,breastSide,babyState,latchQuality,notes,createdAtMs")
        feedings.forEach { f ->
            appendLine(
                "${f.id}," +
                "${escapeCsv(f.syncUuid)}," +
                "${f.babyId}," +
                "${f.startTimeMs}," +
                "${f.endTimeMs ?: ""}," +
                "${f.durationMinutes ?: ""}," +
                "${f.breastSide.name}," +
                "${f.babyState?.name ?: ""}," +
                "${f.latchQuality?.name ?: ""}," +
                "${escapeCsv(f.notes)}," +
                "${f.createdAtMs}"
            )
        }

        // [NAPPIES]
        appendLine("[NAPPIES]")
        appendLine("id,syncUuid,babyId,timestampMs,type,amount,pooColour,notes,createdAtMs")
        nappies.forEach { n ->
            appendLine(
                "${n.id}," +
                "${escapeCsv(n.syncUuid)}," +
                "${n.babyId}," +
                "${n.timestampMs}," +
                "${n.type.name}," +
                "${n.amount.name}," +
                "${n.pooColour?.name ?: ""}," +
                "${escapeCsv(n.notes)}," +
                "${n.createdAtMs}"
            )
        }

        // [MILESTONES]
        appendLine("[MILESTONES]")
        appendLine("id,syncUuid,babyId,timestampMs,title,description,category,photoUri,createdAtMs")
        milestones.forEach { m ->
            appendLine(
                "${m.id}," +
                "${escapeCsv(m.syncUuid)}," +
                "${m.babyId}," +
                "${m.timestampMs}," +
                "${escapeCsv(m.title)}," +
                "${escapeCsv(m.description)}," +
                "${m.category.name}," +
                "${escapeCsv(m.photoUri)}," +
                "${m.createdAtMs}"
            )
        }

        // [GROWTH]
        appendLine("[GROWTH]")
        appendLine("id,syncUuid,babyId,timestampMs,weightGrams,heightCm,headCircumferenceCm,footSizeMm,handSizeMm,legLengthCm,armLengthCm,backLengthCm,notes,createdAtMs")
        growth.forEach { g ->
            appendLine(
                "${g.id}," +
                "${escapeCsv(g.syncUuid)}," +
                "${g.babyId}," +
                "${g.timestampMs}," +
                "${g.weightGrams ?: ""}," +
                "${g.heightCm ?: ""}," +
                "${g.headCircumferenceCm ?: ""}," +
                "${g.footSizeMm ?: ""}," +
                "${g.handSizeMm ?: ""}," +
                "${g.legLengthCm ?: ""}," +
                "${g.armLengthCm ?: ""}," +
                "${g.backLengthCm ?: ""}," +
                "${escapeCsv(g.notes)}," +
                "${g.createdAtMs}"
            )
        }
    }
}

// ─── Import / parse ───────────────────────────────────────────────────────────

suspend fun parseBackup(content: String): ParsedBackup = withContext(Dispatchers.IO) {
    val lines = content.lines()

    // Read header (first 3 lines)
    val headerMap = mutableMapOf<String, String>()
    for (i in 0 until minOf(3, lines.size)) {
        val line = lines[i].trim()
        val eqIdx = line.indexOf('=')
        if (eqIdx > 0) {
            headerMap[line.substring(0, eqIdx).trim()] = line.substring(eqIdx + 1).trim()
        }
    }

    val rawVersionStr = headerMap["BABYDATALOG_BACKUP_VERSION"]
        ?: throw BackupException("Not a valid babyDataLog backup file")
    val rawVersion = rawVersionStr.toIntOrNull()
        ?: throw BackupException("Not a valid babyDataLog backup file")
    val appVersion = headerMap["APP_VERSION"]?.toIntOrNull() ?: 0
    val exportedAt = headerMap["EXPORTED_AT"] ?: ""

    val header = BackupHeader(
        backupFormatVersion = rawVersion,
        appVersion = appVersion,
        exportedAt = exportedAt
    )

    // Check for future backup version before conversion
    if (rawVersion > BACKUP_FORMAT_VERSION) {
        throw BackupException(
            "Backup was created by a newer version of babyDataLog (format v$rawVersion). " +
            "Please update the app to restore this backup."
        )
    }

    // Split content into sections: map of section name → list of lines (header + data rows)
    val rawSections = mutableMapOf<String, MutableList<String>>()
    var currentSection: String? = null
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            currentSection = trimmed.substring(1, trimmed.length - 1)
            rawSections[currentSection] = mutableListOf()
        } else if (currentSection != null && trimmed.isNotEmpty() &&
            !trimmed.startsWith("BABYDATALOG_BACKUP_VERSION") &&
            !trimmed.startsWith("APP_VERSION") &&
            !trimmed.startsWith("EXPORTED_AT")) {
            rawSections[currentSection]?.add(trimmed)
        }
    }

    // Run conversion layer (no-op for V1, but future-proofs)
    val convertedSections = convertBackupToCurrentVersion(rawVersion, rawSections)

    // Parse each section (skip header row, parse data rows)
    val babies = convertedSections["BABIES"]
        ?.drop(1)
        ?.mapNotNull { parseBabyRow(it) }
        ?: emptyList()

    val feedings = convertedSections["FEEDINGS"]
        ?.drop(1)
        ?.mapNotNull { parseFeedingRow(it) }
        ?: emptyList()

    val nappies = convertedSections["NAPPIES"]
        ?.drop(1)
        ?.mapNotNull { parseNappyRow(it) }
        ?: emptyList()

    val milestones = convertedSections["MILESTONES"]
        ?.drop(1)
        ?.mapNotNull { parseMilestoneRow(it) }
        ?: emptyList()

    val growth = convertedSections["GROWTH"]
        ?.drop(1)
        ?.mapNotNull { parseGrowthRow(it) }
        ?: emptyList()

    ParsedBackup(
        header = header,
        babies = babies,
        feedings = feedings,
        nappies = nappies,
        milestones = milestones,
        growthMeasurements = growth
    )
}

// ─── Conversion layer ─────────────────────────────────────────────────────────

private fun convertBackupToCurrentVersion(
    version: Int,
    sections: Map<String, List<String>>
): Map<String, List<String>> {
    var current = version
    var data = sections
    while (current < BACKUP_FORMAT_VERSION) {
        data = when (current) {
            // When backup format V2 is introduced, add:
            // 1 -> convertV1toV2(data)
            else -> throw BackupException(
                "Cannot convert backup version $current to current version $BACKUP_FORMAT_VERSION"
            )
        }
        current++
    }
    return data
}

// ─── CSV parsing helpers ──────────────────────────────────────────────────────

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                // Escaped quote inside quoted field
                current.append('"')
                i += 2
                continue
            }
            ch == '"' -> {
                inQuotes = !inQuotes
            }
            ch == ',' && !inQuotes -> {
                result.add(current.toString())
                current.clear()
            }
            else -> {
                current.append(ch)
            }
        }
        i++
    }
    result.add(current.toString())
    return result
}

private fun String?.orNullIfBlank(): String? = if (this.isNullOrBlank()) null else this

private fun String?.toLongOrNullSafe(): Long? = this?.trim()?.toLongOrNull()

private fun String?.toIntOrNullSafe(): Int? = this?.trim()?.toIntOrNull()

private fun String?.toFloatOrNullSafe(): Float? = this?.trim()?.toFloatOrNull()

// ─── Entity row parsers ───────────────────────────────────────────────────────

private fun parseBabyRow(line: String): Baby? {
    val cols = parseCsvLine(line)
    if (cols.size < 6) return null
    return try {
        Baby(
            id = cols[0].toLongOrNullSafe() ?: 0L, // preserved so import can remap child records
            syncUuid = cols[1].orNullIfBlank() ?: UUID.randomUUID().toString(),
            name = cols[2],
            birthDateMs = cols[3].toLongOrNullSafe() ?: return null,
            birthWeightGrams = cols[4].toIntOrNullSafe(),
            createdAtMs = cols[5].toLongOrNullSafe() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseFeedingRow(line: String): FeedingSession? {
    val cols = parseCsvLine(line)
    if (cols.size < 11) return null
    return try {
        val breastSide = try {
            cols[6].trim().let { if (it.isBlank()) BreastSide.LEFT else BreastSide.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            BreastSide.LEFT
        }
        val babyState = try {
            cols[7].trim().orNullIfBlank()?.let { BabyState.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
        val latchQuality = try {
            cols[8].trim().orNullIfBlank()?.let { LatchQuality.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
        FeedingSession(
            id = 0L,
            syncUuid = cols[1].orNullIfBlank() ?: UUID.randomUUID().toString(),
            babyId = cols[2].toLongOrNullSafe() ?: return null,
            startTimeMs = cols[3].toLongOrNullSafe() ?: return null,
            endTimeMs = cols[4].toLongOrNullSafe(),
            durationMinutes = cols[5].toFloatOrNullSafe(),
            breastSide = breastSide,
            babyState = babyState,
            latchQuality = latchQuality,
            notes = cols[9].orNullIfBlank(),
            createdAtMs = cols[10].toLongOrNullSafe() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseNappyRow(line: String): NappyChange? {
    val cols = parseCsvLine(line)
    if (cols.size < 9) return null
    return try {
        val type = try {
            cols[4].trim().let { if (it.isBlank()) NappyType.PEE else NappyType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            NappyType.PEE
        }
        val amount = try {
            cols[5].trim().let { if (it.isBlank()) NappyAmount.SMALL else NappyAmount.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            NappyAmount.SMALL
        }
        val pooColour = try {
            cols[6].trim().orNullIfBlank()?.let { PooColour.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
        NappyChange(
            id = 0L,
            syncUuid = cols[1].orNullIfBlank() ?: UUID.randomUUID().toString(),
            babyId = cols[2].toLongOrNullSafe() ?: return null,
            timestampMs = cols[3].toLongOrNullSafe() ?: return null,
            type = type,
            amount = amount,
            pooColour = pooColour,
            notes = cols[7].orNullIfBlank(),
            createdAtMs = cols[8].toLongOrNullSafe() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseMilestoneRow(line: String): Milestone? {
    val cols = parseCsvLine(line)
    if (cols.size < 9) return null
    return try {
        val category = try {
            cols[6].trim().let {
                if (it.isBlank()) MilestoneCategory.DEVELOPMENT else MilestoneCategory.valueOf(it)
            }
        } catch (e: IllegalArgumentException) {
            MilestoneCategory.DEVELOPMENT
        }
        Milestone(
            id = 0L,
            syncUuid = cols[1].orNullIfBlank() ?: UUID.randomUUID().toString(),
            babyId = cols[2].toLongOrNullSafe() ?: return null,
            timestampMs = cols[3].toLongOrNullSafe() ?: return null,
            title = cols[4],
            description = cols[5].orNullIfBlank(),
            category = category,
            photoUri = cols[7].orNullIfBlank(),
            createdAtMs = cols[8].toLongOrNullSafe() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseGrowthRow(line: String): GrowthMeasurement? {
    val cols = parseCsvLine(line)
    if (cols.size < 9) return null
    // New format has 14 columns (indices 0–13); old V1 format had 9 columns (indices 0–8).
    // When the extra columns are absent (old backup), they parse as null which is correct.
    val hasNewFields = cols.size >= 14
    return try {
        GrowthMeasurement(
            id = 0L,
            syncUuid = cols[1].orNullIfBlank() ?: UUID.randomUUID().toString(),
            babyId = cols[2].toLongOrNullSafe() ?: return null,
            timestampMs = cols[3].toLongOrNullSafe() ?: return null,
            weightGrams = cols[4].toIntOrNullSafe(),
            heightCm = cols[5].toFloatOrNullSafe(),
            headCircumferenceCm = cols[6].toFloatOrNullSafe(),
            footSizeMm = if (hasNewFields) cols[7].toIntOrNullSafe() else null,
            handSizeMm = if (hasNewFields) cols[8].toIntOrNullSafe() else null,
            legLengthCm = if (hasNewFields) cols[9].toFloatOrNullSafe() else null,
            armLengthCm = if (hasNewFields) cols[10].toFloatOrNullSafe() else null,
            backLengthCm = if (hasNewFields) cols[11].toFloatOrNullSafe() else null,
            notes = if (hasNewFields) cols[12].orNullIfBlank() else cols[7].orNullIfBlank(),
            createdAtMs = if (hasNewFields) cols[13].toLongOrNullSafe() ?: System.currentTimeMillis()
                          else cols[8].toLongOrNullSafe() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}
