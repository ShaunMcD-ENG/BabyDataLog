package com.babydatalog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.SleepDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SleepDaoTest {

    private lateinit var db: BabyDataLogDatabase
    private lateinit var babyDao: BabyDao
    private lateinit var sleepDao: SleepDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyDataLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        babyDao = db.babyDao()
        sleepDao = db.sleepDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun testBaby(): Baby = Baby(
        syncUuid = UUID.randomUUID().toString(),
        name = "Test Baby",
        birthDateMs = System.currentTimeMillis(),
        birthWeightGrams = 3500,
        createdAtMs = System.currentTimeMillis()
    )

    private fun testSleep(
        babyId: Long,
        startMs: Long = System.currentTimeMillis(),
        endMs: Long? = startMs + 1_800_000L,
        type: SleepType = SleepType.NAP
    ): SleepSession = SleepSession(
        syncUuid = UUID.randomUUID().toString(),
        babyId = babyId,
        startTimeMs = startMs,
        endTimeMs = endMs,
        type = type,
        notes = null,
        createdAtMs = System.currentTimeMillis()
    )

    @Test
    fun insertAndRetrieveSleep() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId, type = SleepType.NIGHT))

        val sessions = sleepDao.getSleepSessionsForBaby(babyId).first()
        assertEquals(1, sessions.size)
        assertEquals(SleepType.NIGHT, sessions[0].type)
    }

    @Test
    fun getOpenSleepSession_returnsOnlyUnendedSession() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()
        sleepDao.insertSleep(testSleep(babyId, startMs = now - 3_600_000L, endMs = now - 1_800_000L)) // closed
        val openId = sleepDao.insertSleep(testSleep(babyId, startMs = now, endMs = null)) // open

        val open = sleepDao.getOpenSleepSession(babyId).first()
        assertNotNull(open)
        assertEquals(openId, open!!.id)
        assertNull(open.endTimeMs)
    }

    @Test
    fun getOpenSleepSession_nullWhenNoneOpen() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId))

        val open = sleepDao.getOpenSleepSession(babyId).first()
        assertNull(open)
    }

    @Test
    fun getByNaturalKey_matchesSameMinute() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()
        val minuteStart = now / 60_000L * 60_000L
        sleepDao.insertSleep(testSleep(babyId, startMs = minuteStart + 10_000L))

        val found = sleepDao.getByNaturalKey(babyId, minuteStart, minuteStart + 60_000L)
        assertNotNull(found)
    }

    @Test
    fun deleteSleep_removesRow() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId))
        val inserted = sleepDao.getSleepSessionsForBaby(babyId).first().first()

        val now = System.currentTimeMillis()
        sleepDao.deleteSleep(inserted.copy(deletedAtMs = now, updatedAtMs = now))

        val remaining = sleepDao.getSleepSessionsForBaby(babyId).first()
        assertTrue("Sleep list should be empty after deletion", remaining.isEmpty())
    }

    @Test
    fun updateSleep_persistsEndTime() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        sleepDao.insertSleep(testSleep(babyId, endMs = null))
        val inserted = sleepDao.getSleepSessionsForBaby(babyId).first().first()

        val ended = inserted.copy(endTimeMs = System.currentTimeMillis())
        sleepDao.updateSleep(ended)

        val retrieved = sleepDao.getSleepById(inserted.id).first()
        assertNotNull(retrieved!!.endTimeMs)
    }
}
