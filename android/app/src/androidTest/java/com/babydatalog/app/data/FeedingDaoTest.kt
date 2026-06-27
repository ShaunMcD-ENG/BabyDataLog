package com.babydatalog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.FeedingDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.LatchQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FeedingDaoTest {

    private lateinit var db: BabyDataLogDatabase
    private lateinit var babyDao: BabyDao
    private lateinit var feedingDao: FeedingDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyDataLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        babyDao = db.babyDao()
        feedingDao = db.feedingDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun testBaby(name: String = "Test Baby"): Baby = Baby(
        syncUuid = UUID.randomUUID().toString(),
        name = name,
        birthDateMs = System.currentTimeMillis(),
        birthWeightGrams = 3500,
        createdAtMs = System.currentTimeMillis()
    )

    private fun testFeeding(babyId: Long, startMs: Long = System.currentTimeMillis()): FeedingSession =
        FeedingSession(
            syncUuid = UUID.randomUUID().toString(),
            babyId = babyId,
            startTimeMs = startMs,
            endTimeMs = startMs + 1_800_000L,
            durationMinutes = 30f,
            breastSide = BreastSide.LEFT,
            babyState = BabyState.ENGAGED,
            latchQuality = LatchQuality.GOOD,
            notes = null,
            createdAtMs = System.currentTimeMillis()
        )

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveFeeding() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val feeding = testFeeding(babyId).copy(breastSide = BreastSide.RIGHT)
        feedingDao.insertFeeding(feeding)

        val feedings = feedingDao.getFeedingsForBaby(babyId).first()
        assertEquals(1, feedings.size)
        assertEquals(BreastSide.RIGHT, feedings[0].breastSide)
    }

    @Test
    fun getLastFeeding_returnsLatest() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()

        val olderFeeding = testFeeding(babyId, startMs = now - 3_600_000L) // 1 hour ago
        val newerFeeding = testFeeding(babyId, startMs = now - 600_000L)   // 10 min ago

        feedingDao.insertFeeding(olderFeeding)
        feedingDao.insertFeeding(newerFeeding)

        val last = feedingDao.getLastFeeding(babyId).first()
        assertNotNull(last)
        assertEquals(now - 600_000L, last!!.startTimeMs)
    }

    @Test
    fun getFeedingsInRange() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()

        // Insert 3 feedings: two inside the range, one outside
        val insideOne = testFeeding(babyId, startMs = now - 7_200_000L)  // 2 hours ago — inside
        val insideTwo = testFeeding(babyId, startMs = now - 3_600_000L)  // 1 hour ago — inside
        val outside   = testFeeding(babyId, startMs = now - 86_400_000L) // 24 hours ago — outside

        feedingDao.insertFeeding(insideOne)
        feedingDao.insertFeeding(insideTwo)
        feedingDao.insertFeeding(outside)

        // Range covers the last 12 hours
        val rangeStart = now - 43_200_000L
        val rangeEnd   = now

        val results = feedingDao.getFeedingsInRange(babyId, rangeStart, rangeEnd).first()
        assertEquals(2, results.size)
    }

    @Test
    fun deleteFeeding() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val insertedId = feedingDao.insertFeeding(testFeeding(babyId))

        val inserted = feedingDao.getFeedingsForBaby(babyId).first().first()
        feedingDao.deleteFeeding(inserted)

        val remaining = feedingDao.getFeedingsForBaby(babyId).first()
        assertTrue("Feeding list should be empty after deletion", remaining.isEmpty())
    }

    @Test
    fun updateFeeding() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val insertedId = feedingDao.insertFeeding(testFeeding(babyId))

        val inserted = feedingDao.getFeedingsForBaby(babyId).first().first()
        val updated = inserted.copy(notes = "Updated notes")
        feedingDao.updateFeeding(updated)

        val retrieved = feedingDao.getFeedingsForBaby(babyId).first().first()
        assertEquals("Updated notes", retrieved.notes)
    }
}
