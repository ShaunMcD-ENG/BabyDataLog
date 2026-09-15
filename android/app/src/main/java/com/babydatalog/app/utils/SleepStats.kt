package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MS = 86_400_000L

enum class SleepStatsPeriod { LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL_TIME }

/** One session's contribution to a single local calendar day, after splitting at local midnight. */
data class DaySlice(val date: LocalDate, val startMs: Long, val endMs: Long, val type: SleepType)

// A session starting in the evening/overnight window defaults to NIGHT; anything
// else defaults to NAP. This is only ever a pre-selected default — the type field
// is always user-visible and overridable (spec §4.3).
fun defaultSleepTypeForStart(startTimeMs: Long, zone: ZoneId = ZoneId.systemDefault()): SleepType {
    val hour = Instant.ofEpochMilli(startTimeMs).atZone(zone).hour
    return if (hour >= 18 || hour < 6) SleepType.NIGHT else SleepType.NAP
}

/**
 * Splits a closed session ([startTimeMs, endTimeMs)) into per-local-day portions
 * at midnight, so a session spanning two calendar days contributes to both day's
 * totals and hour-buckets independently (spec §5.1). An open session (no end yet)
 * returns nothing — callers must exclude open sessions from stats until they end
 * (spec §5.2's "open session excluded" rule is enforced by never calling this on one).
 */
fun splitAcrossDays(session: SleepSession, zone: ZoneId = ZoneId.systemDefault()): List<DaySlice> {
    val end = session.endTimeMs ?: return emptyList()
    val start = session.startTimeMs
    if (end <= start) return emptyList()

    val slices = mutableListOf<DaySlice>()
    var cursorMs = start
    while (cursorMs < end) {
        val cursorDate = Instant.ofEpochMilli(cursorMs).atZone(zone).toLocalDate()
        val nextMidnightMs = cursorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sliceEndMs = minOf(end, nextMidnightMs)
        slices += DaySlice(cursorDate, cursorMs, sliceEndMs, session.type)
        cursorMs = sliceEndMs
    }
    return slices
}

/**
 * Start of the rolling window for a stats period. ALL_TIME spans from the
 * earliest recorded session's start to now (spec §5.4); the others are fixed
 * rolling windows ending "now", not calendar-boundary windows.
 */
fun periodStartMs(
    period: SleepStatsPeriod,
    sessions: List<SleepSession>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long = when (period) {
    SleepStatsPeriod.LAST_7_DAYS -> nowMs - 7 * DAY_MS
    SleepStatsPeriod.LAST_30_DAYS -> nowMs - 30 * DAY_MS
    SleepStatsPeriod.LAST_365_DAYS -> nowMs - 365 * DAY_MS
    SleepStatsPeriod.ALL_TIME -> sessions.minOfOrNull { it.startTimeMs } ?: nowMs
}

data class SleepLensStats(
    val sessionCount: Int?,             // null for the Combined lens — summing split-session counts across Nap+Night double-counts, so Combined only reports duration (spec §5.2)
    val totalMinutes: Float,
    val avgMinutesPerDay: Float,
    val avgSessionsPerDay: Float?,
    val avgSessionDurationMinutes: Float?
)

data class SleepStats(val nap: SleepLensStats, val night: SleepLensStats, val combined: SleepLensStats)

private fun lensStats(slices: List<DaySlice>, dayCount: Float, includeCount: Boolean): SleepLensStats {
    val totalMinutes = slices.sumOf { (it.endMs - it.startMs) / 60_000.0 }.toFloat()
    if (!includeCount) {
        return SleepLensStats(
            sessionCount = null,
            totalMinutes = totalMinutes,
            avgMinutesPerDay = totalMinutes / dayCount,
            avgSessionsPerDay = null,
            avgSessionDurationMinutes = null
        )
    }
    val count = slices.size
    return SleepLensStats(
        sessionCount = count,
        totalMinutes = totalMinutes,
        avgMinutesPerDay = totalMinutes / dayCount,
        avgSessionsPerDay = count / dayCount,
        avgSessionDurationMinutes = if (count > 0) totalMinutes / count else null
    )
}

/**
 * Per-lens (Nap / Night / Combined) totals for the selected period. Open
 * sessions are excluded entirely (spec §5.2 / Q19); overnight sessions are
 * split at local midnight first (spec §5.1) so a session that only partly
 * overlaps the period contributes only its overlapping portion.
 */
fun computeSleepStats(
    sessions: List<SleepSession>,
    period: SleepStatsPeriod,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): SleepStats {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(period, closed, nowMs, zone)
    val dayCount = ((nowMs - startMs) / DAY_MS.toDouble()).coerceAtLeast(1.0).toFloat()

    val allSlices = closed.flatMap { splitAcrossDays(it, zone) }
        .filter { it.startMs < nowMs && it.endMs > startMs }

    val napSlices = allSlices.filter { it.type == SleepType.NAP }
    val nightSlices = allSlices.filter { it.type == SleepType.NIGHT }

    return SleepStats(
        nap = lensStats(napSlices, dayCount, includeCount = true),
        night = lensStats(nightSlices, dayCount, includeCount = true),
        combined = lensStats(allSlices, dayCount, includeCount = false)
    )
}
