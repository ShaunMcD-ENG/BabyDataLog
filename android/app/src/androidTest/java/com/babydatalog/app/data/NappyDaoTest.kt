package com.babydatalog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.dao.NappyDao
import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour
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
class NappyDaoTest {

    private lateinit var db: BabyDataLogDatabase
    private lateinit var babyDao: BabyDao
    private lateinit var nappyDao: NappyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyDataLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        babyDao = db.babyDao()
        nappyDao = db.nappyDao()
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

    private fun testNappy(babyId: Long, type: NappyType = NappyType.POO, timestampMs: Long = System.currentTimeMillis()): NappyChange =
        NappyChange(
            syncUuid = UUID.randomUUID().toString(),
            babyId = babyId,
            timestampMs = timestampMs,
            type = type,
            amount = NappyAmount.SMALL,
            pooColour = if (type != NappyType.PEE) PooColour.YELLOW_SEEDY else null,
            notes = null,
            createdAtMs = System.currentTimeMillis()
        )

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveNappy() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val nappy = testNappy(babyId, type = NappyType.POO)
        nappyDao.insertNappy(nappy)

        val nappies = nappyDao.getNappiesForBaby(babyId).first()
        assertEquals(1, nappies.size)
        assertEquals(NappyType.POO, nappies[0].type)
        assertEquals(NappyAmount.SMALL, nappies[0].amount)
    }

    @Test
    fun getNappyCountByType_countsCorrectly() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()

        // Insert 2 POO and 1 PEE
        nappyDao.insertNappy(testNappy(babyId, NappyType.POO, now - 3_600_000L))
        nappyDao.insertNappy(testNappy(babyId, NappyType.POO, now - 1_800_000L))
        nappyDao.insertNappy(testNappy(babyId, NappyType.PEE, now - 900_000L))

        // getNappyCountByType uses < for endMs so use now + 1 to include the latest entry
        val pooCount = nappyDao.getNappyCountByType(
            babyId = babyId,
            startMs = now - 86_400_000L,
            endMs = now + 1L,
            type = NappyType.POO
        ).first()

        assertEquals(2, pooCount)
    }

    @Test
    fun getLastNappy_returnsLatest() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()

        val olderNappy = testNappy(babyId, NappyType.PEE,  timestampMs = now - 7_200_000L)
        val newerNappy = testNappy(babyId, NappyType.POO, timestampMs = now - 600_000L)

        nappyDao.insertNappy(olderNappy)
        nappyDao.insertNappy(newerNappy)

        val last = nappyDao.getLastNappy(babyId).first()
        assertNotNull(last)
        assertEquals(NappyType.POO, last!!.type)
        assertEquals(now - 600_000L, last.timestampMs)
    }

    @Test
    fun deleteNappy() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        nappyDao.insertNappy(testNappy(babyId))

        val inserted = nappyDao.getNappiesForBaby(babyId).first().first()
        nappyDao.deleteNappy(inserted)

        val remaining = nappyDao.getNappiesForBaby(babyId).first()
        assertTrue("Nappy list should be empty after deletion", remaining.isEmpty())
    }

    @Test
    fun insertMultipleNappies_allRetrieved() = runTest {
        val babyId = babyDao.insertBaby(testBaby())
        val now = System.currentTimeMillis()

        nappyDao.insertNappy(testNappy(babyId, NappyType.POO,  timestampMs = now - 3_600_000L))
        nappyDao.insertNappy(testNappy(babyId, NappyType.PEE,  timestampMs = now - 1_800_000L))
        nappyDao.insertNappy(testNappy(babyId, NappyType.BOTH, timestampMs = now - 900_000L))

        val nappies = nappyDao.getNappiesForBaby(babyId).first()
        assertEquals(3, nappies.size)
    }
}
