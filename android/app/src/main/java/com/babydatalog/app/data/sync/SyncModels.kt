package com.babydatalog.app.data.sync

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// --- API request / response models ---

@Serializable
data class RegisterRequest(val deviceId: String, val name: String, val pairingCode: String)

@Serializable
data class RegisterResponse(val ok: Boolean, val status: String)

@Serializable
data class PollResponse(val status: String, val apiKey: String? = null)

@Serializable
data class SyncPushRequest(val deviceId: String, val table: String, val records: JsonArray)

@Serializable
data class SyncPullResponse(val syncedAtMs: Long, val data: JsonObject)

// --- Per-table serializable DTOs (enums as strings for JSON) ---

@Serializable
data class SyncBaby(
    val id: Long, val syncUuid: String, val name: String,
    val birthDateMs: Long, val birthWeightGrams: Int?,
    val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null
)

@Serializable
data class SyncFeeding(
    val id: Long, val syncUuid: String, val babyId: Long,
    val startTimeMs: Long, val endTimeMs: Long?, val durationMinutes: Float?,
    val breastSide: String, val babyState: String?, val latchQuality: String?,
    val notes: String?, val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)

@Serializable
data class SyncNappy(
    val id: Long, val syncUuid: String, val babyId: Long,
    val timestampMs: Long, val type: String, val amount: String,
    val pooColour: String?, val notes: String?,
    val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)

@Serializable
data class SyncMilestone(
    val id: Long, val syncUuid: String, val babyId: Long,
    val timestampMs: Long, val title: String, val description: String?,
    val category: String, val photoUri: String?,
    val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)

@Serializable
data class SyncGrowth(
    val id: Long, val syncUuid: String, val babyId: Long,
    val timestampMs: Long, val weightGrams: Int?, val heightCm: Float?,
    val headCircumferenceCm: Float?, val footSizeMm: Int?, val handSizeMm: Int?,
    val legLengthCm: Float?, val armLengthCm: Float?, val backLengthCm: Float?,
    val notes: String?, val createdAtMs: Long, val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
    val babySyncUuid: String = ""
)

// --- Entity → DTO ---

fun Baby.toSync() = SyncBaby(id, syncUuid, name, birthDateMs, birthWeightGrams, createdAtMs, updatedAtMs, deletedAtMs)

fun FeedingSession.toSync() = SyncFeeding(
    id, syncUuid, babyId, startTimeMs, endTimeMs, durationMinutes,
    breastSide.name, babyState?.name, latchQuality?.name, notes, createdAtMs, updatedAtMs, deletedAtMs
)

fun NappyChange.toSync() = SyncNappy(
    id, syncUuid, babyId, timestampMs, type.name, amount.name,
    pooColour?.name, notes, createdAtMs, updatedAtMs, deletedAtMs
)

fun Milestone.toSync() = SyncMilestone(
    id, syncUuid, babyId, timestampMs, title, description,
    category.name, photoUri, createdAtMs, updatedAtMs, deletedAtMs
)

fun GrowthMeasurement.toSync() = SyncGrowth(
    id, syncUuid, babyId, timestampMs, weightGrams, heightCm, headCircumferenceCm,
    footSizeMm, handSizeMm, legLengthCm, armLengthCm, backLengthCm, notes, createdAtMs, updatedAtMs, deletedAtMs
)

// --- DTO → Entity ---

fun SyncBaby.toEntity() =
    Baby(id, syncUuid, name, birthDateMs, birthWeightGrams, createdAtMs, updatedAtMs, deletedAtMs)

fun SyncFeeding.toEntity() = FeedingSession(
    id, syncUuid, babyId, startTimeMs, endTimeMs, durationMinutes,
    BreastSide.valueOf(breastSide),
    babyState?.let { BabyState.valueOf(it) },
    latchQuality?.let { LatchQuality.valueOf(it) },
    notes, createdAtMs, updatedAtMs, deletedAtMs
)

fun SyncNappy.toEntity() = NappyChange(
    id, syncUuid, babyId, timestampMs,
    NappyType.valueOf(type),
    NappyAmount.valueOf(amount),
    pooColour?.let { PooColour.valueOf(it) },
    notes, createdAtMs, updatedAtMs, deletedAtMs
)

fun SyncMilestone.toEntity() = Milestone(
    id, syncUuid, babyId, timestampMs, title, description,
    MilestoneCategory.valueOf(category),
    photoUri, createdAtMs, updatedAtMs, deletedAtMs
)

fun SyncGrowth.toEntity() = GrowthMeasurement(
    id, syncUuid, babyId, timestampMs, weightGrams, heightCm, headCircumferenceCm,
    footSizeMm, handSizeMm, legLengthCm, armLengthCm, backLengthCm, notes, createdAtMs, updatedAtMs, deletedAtMs
)
