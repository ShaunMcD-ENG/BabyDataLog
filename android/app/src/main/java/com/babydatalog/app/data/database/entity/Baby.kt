package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "babies")
data class Baby(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val name: String,
    val birthDateMs: Long,
    val birthWeightGrams: Int?,
    val createdAtMs: Long,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val deletedAtMs: Long? = null
)
