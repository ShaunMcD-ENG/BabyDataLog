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
 * behind [splitAcrossDays] and the per-day/per-hour chart slicing, the latter of which
 * doesn't need a [SleepType] attached to the pieces.
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
 * Unions overlapping or touching [start, end) ranges, so duplicate or overlapping
 * entries (e.g. a session re-logged by the other parent) aren't counted twice.
 * Empty/negative ranges are dropped.
 */
internal fun mergeIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
    val merged = mutableListOf<Pair<Long, Long>>()
    for ((start, end) in intervals.filter { it.second > it.first }.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && start <= last.second) {
            merged[merged.lastIndex] = last.first to maxOf(last.second, end)
        } else {
            merged += start to end
        }
    }
    return merged
}

/**
 * Each session's effective [start, end) where the later-started entry wins: a session is
 * cut off at the next session's start, so one entry with a wrong (e.g. next-day) end time
 * can't swallow everything logged after it, and an unfinished one only runs until the next
 * entry begins (or until now, if it's the latest). Ends are capped at [nowMs]; sessions
 * starting in the future are dropped as entry mistakes.
 */
private fun effectiveRanges(sessions: List<SleepSession>, nowMs: Long): List<Pair<SleepSession, Pair<Long, Long>>> {
    val sorted = sessions.filter { it.startTimeMs <= nowMs }.sortedBy { it.startTimeMs }
    return sorted.mapIndexed { i, s ->
        val nextStart = sorted.getOrNull(i + 1)?.startTimeMs ?: Long.MAX_VALUE
        s to (s.startTimeMs to minOf(s.endTimeMs ?: nowMs, nextStart, nowMs))
    }
}

/**
 * Closed sessions' merged sleep ranges (see [effectiveRanges]), optionally only those of
 * [type]. Cut-offs are decided across all closed sessions, so a night entry still bounds
 * an overlapping nap even when only naps are being totalled.
 */
private fun closedSleepIntervals(sessions: List<SleepSession>, nowMs: Long, type: SleepType? = null): List<Pair<Long, Long>> =
    mergeIntervals(
        effectiveRanges(sessions.filter { it.endTimeMs != null }, nowMs)
            .filter { (s, _) -> type == null || s.type == type }
            .map { it.second }
    )

private fun List<Pair<Long, Long>>.clippedTo(startMs: Long, endMs: Long): List<Pair<Long, Long>> =
    mapNotNull { (s, e) ->
        val clippedStart = maxOf(s, startMs)
        val clippedEnd = minOf(e, endMs)
        if (clippedStart < clippedEnd) clippedStart to clippedEnd else null
    }

/**
 * The live asleep/awake state, decided by the most recently *started* session only, so
 * an older session left open (or with a bad end time) can't override what's happening
 * now. Asleep → [sinceMs] is the latest session's start; awake → its end. [sinceMs] is
 * null when there are no sessions at all. [openSession] is the session "Woke up" ends.
 */
data class CurrentSleepStatus(val openSession: SleepSession?, val sinceMs: Long?) {
    val isAsleep: Boolean get() = openSession != null
}

fun currentSleepStatus(sessions: List<SleepSession>, nowMs: Long = System.currentTimeMillis()): CurrentSleepStatus {
    // Sessions starting in the future are entry mistakes — ignore them rather than
    // letting them become "the latest".
    val latest = sessions.filter { it.startTimeMs <= nowMs }.maxByOrNull { it.startTimeMs }
        ?: return CurrentSleepStatus(openSession = null, sinceMs = null)
    val end = latest.endTimeMs
    return if (end == null) {
        CurrentSleepStatus(openSession = latest, sinceMs = latest.startTimeMs)
    } else {
        CurrentSleepStatus(openSession = null, sinceMs = minOf(end, nowMs))
    }
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
    val nightWakeWindow: SleepLensStats,
    val dayWakeWindow: SleepLensStats,
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
 * sessions are excluded entirely (spec §5.2 / Q19); a session that only partly overlaps
 * the period contributes only its overlapping portion. Overlapping entries are resolved
 * first (see [effectiveRanges]) so duplicates don't double-count, and an overnight session counts as ONE session
 * (not one per calendar day) so "Average Length" reflects real session lengths — midnight
 * splitting only matters for the per-day charts, not for totals over a continuous window.
 * Wake windows are split into Night (awake between two night sleeps, e.g. overnight feeds)
 * and Day (morning wake-up → naps → bedtime); see [computeWakeWindows].
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

    val allBlocks = closedSleepIntervals(closed, nowMs)
    fun durationsFor(type: SleepType) = closedSleepIntervals(closed, nowMs, type).clippedTo(startMs, nowMs)

    val napDurations = durationsFor(SleepType.NAP)
    val nightDurations = durationsFor(SleepType.NIGHT)
    val allDurations = allBlocks.clippedTo(startMs, nowMs)
    val wakeWindows = computeWakeWindows(sessions, startMs, nowMs, zone, dayCount)

    return SleepStats(
        nap = lensStats(napDurations, dayCount, includeCount = true),
        night = lensStats(nightDurations, dayCount, includeCount = true),
        nightWakeWindow = wakeWindows.night,
        dayWakeWindow = wakeWindows.day,
        combined = lensStats(allDurations, dayCount, includeCount = false)
    )
}

/** A run of merged sleep, remembering the type it started and finished as (for wake-window classification). */
private data class SleepBlock(val startMs: Long, val endMs: Long, val startType: SleepType, val endType: SleepType)

/**
 * All sleep — INCLUDING the in-progress session (running until now) — resolved with
 * [effectiveRanges] and merged into contiguous blocks. The open session matters here: it's
 * what closes the wake window before it, even though its own length isn't final yet.
 */
private fun sleepBlocks(sessions: List<SleepSession>, nowMs: Long): List<SleepBlock> {
    val blocks = mutableListOf<SleepBlock>()
    for ((session, range) in effectiveRanges(sessions, nowMs)) {
        val (start, end) = range
        if (end <= start) continue
        val last = blocks.lastOrNull()
        if (last != null && start <= last.endMs) {
            if (end > last.endMs) blocks[blocks.lastIndex] = last.copy(endMs = end, endType = session.type)
        } else {
            blocks += SleepBlock(start, end, session.type, session.type)
        }
    }
    return blocks
}

/** True if a local 12:00 falls inside (startMs, endMs) — i.e. the gap covers the daytime. */
private fun spansLocalNoon(startMs: Long, endMs: Long, zone: ZoneId): Boolean {
    var noon = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().atTime(12, 0).atZone(zone)
    if (noon.toInstant().toEpochMilli() <= startMs) noon = noon.plusDays(1)
    return noon.toInstant().toEpochMilli() < endMs
}

/**
 * A stretch awake between two sleeps. [isNight] = between two night sleeps. [isOngoing] = the
 * baby is awake right now: it runs from the latest sleep's end until "now" (no next sleep yet).
 */
data class WakeWindow(val startMs: Long, val endMs: Long, val isNight: Boolean, val isOngoing: Boolean = false)

/**
 * Every wake window, oldest first. A window between two sleeps is closed once the next sleep
 * has STARTED — so the gap before an in-progress nap/night counts straight away. If the baby is
 * awake now (latest sleep has ended), the time since then is included as an ongoing window that
 * grows until the next sleep is logged.
 *
 * Night = awake between two night sleeps (overnight feeds/wakes). Everything else is Day: the
 * first window after the last night sleep, the windows between naps, and the one before
 * bedtime. A night→night gap that covers local noon is also Day — that's a whole day with no
 * naps logged, not an overnight wake. The ongoing window is always Day: Night strictly means
 * BETWEEN two night sleeps, so it can only become Night once the next sleep is logged as one
 * (no clock-based guessing — the user decides what's a night sleep).
 */
fun wakeWindows(sessions: List<SleepSession>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<WakeWindow> {
    val blocks = sleepBlocks(sessions, nowMs)
    val closed = blocks.zipWithNext().map { (prev, next) ->
        val night = prev.endType == SleepType.NIGHT && next.startType == SleepType.NIGHT &&
            !spansLocalNoon(prev.endMs, next.startMs, zone)
        WakeWindow(prev.endMs, next.startMs, night)
    }
    val last = blocks.lastOrNull() ?: return closed
    if (last.endMs >= nowMs) return closed // asleep right now — no ongoing wake window
    val ongoing = WakeWindow(
        startMs = last.endMs,
        endMs = nowMs,
        isNight = false,
        isOngoing = true
    )
    return closed + ongoing
}

data class WakeWindowStats(val night: SleepLensStats, val day: SleepLensStats)

/**
 * Night/Day wake-window stats for windows that ENDED within [startMs, nowMs] — including the
 * ongoing one if the baby is awake now (it "ends" at now). Each window is counted whole (not clipped at the period start), so "Average Length" reflects real window
 * lengths instead of being dragged down by one cut in half at the boundary.
 */
fun computeWakeWindows(
    sessions: List<SleepSession>,
    startMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    dayCount: Float = ((nowMs - startMs) / DAY_MS.toDouble()).coerceAtLeast(1.0).toFloat()
): WakeWindowStats {
    val inPeriod = wakeWindows(sessions, nowMs, zone).filter { it.endMs in (startMs + 1)..nowMs }
    val (night, day) = inPeriod.partition { it.isNight }
    return WakeWindowStats(
        night = lensStats(night.map { it.startMs to it.endMs }, dayCount, includeCount = true),
        day = lensStats(day.map { it.startMs to it.endMs }, dayCount, includeCount = true)
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

/**
 * The lens's closed sleep, with overlaps resolved (so they don't double-count), clipped to
 * [startMs, nowMs], then split at local midnight (spec §5.1) into (date, start, end) slices
 * for the per-day/per-hour charts.
 */
private fun clippedLensSlices(
    closed: List<SleepSession>,
    lens: SleepLens,
    startMs: Long,
    nowMs: Long,
    zone: ZoneId
): List<Triple<LocalDate, Long, Long>> {
    val lensType = when (lens) {
        SleepLens.COMBINED -> null
        SleepLens.NAP -> SleepType.NAP
        SleepLens.NIGHT -> SleepType.NIGHT
    }
    return closedSleepIntervals(closed, nowMs, lensType).clippedTo(startMs, nowMs)
        .flatMap { (s, e) -> splitRangeAcrossDays(s, e, zone) }
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
    for ((_, sliceStart, sliceEnd) in slices) {
        var cursorMs = sliceStart
        while (cursorMs < sliceEnd) {
            val zdt = Instant.ofEpochMilli(cursorMs).atZone(zone)
            val nextHourMs = zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            val segmentEndMs = minOf(sliceEnd, nextHourMs)
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
    for ((date, sliceStart, sliceEnd) in slices) {
        minutesByDate[date] = (minutesByDate[date] ?: 0f) + (sliceEnd - sliceStart) / 60_000f
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
    for ((date, sliceStart, sliceEnd) in slices) {
        minutesByDate[date] = (minutesByDate[date] ?: 0f) + (sliceEnd - sliceStart) / 60_000f
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
 * have already closed. Any session — including an older one left open, or one with a bad
 * end time — only counts up to the next session's start (see [effectiveRanges]), so a
 * forgotten "Woke up" or a mis-dated entry can't swallow the whole window.
 * Awake is simply the remainder of the 24-hour window.
 */
fun computeLast24HourSleepAwake(
    sessions: List<SleepSession>,
    nowMs: Long = System.currentTimeMillis()
): SleepAwakeSummary {
    val windowStart = nowMs - DAY_MS
    val windowMinutes = DAY_MS / 60_000f
    val sleepMinutes = mergeIntervals(effectiveRanges(sessions, nowMs).map { it.second }).clippedTo(windowStart, nowMs)
        .sumOf { (s, e) -> (e - s) / 60_000.0 }.toFloat().coerceIn(0f, windowMinutes)
    return SleepAwakeSummary(sleepMinutes, windowMinutes - sleepMinutes)
}

/** Distinct sleep blocks and total minutes asleep within [startMs, endMs), closed sessions only. */
data class SleepRangeTotals(val sessionCount: Int, val totalMinutes: Float)

/**
 * Used by the Summary screen's sleep card. Overlapping entries are resolved first and an
 * overnight session counts once (not once per calendar day it touches); only the portion
 * inside the range contributes minutes.
 */
fun computeSleepTotalsInRange(
    sessions: List<SleepSession>,
    startMs: Long,
    endMs: Long,
    nowMs: Long = System.currentTimeMillis()
): SleepRangeTotals {
    val blocks = closedSleepIntervals(sessions, nowMs).clippedTo(startMs, endMs)
    return SleepRangeTotals(blocks.size, blocks.sumOf { (s, e) -> (e - s) / 60_000.0 }.toFloat())
}
