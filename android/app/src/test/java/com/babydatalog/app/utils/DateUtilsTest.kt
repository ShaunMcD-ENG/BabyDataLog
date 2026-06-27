package com.babydatalog.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DateUtilsTest {

    // ─── formatDuration ───────────────────────────────────────────────────────

    @Test
    fun formatDuration_null_returnsDoubleDash() {
        assertEquals("--", formatDuration(null))
    }

    @Test
    fun formatDuration_zero_returnsZeroMin() {
        assertEquals("0 min", formatDuration(0f))
    }

    @Test
    fun formatDuration_belowOneHour_returnsMinutes() {
        assertEquals("32 min", formatDuration(32f))
    }

    @Test
    fun formatDuration_seventyFiveMinutes_returnsOneHourFifteenMinutes() {
        assertEquals("1h 15m", formatDuration(75f))
    }

    @Test
    fun formatDuration_exactHours_returnsHoursOnly() {
        // Implementation returns "2h" when remainingMinutes == 0, not "2h 0m"
        assertEquals("2h", formatDuration(120f))
    }

    // ─── todayStartMs ─────────────────────────────────────────────────────────

    @Test
    fun todayStartMs_hasTimeAtMidnight() {
        val startMs = todayStartMs()
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault())
        assertEquals(0, ldt.hour)
        assertEquals(0, ldt.minute)
        assertEquals(0, ldt.second)
    }

    // ─── todayEndMs ───────────────────────────────────────────────────────────

    @Test
    fun todayEndMs_hasTimeAt2359() {
        val endMs = todayEndMs()
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault())
        assertEquals(23, ldt.hour)
        assertEquals(59, ldt.minute)
    }

    @Test
    fun todayStartMs_isBeforeTodayEndMs() {
        assertTrue(todayStartMs() < todayEndMs())
    }
}
