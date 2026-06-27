package com.babydatalog.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "babies")
data class Baby(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,          // UUID.randomUUID().toString() — for future sync
    val name: String,
    val birthDateMs: Long,         // Unix timestamp millis
    val birthWeightGrams: Int?,
    val createdAtMs: Long
)
