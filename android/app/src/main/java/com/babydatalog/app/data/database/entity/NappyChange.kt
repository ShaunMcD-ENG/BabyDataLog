package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NappyType { PEE, POO, BOTH }
enum class NappyAmount { SMALL, LARGE }
enum class PooColour { NA, MECONIUM, DARK_GREEN, YELLOW_SEEDY, BRIGHT_YELLOW, GREEN, BROWN, PALE_WHITE, RED_BLOOD }

@Entity(
    tableName = "nappy_changes",
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
data class NappyChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val babyId: Long,
    val timestampMs: Long,
    val type: NappyType,
    val amount: NappyAmount,
    val pooColour: PooColour?,
    val notes: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)
