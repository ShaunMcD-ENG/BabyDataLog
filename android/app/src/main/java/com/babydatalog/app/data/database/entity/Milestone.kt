package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MilestoneCategory { DEVELOPMENT, MEDICAL, SOCIAL, PHYSICAL, FIRST_TIME }

@Entity(
    tableName = "milestones",
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
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val timestampMs: Long,
    val title: String,
    val description: String?,
    val category: MilestoneCategory,
    val photoUri: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)
