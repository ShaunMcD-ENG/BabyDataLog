package com.babydatalog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.repository.FeedingRepository
import com.babydatalog.app.data.repository.NappyRepository
import com.babydatalog.app.utils.monthEndMs
import com.babydatalog.app.utils.monthStartMs
import com.babydatalog.app.utils.todayEndMs
import com.babydatalog.app.utils.todayStartMs
import com.babydatalog.app.utils.weekEndMs
import com.babydatalog.app.utils.weekStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class SummaryPeriod { TODAY, THIS_WEEK, THIS_MONTH }

data class SummaryStats(
    val totalFeedings: Int = 0,
    val totalNappies: Int = 0,
    val totalPees: Int = 0,
    val totalPoos: Int = 0,
    val avgFeedDurationMinutes: Float? = null,
    val feedsPerDay: Float = 0f,
    val nappiesPerDay: Float = 0f,
    val feedingChartData: List<Pair<String, Int>> = emptyList(),
    val nappyChartData: List<Pair<String, Int>> = emptyList()
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val feedingRepository: FeedingRepository,
    private val nappyRepository: NappyRepository
) : ViewModel() {

    // babyId is now driven by BabyViewModel via NavGraph LaunchedEffect
    private val _defaultBabyId = MutableStateFlow<Long?>(null)
    private val _selectedPeriod = MutableStateFlow(SummaryPeriod.TODAY)

    val selectedPeriod: StateFlow<SummaryPeriod> = _selectedPeriod

    val stats: StateFlow<SummaryStats> = combine(
        _defaultBabyId,
        _selectedPeriod
    ) { babyId, period -> Pair(babyId, period) }
        .flatMapLatest { (babyId, period) ->
            if (babyId == null) return@flatMapLatest flowOf(SummaryStats())

            val (startMs, endMs) = periodRange(period)
            val daysInPeriod = ((endMs - startMs) / 86_400_000.0).coerceAtLeast(1.0).toFloat()

            combine(
                feedingRepository.getFeedingsInRange(babyId, startMs, endMs),
                nappyRepository.getNappiesInRange(babyId, startMs, endMs)
            ) { periodFeedings, periodNappies ->

                val totalFeedings = periodFeedings.size
                val totalNappies = periodNappies.size
                val totalPees = periodNappies.count { it.type == NappyType.PEE || it.type == NappyType.BOTH }
                val totalPoos = periodNappies.count { it.type == NappyType.POO || it.type == NappyType.BOTH }

                val durationsWithValue = periodFeedings.mapNotNull { it.durationMinutes }
                val avgDuration = if (durationsWithValue.isNotEmpty()) {
                    durationsWithValue.average().toFloat()
                } else null

                val feedsPerDay = if (period == SummaryPeriod.TODAY) 0f
                else totalFeedings.toFloat() / daysInPeriod

                val nappiesPerDay = if (period == SummaryPeriod.TODAY) 0f
                else totalNappies.toFloat() / daysInPeriod

                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)

                val feedingChartData: List<Pair<String, Int>>
                val nappyChartData: List<Pair<String, Int>>

                when (period) {
                    SummaryPeriod.TODAY -> {
                        // X-axis: hours 0–23, label every 2 hours ("0h", "2h", ...)
                        val todayStart = todayStartMs()
                        feedingChartData = (0..23).map { hour ->
                            val label = if (hour % 2 == 0) "${hour}h" else ""
                            val hourStartMs = todayStart + hour * 3_600_000L
                            val hourEndMs = hourStartMs + 3_600_000L
                            val count = periodFeedings.count { it.startTimeMs in hourStartMs until hourEndMs }
                            label to count
                        }
                        nappyChartData = (0..23).map { hour ->
                            val label = if (hour % 2 == 0) "${hour}h" else ""
                            val hourStartMs = todayStart + hour * 3_600_000L
                            val hourEndMs = hourStartMs + 3_600_000L
                            val count = periodNappies.count { it.timestampMs in hourStartMs until hourEndMs }
                            label to count
                        }
                    }

                    SummaryPeriod.THIS_WEEK -> {
                        // X-axis: Mon–Sun abbreviated day names
                        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        feedingChartData = (0..6).map { dayOffset ->
                            val date = weekStart.plusDays(dayOffset.toLong())
                            val label = dayNames[dayOffset]
                            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                            val dayEndMs = dayStartMs + 86_400_000L
                            val count = periodFeedings.count { it.startTimeMs in dayStartMs until dayEndMs }
                            label to count
                        }
                        nappyChartData = (0..6).map { dayOffset ->
                            val date = weekStart.plusDays(dayOffset.toLong())
                            val label = dayNames[dayOffset]
                            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                            val dayEndMs = dayStartMs + 86_400_000L
                            val count = periodNappies.count { it.timestampMs in dayStartMs until dayEndMs }
                            label to count
                        }
                    }

                    SummaryPeriod.THIS_MONTH -> {
                        // X-axis: each day; label only Mondays (or every 7th day if no Mondays)
                        val firstDay = today.withDayOfMonth(1)
                        val daysInMonth = today.lengthOfMonth()

                        val mondays = (0 until daysInMonth)
                            .map { firstDay.plusDays(it.toLong()) }
                            .filter { it.dayOfWeek == DayOfWeek.MONDAY }
                            .map { it.dayOfMonth }
                            .toSet()
                        val labelDays: Set<Int> = if (mondays.isNotEmpty()) mondays
                        else (1..daysInMonth step 7).toSet()

                        feedingChartData = (0 until daysInMonth).map { dayIndex ->
                            val date = firstDay.plusDays(dayIndex.toLong())
                            val label = if (date.dayOfMonth in labelDays) "${date.dayOfMonth}" else ""
                            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                            val dayEndMs = dayStartMs + 86_400_000L
                            val count = periodFeedings.count { it.startTimeMs in dayStartMs until dayEndMs }
                            label to count
                        }
                        nappyChartData = (0 until daysInMonth).map { dayIndex ->
                            val date = firstDay.plusDays(dayIndex.toLong())
                            val label = if (date.dayOfMonth in labelDays) "${date.dayOfMonth}" else ""
                            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                            val dayEndMs = dayStartMs + 86_400_000L
                            val count = periodNappies.count { it.timestampMs in dayStartMs until dayEndMs }
                            label to count
                        }
                    }
                }

                SummaryStats(
                    totalFeedings = totalFeedings,
                    totalNappies = totalNappies,
                    totalPees = totalPees,
                    totalPoos = totalPoos,
                    avgFeedDurationMinutes = avgDuration,
                    feedsPerDay = feedsPerDay,
                    nappiesPerDay = nappiesPerDay,
                    feedingChartData = feedingChartData,
                    nappyChartData = nappyChartData
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryStats())

    fun setActiveBabyId(id: Long) {
        if (_defaultBabyId.value != id) {
            _defaultBabyId.value = id
        }
    }

    fun selectPeriod(period: SummaryPeriod) {
        _selectedPeriod.value = period
    }

    private fun periodRange(period: SummaryPeriod): Pair<Long, Long> = when (period) {
        SummaryPeriod.TODAY -> Pair(todayStartMs(), todayEndMs())
        SummaryPeriod.THIS_WEEK -> Pair(weekStartMs(), weekEndMs())
        SummaryPeriod.THIS_MONTH -> Pair(monthStartMs(), monthEndMs())
    }
}
