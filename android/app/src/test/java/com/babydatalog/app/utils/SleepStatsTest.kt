package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
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
    fun computeSleepStats_overnightSession_countsOnce() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = day1Start + 40 * 3_600_000L // well into day 2
        val overnight = session(
            id = 1,
            startMs = day1Start + 20 * 3_600_000L, // 8pm day 1
            endMs = day1Start + 30 * 3_600_000L,   // 6am day 2
            type = SleepType.NIGHT
        )
        val stats = computeSleepStats(listOf(overnight), SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // one physical session -> count of 1, so Average Length is the real 10h, not 5h
        assertEquals(1, stats.night.sessionCount)
        assertEquals(600f, stats.night.totalMinutes, 0.01f)
        assertEquals(600f, stats.night.avgSessionDurationMinutes!!, 0.01f)
    }

    @Test
    fun computeSleepStats_overlappingDuplicates_notDoubleCounted() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 4 * 3_600_000L, endMs = now - 2 * 3_600_000L, type = SleepType.NAP),
            // same nap logged again by the other parent, slightly offset
            session(id = 2, startMs = now - 4 * 3_600_000L + 300_000L, endMs = now - 2 * 3_600_000L, type = SleepType.NAP)
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(1, stats.nap.sessionCount)
        assertEquals(120f, stats.nap.totalMinutes, 0.01f)
        assertEquals(120f, stats.combined.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_futureEndTime_cappedAtNow() {
        val now = 1_700_000_000_000L
        // started 2h ago, but end mistakenly entered as tomorrow
        val sessions = listOf(session(startMs = now - 2 * 3_600_000L, endMs = now + DAYS_MS_TEST, type = SleepType.NIGHT))
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(120f, stats.night.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_misdatedEndTime_cutOffAtNextEntry() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            // end mistakenly set ~a day later than it should be
            session(id = 1, startMs = now - 10 * 3_600_000L, endMs = now + 8 * 3_600_000L, type = SleepType.NIGHT),
            session(id = 2, startMs = now - 9 * 3_600_000L, endMs = now - 7 * 3_600_000L, type = SleepType.NIGHT),
            session(id = 3, startMs = now - 3 * 3_600_000L, endMs = now - 2 * 3_600_000L, type = SleepType.NAP)
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // session 1 only counts until session 2 starts: 10h->7h ago asleep, then the 1h nap
        assertEquals(180f, stats.night.totalMinutes, 0.01f)
        assertEquals(240f, stats.combined.totalMinutes, 0.01f)
        // the real awake gap (7h ago -> 3h ago) is still a wake window
        assertEquals(1, stats.wakeWindow.sessionCount)
        assertEquals(240f, stats.wakeWindow.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_today_isFromLocalMidnightToNow() {
        val now = LocalDate.of(2024, 1, 8).atTime(15, 0).toInstant(UTC.rules.getOffset(java.time.Instant.now())).toEpochMilli()
        val midnight = LocalDate.of(2024, 1, 8).atStartOfDay(UTC).toInstant().toEpochMilli()
        val sessions = listOf(
            session(id = 1, startMs = midnight + 3_600_000L, endMs = midnight + 5_400_000L, type = SleepType.NAP), // in today
            session(id = 2, startMs = midnight - 3_600_000L, endMs = midnight - 1_800_000L, type = SleepType.NAP)  // yesterday, excluded
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.TODAY, now, UTC)
        assertEquals(1, stats.nap.sessionCount)
        assertEquals(30f, stats.nap.totalMinutes, 0.01f)
    }

    // ─── wake window ─────────────────────────────────────────────────────────

    @Test
    fun computeSleepStats_wakeWindow_isGapBetweenConsecutiveSessions() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 10 * 3_600_000L, endMs = now - 9 * 3_600_000L, type = SleepType.NAP),
            // 2 hour gap between session 1's end and session 2's start
            session(id = 2, startMs = now - 7 * 3_600_000L, endMs = now - 6 * 3_600_000L, type = SleepType.NAP)
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(1, stats.wakeWindow.sessionCount)
        assertEquals(120f, stats.wakeWindow.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_wakeWindow_noGapAfterMostRecentSession() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 10 * 3_600_000L, endMs = now - 9 * 3_600_000L, type = SleepType.NAP)
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // only one session -> no "next" session -> no wake window counted
        assertEquals(0, stats.wakeWindow.sessionCount)
        assertEquals(0f, stats.wakeWindow.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_wakeWindow_overlappingSessionsProduceNoNegativeGap() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 10 * 3_600_000L, endMs = now - 6 * 3_600_000L, type = SleepType.NAP),
            // overlaps session 1 entirely
            session(id = 2, startMs = now - 9 * 3_600_000L, endMs = now - 8 * 3_600_000L, type = SleepType.NAP)
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        assertEquals(0, stats.wakeWindow.sessionCount)
        assertEquals(0f, stats.wakeWindow.totalMinutes, 0.01f)
    }

    @Test
    fun computeSleepStats_wakeWindow_crossesMidnight_countsOnce() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = day1Start + 40 * 3_600_000L
        val sessions = listOf(
            session(id = 1, startMs = day1Start + 20 * 3_600_000L, endMs = day1Start + 22 * 3_600_000L, type = SleepType.NAP), // ends 10pm day1
            session(id = 2, startMs = day1Start + 26 * 3_600_000L, endMs = day1Start + 27 * 3_600_000L, type = SleepType.NAP)  // starts 2am day2
        )
        val stats = computeSleepStats(sessions, SleepStatsPeriod.LAST_7_DAYS, now, UTC)
        // one 4 hour gap (10pm-2am) -> one wake window, even though it crosses midnight
        assertEquals(1, stats.wakeWindow.sessionCount)
        assertEquals(240f, stats.wakeWindow.totalMinutes, 0.01f)
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

    // ─── periodStartMs: LAST_24_HOURS ───────────────────────────────────────

    @Test
    fun periodStartMs_last24Hours_isExactlyOneDayBeforeNow() {
        val now = 1_700_000_000_000L
        assertEquals(now - 86_400_000L, periodStartMs(SleepStatsPeriod.LAST_24_HOURS, emptyList(), now, UTC))
    }

    // ─── computeSleepHistogram ───────────────────────────────────────────────

    @Test
    fun computeSleepHistogram_singleDaySingleNap_marksOnlyOverlappingHours() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // Nap from 14:45 to 15:15 -> overlaps hour 14 and hour 15
        val nap = session(startMs = dayStart + 14 * 3_600_000L + 45 * 60_000L, endMs = dayStart + 15 * 3_600_000L + 15 * 60_000L)

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        assertEquals(24, histogram.hourlyMinutesAsleep.size)
        assertTrue("hour 14 should show >0 min", histogram.hourlyMinutesAsleep[14] > 0f)
        assertTrue("hour 15 should show >0 min", histogram.hourlyMinutesAsleep[15] > 0f)
        assertEquals(0f, histogram.hourlyMinutesAsleep[10], 0.01f)
    }

    @Test
    fun computeSleepHistogram_isDurationWeightedInMinutes() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        // 1 minute of overlap with hour 6, 15 minutes with hour 5
        val nap = session(startMs = dayStart + 5 * 3_600_000L + 45 * 60_000L, endMs = dayStart + 6 * 3_600_000L + 1 * 60_000L)

        // period is LAST_7_DAYS -> divided by 7 days -> averaged minutes, not raw minutes
        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)
        assertEquals(15f / 7f, histogram.hourlyMinutesAsleep[5], 0.01f)
        assertEquals(1f / 7f, histogram.hourlyMinutesAsleep[6], 0.01f)
    }

    @Test
    fun computeSleepHistogram_todayOrLast24Hours_isLiteralMinutesNotAveraged() {
        val dayStart = LocalDate.of(2024, 1, 8).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 10 * 3_600_000L // 2024-01-08 10:00
        val nap = session(startMs = dayStart + 5 * 3_600_000L, endMs = dayStart + 5 * 3_600_000L + 30 * 60_000L) // 30 min at hour 5

        val histogram = computeSleepHistogram(listOf(nap), SleepStatsPeriod.TODAY, SleepLens.NAP, now, UTC)
        // day count is clamped to 1.0 for a same-day period -> literal 30 minutes, not divided further
        assertEquals(30f, histogram.hourlyMinutesAsleep[5], 0.01f)
    }

    @Test
    fun computeSleepHistogram_lensFiltersByType() {
        val dayStart = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = dayStart + 2 * DAYS_MS_TEST
        val nap = session(id = 1, startMs = dayStart + 15 * 3_600_000L, endMs = dayStart + 16 * 3_600_000L, type = SleepType.NAP)
        val night = session(id = 2, startMs = dayStart + 21 * 3_600_000L, endMs = dayStart + 23 * 3_600_000L, type = SleepType.NIGHT)

        val napOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)
        assertEquals(0f, napOnly.hourlyMinutesAsleep[21], 0.01f)
        assertTrue(napOnly.hourlyMinutesAsleep[15] > 0f)

        val nightOnly = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NIGHT, now, UTC)
        assertEquals(0f, nightOnly.hourlyMinutesAsleep[15], 0.01f)
        assertTrue(nightOnly.hourlyMinutesAsleep[21] > 0f)

        val combined = computeSleepHistogram(listOf(nap, night), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(combined.hourlyMinutesAsleep[15] > 0f)
        assertTrue(combined.hourlyMinutesAsleep[21] > 0f)
    }

    @Test
    fun computeSleepHistogram_openSession_excluded() {
        val now = 1_700_000_000_000L
        val open = session(startMs = now - 1_800_000L, endMs = null)
        val histogram = computeSleepHistogram(listOf(open), SleepStatsPeriod.LAST_7_DAYS, SleepLens.COMBINED, now, UTC)
        assertTrue(histogram.hourlyMinutesAsleep.all { it == 0f })
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
        assertEquals(0f, histogram.hourlyMinutesAsleep[13], 0.01f)
        assertEquals(0f, histogram.hourlyMinutesAsleep[14], 0.01f)
        assertTrue("hour 15 (3pm) should be marked - in period", histogram.hourlyMinutesAsleep[15] > 0f)
        assertTrue("hour 16 (4pm) should be marked - in period", histogram.hourlyMinutesAsleep[16] > 0f)
    }

    // ─── computeDailySleepTotals ─────────────────────────────────────────────

    @Test
    fun computeDailySleepTotals_oneEntryPerDayIncludingEmptyDays() {
        val day1Start = LocalDate.of(2024, 1, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val now = day1Start + 3 * DAYS_MS_TEST // now is 2024-01-04, 00:00
        val nap = session(startMs = day1Start + 3_600_000L, endMs = day1Start + 3_600_000L + 30 * 60_000L) // 30 min on day 1

        val totals = computeDailySleepTotals(listOf(nap), SleepStatsPeriod.LAST_7_DAYS, SleepLens.NAP, now, UTC)

        // period start is 6 days before "now", so totals cover every day from periodStart's date to today
        assertTrue(totals.isNotEmpty())
        val day1Total = totals.find { it.date == LocalDate.of(2024, 1, 1) }
        assertEquals(30f, day1Total?.totalMinutes ?: -1f, 0.01f)
        // a day with no sleep is still present, with 0 minutes
        val emptyDay = totals.find { it.date == LocalDate.of(2024, 1, 2) }
        assertEquals(0f, emptyDay?.totalMinutes ?: -1f, 0.01f)
    }

    // ─── computeMonthlySleepAverages ─────────────────────────────────────────

    @Test
    fun computeMonthlySleepAverages_averagesOverDaysActuallyInWindow() {
        val now = LocalDate.of(2024, 6, 15).atStartOfDay(UTC).toInstant().toEpochMilli()
        // 60 minutes of nap sleep on each of 2 different days within June
        val juneDay1 = LocalDate.of(2024, 6, 1).atStartOfDay(UTC).toInstant().toEpochMilli()
        val juneDay2 = LocalDate.of(2024, 6, 2).atStartOfDay(UTC).toInstant().toEpochMilli()
        val sessions = listOf(
            session(id = 1, startMs = juneDay1 + 3_600_000L, endMs = juneDay1 + 2 * 3_600_000L, type = SleepType.NAP), // 60 min
            session(id = 2, startMs = juneDay2 + 3_600_000L, endMs = juneDay2 + 2 * 3_600_000L, type = SleepType.NAP)  // 60 min
        )

        val averages = computeMonthlySleepAverages(sessions, SleepLens.NAP, now, UTC)
        val june = averages.find { it.yearMonth == YearMonth.of(2024, 6) }
        assertTrue("June should be present", june != null)
        // 120 total minutes over 15 days-in-window (June 1st through June 15th) = 8 min/day
        assertEquals(120f / 15f, june!!.avgDailyMinutes, 0.01f)
    }

    @Test
    fun computeMonthlySleepAverages_noSessionsOutsideLast365Days() {
        val now = LocalDate.of(2024, 6, 15).atStartOfDay(UTC).toInstant().toEpochMilli()
        val tooOld = session(
            startMs = now - 400 * DAYS_MS_TEST,
            endMs = now - 400 * DAYS_MS_TEST + 3_600_000L,
            type = SleepType.NAP
        )
        val averages = computeMonthlySleepAverages(listOf(tooOld), SleepLens.NAP, now, UTC)
        assertTrue(averages.all { it.avgDailyMinutes == 0f })
    }

    // ─── computeLast24HourSleepAwake ─────────────────────────────────────────

    @Test
    fun computeLast24HourSleepAwake_closedSession_splitsIntoSleepAndAwake() {
        val now = 1_700_000_000_000L
        // 2 hours of sleep somewhere in the last 24h
        val sessions = listOf(session(startMs = now - 5 * 3_600_000L, endMs = now - 3 * 3_600_000L))

        val summary = computeLast24HourSleepAwake(sessions, now)

        assertEquals(120f, summary.sleepMinutes, 0.01f)
        assertEquals(24 * 60f - 120f, summary.awakeMinutes, 0.01f)
    }

    @Test
    fun computeLast24HourSleepAwake_openSession_countsElapsedTimeAsAsleep() {
        val now = 1_700_000_000_000L
        // still asleep, started 1 hour ago
        val sessions = listOf(session(startMs = now - 3_600_000L, endMs = null))

        val summary = computeLast24HourSleepAwake(sessions, now)

        assertEquals(60f, summary.sleepMinutes, 0.01f)
        assertEquals(24 * 60f - 60f, summary.awakeMinutes, 0.01f)
    }

    @Test
    fun computeLast24HourSleepAwake_sessionStartsBeforeWindow_clipsToWindow() {
        val now = 1_700_000_000_000L
        // started 30 hours ago (before the window), ended 20 hours ago
        val sessions = listOf(session(startMs = now - 30 * 3_600_000L, endMs = now - 20 * 3_600_000L))

        val summary = computeLast24HourSleepAwake(sessions, now)

        // only the last 4 hours of that session (24h ago -> 20h ago) fall in the window
        assertEquals(4 * 60f, summary.sleepMinutes, 0.01f)
    }

    @Test
    fun computeLast24HourSleepAwake_noSessions_allAwake() {
        val now = 1_700_000_000_000L
        val summary = computeLast24HourSleepAwake(emptyList(), now)
        assertEquals(0f, summary.sleepMinutes, 0.01f)
        assertEquals(24 * 60f, summary.awakeMinutes, 0.01f)
    }

    @Test
    fun currentSleepStatus_latestOpenSession_isAsleepSinceItsStart() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 5 * 3_600_000L, endMs = now - 4 * 3_600_000L),
            session(id = 2, startMs = now - 30 * 60_000L, endMs = null)
        )
        val status = currentSleepStatus(sessions, now)
        assertTrue(status.isAsleep)
        assertEquals(2L, status.openSession?.id)
        assertEquals(now - 30 * 60_000L, status.sinceMs)
    }

    @Test
    fun currentSleepStatus_olderUnfinishedSession_ignoredOnceNewerOneLogged() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 6 * 3_600_000L, endMs = null),                 // forgotten "Woke up"
            session(id = 2, startMs = now - 2 * 3_600_000L, endMs = now - 1 * 3_600_000L)  // newer, finished
        )
        val status = currentSleepStatus(sessions, now)
        assertEquals(false, status.isAsleep)
        assertEquals(now - 1 * 3_600_000L, status.sinceMs)
    }

    @Test
    fun currentSleepStatus_futureStart_ignored() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 2 * 3_600_000L, endMs = now - 1 * 3_600_000L),
            session(id = 2, startMs = now + 3_600_000L, endMs = null)
        )
        assertEquals(now - 1 * 3_600_000L, currentSleepStatus(sessions, now).sinceMs)
    }

    @Test
    fun currentSleepStatus_noSessions_awakeWithNoSince() {
        val status = currentSleepStatus(emptyList(), 1_700_000_000_000L)
        assertEquals(false, status.isAsleep)
        assertEquals(null, status.sinceMs)
    }

    @Test
    fun currentSleepStatus_futureEndTimeOnOlderEntry_doesNotGoNegative() {
        val now = 1_700_000_000_000L
        // the real-data bug: a night entry started ~43h ago with its end mistakenly set ~8h in the future,
        // plus a normal nap that ended 2h ago
        val sessions = listOf(
            session(id = 1, startMs = now - 43 * 3_600_000L, endMs = now + 8 * 3_600_000L, type = SleepType.NIGHT),
            session(id = 2, startMs = now - 3 * 3_600_000L, endMs = now - 2 * 3_600_000L)
        )
        val status = currentSleepStatus(sessions, now)
        // the nap is the latest-started entry, so baby is awake since it ended — not negative
        assertEquals(false, status.isAsleep)
        assertEquals(now - 2 * 3_600_000L, status.sinceMs)
        // and the bad entry stops counting once the nap starts, instead of filling all 24h
        val summary = computeLast24HourSleepAwake(sessions, now)
        assertEquals(22 * 60f, summary.sleepMinutes, 0.01f) // 24h ago -> 3h ago, then the 1h nap
    }

    @Test
    fun computeLast24HourSleepAwake_orphanedOpenSession_onlyCountsUntilNextSessionStarts() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 20 * 3_600_000L, endMs = null),                 // never ended
            session(id = 2, startMs = now - 18 * 3_600_000L, endMs = now - 17 * 3_600_000L) // a later nap
        )
        val summary = computeLast24HourSleepAwake(sessions, now)
        // orphan: 20h ago -> 18h ago (2h), then the nap (1h) = 3h, not 20h
        assertEquals(180f, summary.sleepMinutes, 0.01f)
    }

    @Test
    fun computeLast24HourSleepAwake_overlappingSessions_notDoubleCounted() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 5 * 3_600_000L, endMs = now - 3 * 3_600_000L),
            session(id = 2, startMs = now - 4 * 3_600_000L, endMs = now - 2 * 3_600_000L)
        )
        assertEquals(180f, computeLast24HourSleepAwake(sessions, now).sleepMinutes, 0.01f)
    }

    @Test
    fun computeLast24HourSleepAwake_alwaysSumsToTwentyFourHours() {
        val now = 1_700_000_000_000L
        val sessions = listOf(
            session(id = 1, startMs = now - 10 * 3_600_000L, endMs = now - 8 * 3_600_000L),
            session(id = 2, startMs = now - 4 * 3_600_000L, endMs = now - 2 * 3_600_000L)
        )
        val summary = computeLast24HourSleepAwake(sessions, now)
        assertEquals(24 * 60f, summary.sleepMinutes + summary.awakeMinutes, 0.01f)
    }
}
