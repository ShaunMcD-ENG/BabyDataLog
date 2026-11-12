package com.babydatalog.app.ui.screens.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.utils.DailySleepTotal
import com.babydatalog.app.utils.MonthlySleepAverage
import com.babydatalog.app.utils.SleepHistogram
import com.babydatalog.app.utils.SleepLens
import com.babydatalog.app.utils.SleepLensStats
import com.babydatalog.app.utils.SleepStatsPeriod
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.viewmodel.SleepStatsViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.context.MeasureContext
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

private fun periodLabel(period: SleepStatsPeriod): String = when (period) {
    SleepStatsPeriod.TODAY -> "Today"
    SleepStatsPeriod.LAST_24_HOURS -> "Last 24 Hours"
    SleepStatsPeriod.LAST_7_DAYS -> "Last 7 Days"
    SleepStatsPeriod.LAST_30_DAYS -> "Last 30 Days"
    SleepStatsPeriod.LAST_365_DAYS -> "Last Year"
    SleepStatsPeriod.ALL_TIME -> "All Time"
}

// TODAY/LAST_24_HOURS span at most one day, so the hourly chart shows a literal minute
// count rather than an average across multiple days.
private fun isSingleDayPeriod(period: SleepStatsPeriod): Boolean =
    period == SleepStatsPeriod.TODAY || period == SleepStatsPeriod.LAST_24_HOURS

// 0 -> "12am", 13 -> "1pm", 23 -> "11pm", etc.
private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12am"
    hour < 12 -> "${hour}am"
    hour == 12 -> "12pm"
    else -> "${hour - 12}pm"
}

/**
 * Vico's default vertical-axis item placer divides [minY, maxY] into
 * evenly-spaced steps with no regard for whether the result is a whole
 * number (e.g. 13.3h gridlines for a 4-label axis over a 40h range). This
 * picks a whole-number step instead, so "hours" axes always show integers.
 */
private class WholeNumberVerticalAxisItemPlacer(private val maxLabelCount: Int = 5) : AxisItemPlacer.Vertical {

    private fun labelValues(context: MeasureContext, position: AxisPosition.Vertical): List<Float> {
        val maxY = context.chartValuesProvider.getChartValues(position).maxY
        if (maxY <= 0f) return listOf(0f)
        val roundedMax = ceil(maxY).toInt().coerceAtLeast(1)
        val step = ceil(roundedMax / (maxLabelCount - 1).toFloat()).toInt().coerceAtLeast(1)
        val values = mutableListOf(0f)
        var v = step
        while (v < roundedMax) {
            values += v.toFloat()
            v += step
        }
        values += roundedMax.toFloat()
        return values
    }

    override fun getLabelValues(
        context: ChartDrawContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: AxisPosition.Vertical
    ): List<Float> = labelValues(context, position)

    override fun getHeightMeasurementLabelValues(
        context: MeasureContext,
        position: AxisPosition.Vertical
    ): List<Float> = labelValues(context, position)

    override fun getWidthMeasurementLabelValues(
        context: MeasureContext,
        axisHeight: Float,
        maxLabelHeight: Float,
        position: AxisPosition.Vertical
    ): List<Float> = labelValues(context, position)

    override fun getTopVerticalAxisInset(
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float
    ): Float = maxLabelHeight / 2f

    override fun getBottomVerticalAxisInset(
        verticalLabelPosition: VerticalAxis.VerticalLabelPosition,
        maxLabelHeight: Float,
        maxLineThickness: Float
    ): Float = maxLabelHeight / 2f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SleepStatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val lens by viewModel.lens.collectAsStateWithLifecycle()
    val histogram by viewModel.histogram.collectAsStateWithLifecycle()
    val dailyTotals by viewModel.dailyTotals.collectAsStateWithLifecycle()
    val monthlyAverages by viewModel.monthlyAverages.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sleep Stats") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Sleep Log")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepStatsPeriod.entries.chunked(3).forEach { rowPeriods ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPeriods.forEach { p ->
                            FilterChip(
                                selected = period == p,
                                onClick = { viewModel.selectPeriod(p) },
                                label = { Text(periodLabel(p)) }
                            )
                        }
                    }
                }
            }

            SleepLensCard(title = "Day Wake Windows", subtitle = "Morning wake-up → naps → bedtime", stats = stats.dayWakeWindow)
            SleepLensCard(title = "Night Wake Windows", subtitle = "Awake between night sleeps (e.g. feeds)", stats = stats.nightWakeWindow)
            SleepLensCard(title = "Naps", stats = stats.nap)
            SleepLensCard(title = "Night Sleep", stats = stats.night)
            SleepLensCard(title = "Total (Naps + Night)", stats = stats.combined)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isSingleDayPeriod(period)) "Minutes Asleep by Hour of Day" else "Average Minutes Asleep by Hour of Day",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SleepLens.entries.forEach { l ->
                            FilterChip(
                                selected = lens == l,
                                onClick = { viewModel.selectLens(l) },
                                label = {
                                    Text(
                                        when (l) {
                                            SleepLens.NAP -> "Nap"
                                            SleepLens.NIGHT -> "Night"
                                            SleepLens.COMBINED -> "Combined"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepHistogramChart(histogram = histogram)
                }
            }

            when (period) {
                SleepStatsPeriod.LAST_7_DAYS, SleepStatsPeriod.LAST_30_DAYS -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Sleep by Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            DailySleepChart(totals = dailyTotals)
                        }
                    }
                }
                SleepStatsPeriod.LAST_365_DAYS -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Average Daily Sleep by Month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            MonthlySleepChart(averages = monthlyAverages)
                        }
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SleepLensCard(title: String, stats: SleepLensStats, subtitle: String? = null) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.height(8.dp))
            if (stats.sessionCount != null) {
                SleepStatRow("Sessions", "${stats.sessionCount}")
                SleepStatRow("Sessions / Day", "%.1f".format(stats.avgSessionsPerDay ?: 0f))
                stats.avgSessionDurationMinutes?.let { SleepStatRow("Average Length", formatDuration(it)) }
            }
            SleepStatRow("Total Time", formatDuration(stats.totalMinutes))
            SleepStatRow("Average / Day", formatDuration(stats.avgMinutesPerDay))
        }
    }
}

@Composable
private fun SleepStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SleepHistogramChart(histogram: SleepHistogram) {
    // Vico's Chart() leaves stale (blank) content on screen when only chartModelProducer's
    // *content* changes across recompositions -- reproduced independently of the ViewModel/
    // StateFlow plumbing: a chart that renders 24 all-zero entries once, then receives an
    // update with real values, keeps drawing nothing even though the axis scale updates
    // correctly and the producer's entries are verifiably correct (confirmed via logging and
    // via a hardcoded-producer control that rendered fine). Keying the whole chart subtree on
    // the data forces Vico to fully discard and rebuild its internal chart state instead of
    // reusing the stale one, which reliably fixes it. A stable producer + setEntries(), and
    // diffAnimationSpec = null, were both tried first and did NOT fix this on their own.
    key(histogram) {
        val entries = remember(histogram) {
            histogram.hourlyMinutesAsleep.mapIndexed { hour, minutes -> entryOf(hour.toFloat(), minutes) }
        }
        val producer = remember(histogram) { ChartEntryModelProducer(entries) }

        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                // Default point connector uses cubic bezier curves between points.
                chart = lineChart(axisValuesOverrider = AxisValuesOverrider.fixed(minY = 0f, maxY = 60f)),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(
                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                    valueFormatter = { value, _ -> "${value.toInt()}m" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> if (value.toInt() % 2 == 0) hourLabel(value.toInt()) else "" }
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

@Composable
private fun DailySleepChart(totals: List<DailySleepTotal>) {
    key(totals) {
        val dateFormatter = remember { DateTimeFormatter.ofPattern("d/M") }
        val labels = remember(totals) { totals.map { dateFormatter.format(it.date) } }
        val entries = remember(totals) {
            totals.mapIndexed { index, total -> entryOf(index.toFloat(), total.totalMinutes / 60f) }
        }
        val producer = remember(totals) { ChartEntryModelProducer(entries) }
        val axisMax = remember(entries) { ceil((entries.maxOfOrNull { it.y } ?: 0f).coerceAtLeast(1f)) }

        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = columnChart(axisValuesOverrider = AxisValuesOverrider.fixed(minY = 0f, maxY = axisMax)),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(
                    itemPlacer = remember { WholeNumberVerticalAxisItemPlacer() },
                    valueFormatter = { value, _ -> "${value.toInt()}h" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> labels.getOrNull(value.toInt()) ?: "" }
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}

@Composable
private fun MonthlySleepChart(averages: List<MonthlySleepAverage>) {
    key(averages) {
        val labels = remember(averages) {
            averages.map { it.yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        val entries = remember(averages) {
            averages.mapIndexed { index, avg -> entryOf(index.toFloat(), avg.avgDailyMinutes / 60f) }
        }
        val producer = remember(averages) { ChartEntryModelProducer(entries) }
        val axisMax = remember(entries) { ceil((entries.maxOfOrNull { it.y } ?: 0f).coerceAtLeast(1f)) }

        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = columnChart(axisValuesOverrider = AxisValuesOverrider.fixed(minY = 0f, maxY = axisMax)),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(
                    itemPlacer = remember { WholeNumberVerticalAxisItemPlacer() },
                    valueFormatter = { value, _ -> "${value.toInt()}h" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> labels.getOrNull(value.toInt()) ?: "" }
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
