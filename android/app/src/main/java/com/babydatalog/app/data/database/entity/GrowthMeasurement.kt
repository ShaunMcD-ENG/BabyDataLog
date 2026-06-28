package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "growth_measurements",
    foreignKeys = [ForeignKey(entity = Baby::class, parentColumns = ["id"], childColumns = ["babyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("babyId")]
)
data class GrowthMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
