package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val UTC: ZoneId = ZoneOffset.UTC
private const val DAYS_MS_TEST = 86_400_000L

private fun session(
    id: Long = 1L,
    startMs: Long,
    endMs: Long?,
    type: SleepType = SleepType.NAP
) = SleepSession(
    id = id,
    syncUuid = "uuid-$id",
    babyId = 1L,
    startTimeMs = startMs,
    endTimeMs = endMs,
    type = type,
    notes = null,
    createdAtMs = startMs
)

class SleepStatsTest {

    // ─── defaultSleepTypeForStart ───────────────────────────────────────────

    @Test
    fun defaultSleepTypeForStart_earlyAfternoon_isNap() {
        // 2024-01-01 14:00 UTC
        val ms = LocalDate.of(2024, 1, 1).atTime(14, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NAP, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_evening_isNight() {
        val ms = LocalDate.of(2024, 1, 1).atTime(19, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_earlyMorning_isNight() {
        val ms = LocalDate.of(2024, 1, 1).atTime(3, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(ms, UTC))
    }

    @Test
    fun defaultSleepTypeForStart_boundaryHours() {
        val sixAm = LocalDate.of(2024, 1, 1).atTime(6, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        val sixPm = LocalDate.of(2024, 1, 1).atTime(18, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        assertEquals(SleepType.NAP, defaultSleepTypeForStart(sixAm, UTC))   // 6am is the first NAP hour
        assertEquals(SleepType.NIGHT, defaultSleepTypeForStart(sixPm, UTC)) // 6pm is the first NIGHT hour
    }

    // ─── splitAcrossDays ─────────────────────────────────────────────────────

    @Test
    fun splitAcrossDays_withinOneDay_returnsSingleSlice() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val s = session(startMs = dayStart + 3_600_000L, endMs = dayStart + 5_400_000L) // 1h-1h30

        val slices = splitAcrossDays(s, UTC)

        assertEquals(1, slices.size)
        assertEquals(LocalDate.of(2024, 1, 1), slices[0].date)
        assertEquals(1_800_000L, slices[0].endMs - slices[0].startMs)
    }

    @Test
    fun splitAcrossDays_crossingMidnight_splitsIntoTwo() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        // 8pm day 1 to 6am day 2 — 10 hours total
        val startMs = day1Start + 20 * 3_600_000L
        val endMs = day1Start + 30 * 3_600_000L
        val s = session(startMs = startMs, endMs = endMs, type = SleepType.NIGHT)

        val slices = splitAcrossDays(s, UTC)

        assertEquals(2, slices.size)
        assertEquals(LocalDate.of(2024, 1, 1), slices[0].date)
        assertEquals(LocalDate.of(2024, 1, 2), slices[1].date)
        val firstMinutes = (slices[0].endMs - slices[0].startMs) / 60_000
        val secondMinutes = (slices[1].endMs - slices[1].startMs) / 60_000
        assertEquals(4 * 60L, firstMinutes)  // 8pm-midnight = 4h
        assertEquals(6 * 60L, secondMinutes) // midnight-6am = 6h
        assertEquals(SleepType.NIGHT, slices[0].type)
        assertEquals(SleepType.NIGHT, slices[1].type)
    }

    @Test
    fun splitAcrossDays_openSession_returnsEmpty() {
        val s = session(startMs = System.currentTimeMillis(), endMs = null)
        assertTrue(splitAcrossDays(s, UTC).isEmpty())
    }

    @Test
    fun splitAcrossDays_zeroOrNegativeDuration_returnsEmpty() {
        val now = System.currentTimeMillis()
        assertTrue(splitAcrossDays(session(startMs = now, endMs = now), UTC).isEmpty())
        assertTrue(splitAcrossDays(session(startMs = now, endMs = now - 1000L), UTC).isEmpty())
    }

    @Test
    fun splitAcrossDays_nonUtcZone_usesPassedZoneNotUtc() {
        val kiritimatiZone = ZoneId.of("Pacific/Kiritimati") // UTC+14

        // Session from 2024-01-01 23:00 UTC to 2024-01-02 01:00 UTC (2 hours, crosses UTC midnight)
        // In Kiritimati time (UTC+14): 2024-01-02 13:00 to 2024-01-02 15:00 (same local day, no local midnight crossing)
        val utcStart = LocalDate.of(2024, 1, 1).atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val utcEnd = LocalDate.of(2024, 1, 2).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val s = session(startMs = utcStart, endMs = utcEnd, type = SleepType.NAP)
        val slices = splitAcrossDays(s, kiritimatiZone)

        // Should be 1 slice because in Kiritimati's local timezone, the session
        // doesn't cross midnight. If the function incorrectly used UTC, it would return 2 slices.
        assertEquals(1, slices.size)
        assertEquals(LocalDate.of(2024, 1, 2), slices[0].date)
        assertEquals(2 * 3_600_000L, slices[0].endMs - slices[0].startMs) // 2 hours
    }

    // ─── periodStartMs ───────────────────────────────────────────────────────

    @Test
    fun periodStartMs_last7Days_isSevenDaysBeforeNow() {
        val now = 1_700_000_000_000L
        val start = periodStartMs(SleepStatsPeriod.LAST_7_DAYS, emptyList(), now, UTC)
        assertEquals(now - 7 * 86_400_000L, start)
    }

    @Test
    fun periodStartMs_allTime_isEarliestSessionStart() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 100 * 86_400_000L, endMs = now - 99 * 86_400_000L),
            session(id = 2, startMs = now - 5 * 86_400_000L, endMs = now - 4 * 86_400_000L)
        )
        val start = periodStartMs(SleepStatsPeriod.ALL_TIME, sessions, now, UTC)
        assertEquals(now - 100 * 86_400_000L, start)
    }

    @Test
    fun periodStartMs_allTime_noSessions_isNow() {
        val now = 1_700_000_000_000L
        assertEquals(now, periodStartMs(SleepStatsPeriod.ALL_TIME, emptyList(), now, UTC))
    }

    // ─── computeSleepStats ───────────────────────────────────────────────────

    @Test
    fun computeSleepStats_emptySessions_allZero() {
        val stats = computeSleepStats(emptyList(), SleepStatsPeriod.LAST_7_DAYS, nowMs = 1_700_000_000_000L, zone = UTC)
        assertEquals(0, stats.nap.sessionCount)
        assertEquals(0f, stats.nap.totalMinutes, 0.01f)
        assertEquals(0, stats.night.sessionCount)
        assertEquals(null, stats.combined.sessionCount)
        assertEquals(0f, stats.combined.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_napsOnly_countedSeparatelyFromNight() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 3_600_000L, endMs = now - 1_800_000L, type = SleepType.NAP),   // 30 min nap
            session(id = 2, startMs = now - 7_200_000L, endMs = now - 5_400_000L, type = SleepType.NAP),   // 30 min nap
            session(id = 3, startMs = now - 36_000_000L, endMs = now - 7_200_000L, type = SleepType.NIGHT) // 8h "night"
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)

        assertEquals(2, stats.nap.sessionCount)
        assertEquals(60f, stats.nap.totalMinutes, 0.01f)
        assertEquals(1, stats.night.sessionCount)
        assertEquals(480f, stats.night.totalMinutes, 0.01f)
        // combined has no session count but sums both
        assertEquals(null, stats.combined.sessionCount)
        assertEquals(540f, stats.combined.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_openSession_excludedEntirely() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 1_800_000L, endMs = null, type = SleepType.NAP) // still asleep
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(0, stats.nap.sessionCount)
        assertEquals(0f, stats.nap.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_overnightSession_countsOnBothDays() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = day1Start + 40 * 3_600_000L // well into day 2
        val overnight = session(
            id = 1,
            startMs = day1Start + 20 * 3_600_000L, // 8pm day 1
            endMs = day1Start + 30 * 3_600_000L,   // 6am day 2
            type = SleepType.NIGHT
        )
        val stats = computeSleepStats(listOf(overnight), SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // one physical session, split across 2 days -> count of 2 (spec §5.1's stated consequence)
        assertEquals(2, stats.night.sessionCount)
        assertEquals(600f, stats.night.totalMinutes, 0.01f) // 10 hours total, unaffected by the split
    }

    @Test
    fun computeSleepStats_sessionCrossesPeriodsStartBoundary_countsOnlyInPeriodPortion() {
        // Use "now" at 3pm (15:00) local time, NOT at midnight
        // This ensures periodStartMs lands mid-day, not at a midnight boundary
        val now = LocalDate.of(2024, 1, 8).atTime(15, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        // For LAST_7_DAYS: periodStartMs = now - 7*DAY_MS = 2024-01-01 at 3pm
        val periodStart = now - 7 * 86_400_000L

        // Session: starts 2 hours before period cutoff, ends 2 hours after it
        // Entirely on 2024-01-01, so splitAcrossDays produces ONE slice (no midnight split at the period boundary)
        // Slice spans 2024-01-01 13:00 → 17:00 (1pm → 5pm, 4 hours total)
        // Period boundary is at 2024-01-01 15:00 (3pm)
        // Only the 15:00→17:00 portion (2 hours = 120 minutes) falls within [periodStart, now]
        val sessions = listOf(
            session(
                id = 1,
                startMs = periodStart - 2 * 3_600_000L,   // 1pm: 2 hours before 3pm cutoff
                endMs = periodStart + 2 * 3_600_000L,     // 5pm: 2 hours after 3pm cutoff
                type = SleepType.NAP
            )
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)

        // splitAcrossDays produces ONE slice (1pm→5pm, same local day, doesn't cross midnight at period boundary)
        // Old buggy filter would include it in full (4 hours)
        // New clipping logic cuts it to [3pm, 5pm] (2 hours = 120 minutes)
        assertEquals(1, stats.nap.sessionCount)
        assertEquals(120f, stats.nap.totalMinutes, 0.01f) // Only 2 in-period hours, not 4 total
    }

    // ─── computeSleepHistogram ───────────────────────────────────────────────

    @Test
    fun computeSleepHistogram_singleDaySingleNap_marksOnlyOverlappingHours() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // Nap from 14:45 to 15:15 -> overlaps hour 14 and hour 15
        val nap = session(startMs = dayStart + 14 * 3_600_000L + 45 * 60_000L, endMs = dayStart + 15 * 3_600_000L + 15 * 60_000L)

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        assertEquals(24, histogram.hourlyPercent.size)
        assertTrue("hour 14 should show >0%", histogram.hourlyPercent[14] > 0f)
        assertTrue("hour 15 should show >0%", histogram.hourlyPercent[15] > 0f)
        assertEquals(0f, histogram.hourlyPercent[10], 0.01f)
    }

    @Test
    fun computeSleepHistogram_binaryPresence_notDurationWeighted() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // Only 1 minute of overlap with hour 6, but the whole hour still counts as "asleep"
        val nap = session(startMs = dayStart + 5 * 3_600_000L + 59 * 60_000L, endMs = dayStart + 6 * 3_600_000L + 1 * 60_000L)

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        // period is LAST_7_DAYS -> denominator is 7 days, one day has presence in hour 5 and hour 6
        assertTrue(histogram.hourlyPercent[5] > 0f)
        assertTrue(histogram.hourlyPercent[6] > 0f)
    }

    @Test
    fun computeSleepHistogram_lensFiltersByType() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        val nap = session(id = 1, startMs = dayStart + 15 * 3_600_000L, endMs = dayStart + 16 * 3_600_000L, type = SleepType.NAP)
        val night = session(id = 2, startMs = dayStart + 21 * 3_600_000L, endMs = dayStart + 23 * 3_600_000L, type = SleepType.NIGHT)

        val napOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)
        assertEquals(0f, napOnly.hourlyPercent[21], 0.01f)
        assertTrue(napOnly.hourlyPercent[15] > 0f)

        val nightOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NIGHT, now, UTC)
        assertEquals(0f, nightOnly.hourlyPercent[15], 0.01f)
        assertTrue(nightOnly.hourlyPercent[21] > 0f)

        val combined = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(combined.hourlyPercent[15] > 0f)
        assertTrue(combined.hourlyPercent[21] > 0f)
    }

    @Test
    fun computeSleepHistogram_openSession_excluded() {
        val now = 1_700_000_000_000L
        val open = session(startMs = now - 1_800_000L, endMs = null)
        val histogram = computeSleepHistogram(listOf(open), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(histogram.hourlyPercent.all { it == 0f })
    }

    @Test
    fun computeSleepHistogram_sliceStradlesPeriodStart_clipsAndExcludesOutsideHours() {
        // Use "now" at 3pm (15:00) local time, NOT at midnight
        // This ensures periodStartMs lands mid-day, not at a midnight boundary
        val now = LocalDate.of(2024, 1, 8).atTime(15, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        // For LAST_7_DAYS: periodStartMs = now - 7*DAY_MS = 2024-01-01 at 3pm
        val periodStart = now - 7 * DAYS_MS_TEST

        // Session: entirely on 2024-01-01, spans 1pm → 5pm (4 hours total)
        // splitAcrossDays produces ONE slice (no midnight crossing)
        // This slice straddles the period boundary at 3pm
        // Only the 3pm→5pm portion falls within [periodStart, now]
        // Hours marked in period: 15 (3pm) and 16 (4pm), but NOT 13 (1pm) or 14 (2pm)
        val sessions = listOf(
            session(
                id = 1,
                startMs = periodStart - 2 * 3_600_000L,   // 1pm: 2 hours before 3pm cutoff
                endMs = periodStart + 2 * 3_600_000L,     // 5pm: 2 hours after 3pm cutoff
                type = SleepType.NAP
            )
        )
        val histogram = computeSleepHistogram(sessions, SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        // Old buggy filter would mark hours 13, 14, 15, 16 (the entire 1pm-5pm span)
        // New clipping logic should mark only 15 and 16 (the 3pm-5pm in-period span)
        assertEquals(0f, histogram.hourlyPercent[13], 0.01f)
        assertEquals(0f, histogram.hourlyPercent[14], 0.01f)
        assertTrue("hour 15 (3pm) should be marked - in period", histogram.hourlyPercent[15] > 0f)
        assertTrue("hour 16 (4pm) should be marked - in period", histogram.hourlyPercent[16] > 0f)
    }
}
