package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.Baby
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.FeedingSession
import com.babydatalog.app.data.database.entity.LatchQuality
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ExportImportTest {

    private lateinit var sampleBabies: List<Baby>
    private lateinit var sampleFeedings: List<FeedingSession>
    private lateinit var sampleNappies: List<NappyChange>
    private lateinit var sampleMilestones: List<Milestone>

    @Before
    fun setUp() {
        val now = System.currentTimeMillis()

        sampleBabies = listOf(
            Baby(
                id = 1L,
                syncUuid = UUID.randomUUID().toString(),
                name = "Alice",
                birthDateMs = now - 86_400_000L * 30,
                birthWeightGrams = 3400,
                createdAtMs = now
            )
        )

        sampleFeedings = listOf(
            FeedingSession(
                id = 1L,
                syncUuid = UUID.randomUUID().toString(),
                babyId = 1L,
                startTimeMs = now - 3_600_000L,
                endTimeMs = now - 1_800_000L,
                durationMinutes = 30f,
                breastSide = BreastSide.LEFT,
                babyState = BabyState.ENGAGED,
                latchQuality = LatchQuality.GOOD,
                notes = "Went well",
                createdAtMs = now
            ),
            FeedingSession(
                id = 2L,
                syncUuid = UUID.randomUUID().toString(),
                babyId = 1L,
                startTimeMs = now - 7_200_000L,
                endTimeMs = now - 5_400_000L,
                durationMinutes = 30f,
                breastSide = BreastSide.RIGHT,
                babyState = BabyState.SLEEPY,
                latchQuality = LatchQuality.POOR,
                notes = null,
                createdAtMs = now
            )
        )

        sampleNappies = listOf(
            NappyChange(
                id = 1L,
                syncUuid = UUID.randomUUID().toString(),
                babyId = 1L,
                timestampMs = now,
                type = NappyType.POO,
                amount = NappyAmount.SMALL,
                pooColour = PooColour.YELLOW_SEEDY,
                notes = "Normal",
                createdAtMs = now
            )
        )

        sampleMilestones = listOf(
            Milestone(
                id = 1L,
                syncUuid = UUID.randomUUID().toString(),
                babyId = 1L,
                timestampMs = now,
                title = "First smile",
                description = "Smiled at mum",
                category = MilestoneCategory.SOCIAL,
                photoUri = null,
                createdAtMs = now
            )
        )
    }

    // ─── JSON export ─────────────────────────────────────────────────────────

    @Test
    fun exportToJson_returnsNonEmptyStringWithSchemaVersion() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        assertTrue("JSON should be non-empty", json.isNotEmpty())
        assertTrue("JSON should contain schemaVersion", json.contains("schemaVersion"))
    }

    @Test
    fun exportToJson_containsExpectedBabyName() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        assertTrue("JSON should contain baby name", json.contains("Alice"))
    }

    // ─── JSON round-trip ─────────────────────────────────────────────────────

    @Test
    fun importFromJson_roundTrip_schemaVersionIsOne() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val exportData = importFromJson(json)
        assertEquals(1, exportData.schemaVersion)
    }

    @Test
    fun importFromJson_roundTrip_feedingSessionCountMatches() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val exportData = importFromJson(json)
        assertEquals(sampleFeedings.size, exportData.feedingSessions.size)
    }

    @Test
    fun importFromJson_roundTrip_nappyTypeStoredAsString() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val exportData = importFromJson(json)
        assertEquals("POO", exportData.nappyChanges[0].type)
    }

    // ─── CSV export ──────────────────────────────────────────────────────────

    @Test
    fun exportToCsv_containsExpectedCsvKeys() = runTest {
        val csvMap = exportToCsv(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        assertTrue("Should contain babies.csv", csvMap.containsKey("babies.csv"))
        assertTrue("Should contain feedings.csv", csvMap.containsKey("feedings.csv"))
        assertTrue("Should contain nappies.csv", csvMap.containsKey("nappies.csv"))
        assertTrue("Should contain milestones.csv", csvMap.containsKey("milestones.csv"))
    }

    @Test
    fun exportToCsv_feedingsCsvStartsWithExpectedHeader() = runTest {
        val csvMap = exportToCsv(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val feedingsCsv = csvMap["feedings.csv"] ?: ""
        assertTrue(
            "feedings.csv should start with 'id,date,time,'",
            feedingsCsv.startsWith("id,date,time,")
        )
    }

    // ─── importFromJson error cases ───────────────────────────────────────────

    @Test(expected = ImportException::class)
    fun importFromJson_invalidJson_throwsImportException() = runTest {
        importFromJson("this is not valid json {{{")
    }

    @Test(expected = ImportException::class)
    fun importFromJson_unsupportedSchemaVersion_throwsImportException() = runTest {
        // Build a minimal valid JSON payload but with schemaVersion: 999
        val now = System.currentTimeMillis()
        val badJson = """
            {
              "schemaVersion": 999,
              "exportedAtMs": $now,
              "babies": [],
              "feedingSessions": [],
              "nappyChanges": [],
              "milestones": []
            }
        """.trimIndent()
        importFromJson(badJson)
    }

    // ─── additional round-trip checks ────────────────────────────────────────

    @Test
    fun importFromJson_roundTrip_babyNamePreserved() = runTest {
        val json = exportToJson(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val exportData = importFromJson(json)
        assertNotNull(exportData.babies.firstOrNull())
        assertEquals("Alice", exportData.babies[0].name)
    }

    @Test
    fun exportToCsv_babiesCsvIsNotEmpty() = runTest {
        val csvMap = exportToCsv(sampleBabies, sampleFeedings, sampleNappies, sampleMilestones)
        val babiesCsv = csvMap["babies.csv"] ?: ""
        assertTrue("babies.csv should not be empty", babiesCsv.isNotEmpty())
    }
}
