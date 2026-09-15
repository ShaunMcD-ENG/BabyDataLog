package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SleepType { NAP, NIGHT }

@Entity(
    tableName = "sleep_sessions",
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
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,       // null while the session is open ("currently asleep")
    val type: SleepType,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
