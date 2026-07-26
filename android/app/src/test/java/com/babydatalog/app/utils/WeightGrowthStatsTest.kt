package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.GrowthMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val DAY_MS = 86_400_000L
private const val NOW = 1_700_000_000_000L

private fun measurement(id: Long, daysAgo: Long, weightGrams: Int?) = GrowthMeasurement(
    id = id,
    syncUuid = "uuid-$id",
    babyId = 1L,
    timestampMs = NOW - daysAgo * DAY_MS,
    weightGrams = weightGrams,
    heightCm = null,
    headCircumferenceCm = null,
    footSizeMm = null,
    handSizeMm = null,
    legLengthCm = null,
    armLengthCm = null,
    backLengthCm = null,
    notes = null,
    createdAtMs = NOW - daysAgo * DAY_MS
)

class WeightGrowthStatsTest {

    @Test
    fun computeWeightGrowthStats_emptyList_returnsAllNulls() {
        val stats = computeWeightGrowthStats(emptyList(), NOW)
        assertNull(stats.netChangeLastWeekGrams)
        assertNull(stats.netChangeLastMonthGrams)
        assertNull(stats.avgPerDayAllTimeGrams)
        assertEquals(4, stats.weeklyStats.size)
        assertEquals(3, stats.monthlyStats.size)
        stats.weeklyStats.forEach { assertNull(it.netChangeGrams) }
        stats.monthlyStats.forEach { assertNull(it.netChangeGrams) }
    }

    @Test
    fun computeWeightGrowthStats_singleRecord_returnsAllNulls() {
        val stats = computeWeightGrowthStats(listOf(measurement(1, 0, 4000)), NOW)
        assertNull(stats.netChangeLastWeekGrams)
        assertNull(stats.avgPerDayAllTimeGrams)
    }

    @Test
    fun computeWeightGrowthStats_twoRecordsOneWeekApart_computesNetChangeAndAvgPerDay() {
        val measurements = listOf(
            measurement(1, 7, 4000),
            measurement(2, 0, 4700)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        assertEquals(700, stats.netChangeLastWeekGrams)
        assertEquals(100f, stats.avgPerDayLastWeekGrams!!, 0.01f)
    }

    @Test
    fun computeWeightGrowthStats_recordsOverNinetyDays_computesAllTimeAvg() {
        val measurements = listOf(
            measurement(1, 90, 3000),
            measurement(2, 0, 5700)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        assertEquals(30f, stats.avgPerDayAllTimeGrams!!, 0.01f)
        assertEquals(30f, stats.avgPerDayLast3MonthsGrams!!, 0.01f)
    }

    @Test
    fun computeWeightGrowthStats_ignoresMeasurementsWithNullWeight() {
        val measurements = listOf(
            measurement(1, 7, 4000),
            measurement(2, 3, null), // e.g. a height-only record — must be skipped, not crash
            measurement(3, 0, 4700)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        assertEquals(700, stats.netChangeLastWeekGrams)
    }

    @Test
    fun computeWeightGrowthStats_noDataInWindow_bucketIsNull() {
        // Only two records, 60 days apart — the "22-28 days ago" weekly bucket has no
        // anchor inside it and should report null rather than fabricating a number.
        val measurements = listOf(
            measurement(1, 60, 4000),
            measurement(2, 0, 5000)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        val week4 = stats.weeklyStats[3]
        assertNull(week4.netChangeGrams)
    }
}
