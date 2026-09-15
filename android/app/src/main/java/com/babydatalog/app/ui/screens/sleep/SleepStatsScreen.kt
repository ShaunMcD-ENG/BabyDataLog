package com.babydatalog.app.ui.screens.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

private fun periodLabel(period: SleepStatsPeriod): String = when (period) {
    SleepStatsPeriod.LAST_7_DAYS -> "Last 7 Days"
    SleepStatsPeriod.LAST_30_DAYS -> "Last 30 Days"
    SleepStatsPeriod.LAST_365_DAYS -> "Last Year"
    SleepStatsPeriod.ALL_TIME -> "All Time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepStatsScreen(
    viewModel: SleepStatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val lens by viewModel.lens.collectAsStateWithLifecycle()
    val histogram by viewModel.histogram.collectAsStateWithLifecycle()

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepStatsPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { viewModel.selectPeriod(p) },
                        label = { Text(periodLabel(p)) }
                    )
                }
            }

            SleepLensCard(title = "Naps", stats = stats.nap)
            SleepLensCard(title = "Night Sleep", stats = stats.night)
            SleepLensCard(title = "Total (Naps + Night)", stats = stats.combined)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sleep by Hour of Day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SleepLensCard(title: String, stats: SleepLensStats) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            if (stats.sessionCount != null) {
                SleepStatRow("Sessions", "${stats.sessionCount}")
                SleepStatRow("Sessions / Day", "%.1f".format(stats.avgSessionsPerDay ?: 0f))
                stats.avgSessionDurationMinutes?.let { SleepStatRow("Avg Length", formatDuration(it)) }
            }
            SleepStatRow("Total Time", formatDuration(stats.totalMinutes))
            SleepStatRow("Avg / Day", formatDuration(stats.avgMinutesPerDay))
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
    // Vico's Chart() leaves stale (blank) columns on screen when only chartModelProducer's
    // *content* changes across recompositions -- reproduced independently of the ViewModel/
    // StateFlow plumbing: a chart that renders 24 all-zero entries once, then receives an
    // update with real values, keeps drawing nothing even though the axis scale updates
    // correctly and the producer's entries are verifiably correct (confirmed via logging and
    // via a hardcoded-producer control that rendered fine). Keying the whole chart subtree on
    // `histogram` forces Vico to fully discard and rebuild its internal chart state instead of
    // reusing the stale one, which reliably fixes it. A stable producer + setEntries(), and
    // diffAnimationSpec = null, were both tried first and did NOT fix this on their own.
    key(histogram) {
        val entries = remember(histogram) {
            histogram.hourlyPercent.mapIndexed { hour, pct -> entryOf(hour.toFloat(), pct) }
        }
        val producer = remember(histogram) { ChartEntryModelProducer(entries) }

        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = columnChart(),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(
                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                    valueFormatter = { value, _ -> "${value.toInt()}%" }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ -> if (value.toInt() % 2 == 0) "${value.toInt()}h" else "" }
                ),
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
