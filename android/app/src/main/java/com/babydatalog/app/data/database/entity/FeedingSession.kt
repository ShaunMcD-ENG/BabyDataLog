package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BreastSide { LEFT, RIGHT, BOTH, BOTTLE }
enum class BabyState { SLEEPY, ENGAGED }
enum class LatchQuality { GOOD, POOR }

@Entity(
    tableName = "feeding_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Baby::class,
            parentColumns = ["id"],
            childColumns = ["babyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("babyId")]
)
data class FeedingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val durationMinutes: Float?,
    val breastSide: BreastSide,
    val babyState: BabyState?,
    val latchQuality: LatchQuality?,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
