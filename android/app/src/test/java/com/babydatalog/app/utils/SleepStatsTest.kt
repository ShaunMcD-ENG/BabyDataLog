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
}
