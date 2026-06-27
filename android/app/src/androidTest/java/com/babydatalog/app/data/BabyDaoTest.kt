package com.babydatalog.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babydatalog.app.data.database.BabyDataLogDatabase
import com.babydatalog.app.data.database.dao.BabyDao
import com.babydatalog.app.data.database.entity.Baby
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
class BabyDaoTest {

    private lateinit var db: BabyDataLogDatabase
    private lateinit var dao: BabyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BabyDataLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.babyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun testBaby(name: String = "Test Baby"): Baby = Baby(
        syncUuid = UUID.randomUUID().toString(),
        name = name,
        birthDateMs = System.currentTimeMillis(),
        birthWeightGrams = 3500,
        createdAtMs = System.currentTimeMillis()
    )

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveBaby() = runTest {
        val baby = testBaby("Olivia")
        val insertedId = dao.insertBaby(baby)

        val retrieved = dao.getBabyByIdOnce(insertedId)
        assertNotNull(retrieved)
        assertEquals("Olivia", retrieved!!.name)
    }

    @Test
    fun updateBaby() = runTest {
        val baby = testBaby("Original Name")
        val insertedId = dao.insertBaby(baby)

        val inserted = dao.getBabyByIdOnce(insertedId)!!
        val updated = inserted.copy(name = "Updated Name")
        dao.updateBaby(updated)

        val retrieved = dao.getBabyByIdOnce(insertedId)
        assertNotNull(retrieved)
        assertEquals("Updated Name", retrieved!!.name)
    }

    @Test
    fun deleteBaby() = runTest {
        val baby = testBaby()
        val insertedId = dao.insertBaby(baby)

        val inserted = dao.getBabyByIdOnce(insertedId)!!
        dao.deleteBaby(inserted)

        val allBabies = dao.getAllBabies().first()
        assertTrue("Baby list should be empty after deletion", allBabies.isEmpty())
    }

    @Test
    fun getAllBabies_returnsAllInserted() = runTest {
        dao.insertBaby(testBaby("Baby One"))
        dao.insertBaby(testBaby("Baby Two"))
        dao.insertBaby(testBaby("Baby Three"))

        val allBabies = dao.getAllBabies().first()
        assertEquals(3, allBabies.size)
    }

    @Test
    fun getAllBabies_emptyInitially() = runTest {
        val allBabies = dao.getAllBabies().first()
        assertTrue("Database should start empty", allBabies.isEmpty())
    }
}
