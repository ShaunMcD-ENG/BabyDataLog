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
    val startTimeMs: Long,         // when feed started
    val endTimeMs: Long?,          // null if not recorded
    val durationMinutes: Float?,   // calculated from start/end OR manually entered
    val breastSide: BreastSide,    // enum
    val babyState: BabyState?,     // enum, optional
    val latchQuality: LatchQuality?, // enum, optional
    val notes: String?,
    val createdAtMs: Long
)
