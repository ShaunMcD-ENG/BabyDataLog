package com.babydatalog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babydatalog.app.data.database.entity.SleepSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Insert
    suspend fun insertSleep(session: SleepSession): Long

    @Update
    suspend fun updateSleep(session: SleepSession)

    @Update
    suspend fun deleteSleep(session: SleepSession)

    @Query("UPDATE sleep_sessions SET deletedAtMs = :now, updatedAtMs = :now WHERE babyId = :babyId AND deletedAtMs IS NULL")
    suspend fun softDeleteAllForBaby(babyId: Long, now: Long)

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND deletedAtMs IS NULL ORDER BY startTimeMs DESC")
    fun getSleepSessionsForBaby(babyId: Long): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id AND deletedAtMs IS NULL")
    fun getSleepById(id: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND endTimeMs IS NULL AND deletedAtMs IS NULL LIMIT 1")
    fun getOpenSleepSession(babyId: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions")
    suspend fun getAllForSync(): List<SleepSession>

    @Query("SELECT * FROM sleep_sessions WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): SleepSession?

    @Query("SELECT * FROM sleep_sessions WHERE babyId = :babyId AND startTimeMs >= :minuteStart AND startTimeMs < :minuteEnd LIMIT 1")
    suspend fun getByNaturalKey(babyId: Long, minuteStart: Long, minuteEnd: Long): SleepSession?

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
