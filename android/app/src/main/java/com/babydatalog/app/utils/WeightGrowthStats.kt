package com.babydatalog.app.utils

import com.babydatalog.app.data.database.entity.GrowthMeasurement

data class WeightPeriodStat(
    val label: String,
    val netChangeGrams: Int?,
    val avgPerDayGrams: Float?
)

data class WeightGrowthStats(
    val netChangeLastWeekGrams: Int?,
    val netChangeLastMonthGrams: Int?,
    val weeklyStats: List<WeightPeriodStat>,
    val monthlyStats: List<WeightPeriodStat>,
    val avgPerDayLastWeekGrams: Float?,
    val avgPerDayLastMonthGrams: Float?,
    val avgPerDayLast3MonthsGrams: Float?,
    val avgPerDayAllTimeGrams: Float?
)

private const val DAY_MS = 86_400_000L

private fun weekLabel(i: Int): String =
    if (i == 0) "Last 7 Days" else "${i * 7 + 1}–${(i + 1) * 7} Days Ago"

private fun monthLabel(i: Int): String =
    if (i == 0) "Last 30 Days" else "${i * 30 + 1}–${(i + 1) * 30} Days Ago"

// weights must be sorted ascending by timestampMs and contain only records with a non-null weight.
private fun periodStat(label: String, weights: List<GrowthMeasurement>, windowStartMs: Long, windowEndMs: Long): WeightPeriodStat {
    val startAnchor = weights.lastOrNull { it.timestampMs <= windowStartMs }
        ?: weights.firstOrNull { it.timestampMs in windowStartMs..windowEndMs }
    val endAnchor = weights.lastOrNull { it.timestampMs <= windowEndMs }
    if (startAnchor == null || endAnchor == null || startAnchor.id == endAnchor.id) {
        return WeightPeriodStat(label, null, null)
    }
    val netChange = endAnchor.weightGrams!! - startAnchor.weightGrams!!
    val daySpan = ((endAnchor.timestampMs - startAnchor.timestampMs) / DAY_MS.toDouble()).coerceAtLeast(1.0)
    return WeightPeriodStat(label, netChange, (netChange / daySpan).toFloat())
}

fun computeWeightGrowthStats(
    measurements: List<GrowthMeasurement>,
    nowMs: Long = System.currentTimeMillis()
): WeightGrowthStats {
    val weights = measurements.filter { it.weightGrams != null }.sortedBy { it.timestampMs }

    if (weights.size < 2) {
        return WeightGrowthStats(
            netChangeLastWeekGrams = null,
            netChangeLastMonthGrams = null,
            weeklyStats = (0 until 4).map { WeightPeriodStat(weekLabel(it), null, null) },
            monthlyStats = (0 until 3).map { WeightPeriodStat(monthLabel(it), null, null) },
            avgPerDayLastWeekGrams = null,
            avgPerDayLastMonthGrams = null,
            avgPerDayLast3MonthsGrams = null,
            avgPerDayAllTimeGrams = null
        )
    }

    val lastWeek = periodStat(weekLabel(0), weights, nowMs - 7 * DAY_MS, nowMs)
    val lastMonth = periodStat(monthLabel(0), weights, nowMs - 30 * DAY_MS, nowMs)
    val last3Months = periodStat("Last 90 Days", weights, nowMs - 90 * DAY_MS, nowMs)

    val weeklyStats = (0 until 4).map { i ->
        periodStat(weekLabel(i), weights, nowMs - (i + 1) * 7 * DAY_MS, nowMs - i * 7 * DAY_MS)
    }

    val monthlyStats = (0 until 3).map { i ->
        periodStat(monthLabel(i), weights, nowMs - (i + 1) * 30 * DAY_MS, nowMs - i * 30 * DAY_MS)
    }

    val first = weights.first()
    val last = weights.last()
    val allTimeSpanDays = (last.timestampMs - first.timestampMs) / DAY_MS.toDouble()
    val avgAllTime = if (allTimeSpanDays >= 1.0 && first.id != last.id) {
        ((last.weightGrams!! - first.weightGrams!!) / allTimeSpanDays).toFloat()
    } else null

    return WeightGrowthStats(
        netChangeLastWeekGrams = lastWeek.netChangeGrams,
        netChangeLastMonthGrams = lastMonth.netChangeGrams,
        weeklyStats = weeklyStats,
        monthlyStats = monthlyStats,
        avgPerDayLastWeekGrams = lastWeek.avgPerDayGrams,
        avgPerDayLastMonthGrams = lastMonth.avgPerDayGrams,
        avgPerDayLast3MonthsGrams = last3Months.avgPerDayGrams,
        avgPerDayAllTimeGrams = avgAllTime
    )
}
