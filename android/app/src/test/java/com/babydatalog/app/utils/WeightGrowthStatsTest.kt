package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.GrowthMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 86_400_000L
private const val NOW = 1_700_000_000_000L

private fun measurement(id: Long, daysAgo: Long, weightGrams: Int?) =
    measurementAtMs(id, NOW - daysAgo * DAY_MS, weightGrams)

private fun measurementAtMs(id: Long, timestampMs: Long, weightGrams: Int?) = GrowthMeasurement(
    id = id,
    syncUuid = "uuid-$id",
    babyId = 1L,
    timestampMs = timestampMs,
    weightGrams = weightGrams,
    heightCm = null,
    headCircumferenceCm = null,
    footSizeMm = null,
    handSizeMm = null,
    legLengthCm = null,
    armLengthCm = null,
    backLengthCm = null,
    notes = null,
    createdAtMs = timestampMs
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
    fun computeWeightGrowthStats_sparseData_interpolatesProportionalSliceForBucket() {
        // Only two records, 60 days apart, at a steady 1000g/60days = 16.667g/day rate.
        // The "22-28 days ago" weekly bucket has no actual weigh-in inside it, but with
        // interpolation it should still report a proportional slice of that steady trend
        // (7 days * 16.667g/day ≈ 117g) instead of bailing out to null.
        val measurements = listOf(
            measurement(1, 60, 4000),
            measurement(2, 0, 5000)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        val week4 = stats.weeklyStats[3]
        assertEquals(117, week4.netChangeGrams)
        assertEquals(16.67f, week4.avgPerDayGrams!!, 0.1f)
    }

    @Test
    fun computeWeightGrowthStats_windowStartsBeforeAllData_bucketIsNull() {
        // Only 10 days of history exist — a bucket reaching further back than that
        // (61-90 days ago) has nothing to interpolate from and must report null, not a
        // fabricated extrapolation.
        val measurements = listOf(
            measurement(1, 10, 4000),
            measurement(2, 0, 4200)
        )
        val stats = computeWeightGrowthStats(measurements, NOW)
        assertNull(stats.monthlyStats[2].netChangeGrams)
    }

    @Test
    fun computeWeightGrowthStats_matchesReportedScenario_notTheOldBuggyJump() {
        // Real reported data: Jul 15 18:19 (3450g), Jul 19 20:48 (3560g), Jul 26 20:04
        // (3850g). Checking "Last 7 Days" right after logging the 3rd weigh-in should give
        // roughly the true ~290g gain between the last two weigh-ins (interpolated across
        // the ~7-day window) — not the old bug's 400g, which came from the boundary falling
        // a few minutes short of the 2nd weigh-in and skipping back to the 1st instead.
        val m1 = measurementAtMs(1, NOW - 956_688_593L, 3450)   // ~11.07 days before m3
        val m2 = measurementAtMs(2, NOW - 602_186_942L, 3560)   // ~6.97 days before m3
        val m3 = measurementAtMs(3, NOW, 3850)
        val stats = computeWeightGrowthStats(listOf(m1, m2, m3), NOW)
        assertEquals(291, stats.netChangeLastWeekGrams)
    }

    @Test
    fun computeWeightGrowthStats_startWeightIsContinuousAcrossMeasurementBoundary() {
        // The actual bug: whichever side of a real measurement's exact timestamp the window
        // boundary fell on determined whether that measurement was used as an anchor at all —
        // a one-second difference in "now" could skip straight past it to a much older (and
        // very different) weigh-in, an instant ~110g jump. Interpolation must vary smoothly
        // across that boundary instead of snapping.
        val m1 = measurementAtMs(1, NOW - 956_688_593L, 3450)
        val m2 = measurementAtMs(2, NOW - 602_186_942L, 3560)
        val m3 = measurementAtMs(3, NOW, 3850)
        val measurements = listOf(m1, m2, m3)

        // "now" positioned so the 7-day-ago boundary lands one second either side of m2.
        val justBefore = computeWeightGrowthStats(measurements, m2.timestampMs + 7 * DAY_MS - 1000L)
        val justAfter = computeWeightGrowthStats(measurements, m2.timestampMs + 7 * DAY_MS + 1000L)

        val delta = kotlin.math.abs(
            justBefore.netChangeLastWeekGrams!! - justAfter.netChangeLastWeekGrams!!
        )
        assertTrue("Expected a continuous estimate across the boundary, but got $delta g apart", delta <= 1)
    }
}
