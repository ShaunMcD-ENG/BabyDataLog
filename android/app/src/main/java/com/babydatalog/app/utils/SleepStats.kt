package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val DAY_MS = 86_400_000L

enum class SleepStatsPeriod { TODAY, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL_TIME }

/** One session's contribution to a single local calendar day, after splitting at local midnight. */
data class DaySlice(val date: LocalDate, val startMs: Long, val endMs: Long, val type: SleepType)

/**
 * Splits a raw [start, end) range into per-local-day portions at midnight. Shared core
 * behind [splitAcrossDays] and the wake-window gap computation in [computeSleepStats],
 * neither of which needs a [SleepType] attached to the pieces.
 */
private fun splitRangeAcrossDays(start: Long, end: Long, zone: ZoneId): List<Triple<LocalDate, Long, Long>> {
    if (end <= start) return emptyList()
    val slices = mutableListOf<Triple<LocalDate, Long, Long>>()
    var cursorMs = start
    while (cursorMs < end) {
        val cursorDate = Instant.ofEpochMilli(cursorMs).atZone(zone).toLocalDate()
        val nextMidnightMs = cursorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sliceEndMs = minOf(end, nextMidnightMs)
        slices += Triple(cursorDate, cursorMs, sliceEndMs)
        cursorMs = sliceEndMs
    }
    return slices
}

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
    return splitRangeAcrossDays(session.startTimeMs, end, zone)
        .map { (date, sliceStart, sliceEnd) -> DaySlice(date, sliceStart, sliceEnd, session.type) }
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
    SleepStatsPeriod.TODAY -> Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    SleepStatsPeriod.LAST_24_HOURS -> nowMs - DAY_MS
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

data class SleepStats(
    val nap: SleepLensStats,
    val night: SleepLensStats,
    val wakeWindow: SleepLensStats,
    val combined: SleepLensStats
)

private fun lensStats(durations: List<Pair<Long, Long>>, dayCount: Float, includeCount: Boolean): SleepLensStats {
    val totalMinutes = durations.sumOf { (it.second - it.first) / 60_000.0 }.toFloat()
    if (!includeCount) {
        return SleepLensStats(
            sessionCount = null,
            totalMinutes = totalMinutes,
            avgMinutesPerDay = totalMinutes / dayCount,
            avgSessionsPerDay = null,
            avgSessionDurationMinutes = null
        )
    }
    val count = durations.size
    return SleepLensStats(
        sessionCount = count,
        totalMinutes = totalMinutes,
        avgMinutesPerDay = totalMinutes / dayCount,
        avgSessionsPerDay = count / dayCount,
        avgSessionDurationMinutes = if (count > 0) totalMinutes / count else null
    )
}

/**
 * Per-lens (Nap / Night / Wake Window / Combined) totals for the selected period. Open
 * sessions are excluded entirely (spec §5.2 / Q19); overnight sessions are split at
 * local midnight first (spec §5.1) so a session that only partly overlaps the period
 * contributes only its overlapping portion. Wake Window is the gap between one closed
 * session's end and the next closed session's start (sessions sorted by start time) —
 * the stretch after the most-recent session's end has no "next" session yet, so it's an
 * open wake window and is excluded, mirroring how an open sleep session is excluded.
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

    fun clippedDurations(rawSlices: List<Triple<LocalDate, Long, Long>>): List<Pair<Long, Long>> =
        rawSlices.mapNotNull { (_, sliceStart, sliceEnd) ->
            val clippedStart = maxOf(sliceStart, startMs)
            val clippedEnd = minOf(sliceEnd, nowMs)
            if (clippedStart < clippedEnd) clippedStart to clippedEnd else null
        }

    fun durationsFor(type: SleepType) = clippedDurations(
        closed.filter { it.type == type }.flatMap { splitRangeAcrossDays(it.startTimeMs, it.endTimeMs!!, zone) }
    )

    val napDurations = durationsFor(SleepType.NAP)
    val nightDurations = durationsFor(SleepType.NIGHT)
    val allDurations = clippedDurations(closed.flatMap { splitRangeAcrossDays(it.startTimeMs, it.endTimeMs!!, zone) })
    val wakeWindowDurations = clippedDurations(
        closed.sortedBy { it.startTimeMs }
            .zipWithNext()
            .flatMap { (prev, next) -> splitRangeAcrossDays(prev.endTimeMs!!, next.startTimeMs, zone) }
    )

    return SleepStats(
        nap = lensStats(napDurations, dayCount, includeCount = true),
        night = lensStats(nightDurations, dayCount, includeCount = true),
        wakeWindow = lensStats(wakeWindowDurations, dayCount, includeCount = true),
        combined = lensStats(allDurations, dayCount, includeCount = false)
    )
}

enum class SleepLens { NAP, NIGHT, COMBINED }

/**
 * hourlyMinutesAsleep has 24 entries, index = local hour-of-day (0..23), value 0..60 —
 * minutes asleep in that hour, averaged across the days in the period (spec-extension:
 * for TODAY/LAST_24_HOURS the "period" spans at most one day, so dividing by a day count
 * clamped to at least 1.0 yields the literal minute count rather than an average).
 */
data class SleepHistogram(val hourlyMinutesAsleep: List<Float>)

private fun clippedLensSlices(
    closed: List<SleepSession>,
    lens: SleepLens,
    startMs: Long,
    nowMs: Long,
    zone: ZoneId
): List<DaySlice> = closed.flatMap { splitAcrossDays(it, zone) }
    .mapNotNull { slice ->
        val clippedStart = maxOf(slice.startMs, startMs)
        val clippedEnd = minOf(slice.endMs, nowMs)
        if (clippedStart < clippedEnd) slice.copy(startMs = clippedStart, endMs = clippedEnd) else null
    }
    .filter {
        when (lens) {
            SleepLens.COMBINED -> true
            SleepLens.NAP -> it.type == SleepType.NAP
            SleepLens.NIGHT -> it.type == SleepType.NIGHT
        }
    }

/**
 * For each local hour-of-day, the average number of minutes (0..60) the baby was recorded
 * asleep (for the given lens) during that hour, averaged over the days in the period.
 * Day-split per §5.1 first, so an overnight session's minutes land on the correct calendar
 * day. Sessions are clipped to the period boundaries to exclude time outside [startMs, nowMs].
 */
fun computeSleepHistogram(
    sessions: List<SleepSession>,
    period: SleepStatsPeriod,
    lens: SleepLens,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): SleepHistogram {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(period, closed, nowMs, zone)
    val slices = clippedLensSlices(closed, lens, startMs, nowMs, zone)

    val minutesByHour = DoubleArray(24)
    for (slice in slices) {
        var cursorMs = slice.startMs
        while (cursorMs < slice.endMs) {
            val zdt = Instant.ofEpochMilli(cursorMs).atZone(zone)
            val nextHourMs = zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            val segmentEndMs = minOf(slice.endMs, nextHourMs)
            minutesByHour[zdt.hour] += (segmentEndMs - cursorMs) / 60_000.0
            cursorMs = segmentEndMs
        }
    }

    val dayCount = ((nowMs - startMs) / DAY_MS.toDouble()).coerceAtLeast(1.0)
    return SleepHistogram((0..23).map { h -> (minutesByHour[h] / dayCount).toFloat() })
}

/** One calendar day's total sleep minutes (for the given lens) within a stats period. */
data class DailySleepTotal(val date: LocalDate, val totalMinutes: Float)

/**
 * Total sleep minutes per calendar day across the period, one entry per day from the
 * period's start date to today (inclusive), even for days with no recorded sleep — so a
 * day-by-day chart doesn't silently skip gaps.
 */
fun computeDailySleepTotals(
    sessions: List<SleepSession>,
    period: SleepStatsPeriod,
    lens: SleepLens,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): List<DailySleepTotal> {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(period, closed, nowMs, zone)
    val slices = clippedLensSlices(closed, lens, startMs, nowMs, zone)

    val minutesByDate = sortedMapOf<LocalDate, Float>()
    val startDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    var d = startDate
    while (!d.isAfter(endDate)) {
        minutesByDate[d] = 0f
        d = d.plusDays(1)
    }
    for (slice in slices) {
        minutesByDate[slice.date] = (minutesByDate[slice.date] ?: 0f) + (slice.endMs - slice.startMs) / 60_000f
    }
    return minutesByDate.map { (date, minutes) -> DailySleepTotal(date, minutes) }
}

/** One calendar month's average daily sleep minutes (for the given lens) within the last year. */
data class MonthlySleepAverage(val yearMonth: YearMonth, val avgDailyMinutes: Float)

/**
 * Average daily sleep minutes per calendar month over the last 365 days, one entry per
 * month touched by that window. A partial month (the first or the current one) is
 * averaged only over the days actually inside the window, not the whole month.
 */
fun computeMonthlySleepAverages(
    sessions: List<SleepSession>,
    lens: SleepLens,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): List<MonthlySleepAverage> {
    val closed = sessions.filter { it.endTimeMs != null }
    val startMs = periodStartMs(SleepStatsPeriod.LAST_365_DAYS, closed, nowMs, zone)
    val slices = clippedLensSlices(closed, lens, startMs, nowMs, zone)

    val minutesByDate = HashMap<LocalDate, Float>()
    for (slice in slices) {
        minutesByDate[slice.date] = (minutesByDate[slice.date] ?: 0f) + (slice.endMs - slice.startMs) / 60_000f
    }

    val dayCountByMonth = LinkedHashMap<YearMonth, Int>()
    val startDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    var d = startDate
    while (!d.isAfter(endDate)) {
        val ym = YearMonth.from(d)
        dayCountByMonth[ym] = (dayCountByMonth[ym] ?: 0) + 1
        d = d.plusDays(1)
    }

    val minutesByMonth = HashMap<YearMonth, Float>()
    for ((date, minutes) in minutesByDate) {
        val ym = YearMonth.from(date)
        if (ym in dayCountByMonth) minutesByMonth[ym] = (minutesByMonth[ym] ?: 0f) + minutes
    }

    return dayCountByMonth.map { (ym, dayCount) -> MonthlySleepAverage(ym, (minutesByMonth[ym] ?: 0f) / dayCount) }
}

/** Total minutes asleep vs. awake over a trailing window (used for the live status card). */
data class SleepAwakeSummary(val sleepMinutes: Float, val awakeMinutes: Float)

/**
 * Sleep/awake split over the trailing 24 hours. Unlike [computeSleepStats], an open
 * (in-progress) session is NOT excluded — it's treated as ending "now", so the live
 * status card's totals reflect the baby's current state instead of only sessions that
 * have already closed. Awake is simply the remainder of the 24-hour window.
 */
fun computeLast24HourSleepAwake(
    sessions: List<SleepSession>,
    nowMs: Long = System.currentTimeMillis()
): SleepAwakeSummary {
    val windowStart = nowMs - DAY_MS
    val windowMinutes = DAY_MS / 60_000f
    val sleepMinutes = sessions.sumOf { session ->
        val end = session.endTimeMs ?: nowMs
        val clippedStart = maxOf(session.startTimeMs, windowStart)
        val clippedEnd = minOf(end, nowMs)
        if (clippedStart < clippedEnd) (clippedEnd - clippedStart) / 60_000.0 else 0.0
    }.toFloat().coerceIn(0f, windowMinutes)
    return SleepAwakeSummary(sleepMinutes, windowMinutes - sleepMinutes)
}
