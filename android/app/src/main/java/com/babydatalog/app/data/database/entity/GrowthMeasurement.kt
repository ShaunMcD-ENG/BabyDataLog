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
    val weightGrams: Int?,             // nullable — may record height only
    val heightCm: Float?,              // nullable — may record weight only
    val headCircumferenceCm: Float?,   // optional — common at health visitor checks
    val footSizeMm: Int?,              // foot length in millimetres
    val handSizeMm: Int?,              // hand length in millimetres
    val legLengthCm: Float?,           // leg length in cm
    val armLengthCm: Float?,           // arm length in cm
    val backLengthCm: Float?,          // back (crown to rump) length in cm
    val notes: String?,
    val createdAtMs: Long
)
