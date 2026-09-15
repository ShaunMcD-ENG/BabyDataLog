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
}
