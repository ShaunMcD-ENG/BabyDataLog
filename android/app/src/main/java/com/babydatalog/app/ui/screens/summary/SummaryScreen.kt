package com.babydatalog.app.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.utils.WeightGrowthStats
import com.babydatalog.app.utils.WeightPeriodStat
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.viewmodel.SummaryPeriod
import com.babydatalog.app.viewmodel.SummaryViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val weightStats by viewModel.weightStats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Summary") })
        }
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

            // Period selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = {
                            Text(
                                when (period) {
                                    SummaryPeriod.TODAY -> "Today"
                                    SummaryPeriod.THIS_WEEK -> "This Week"
                                    SummaryPeriod.THIS_MONTH -> "This Month"
                                }
                            )
                        }
                    )
                }
            }

            // Feedings stats card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Feedings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatRow(label = "Total", value = "${stats.totalFeedings}")
                    if (stats.avgFeedDurationMinutes != null) {
                        StatRow(
                            label = "Avg Duration",
                            value = formatDuration(stats.avgFeedDurationMinutes)
                        )
                    }
                    if (selectedPeriod != SummaryPeriod.TODAY && stats.feedsPerDay > 0f) {
                        StatRow(
                            label = "Per Day",
                            value = "%.1f".format(stats.feedsPerDay)
                        )
                    }
                }
            }

            // Nappies stats card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Nappy Changes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatRow(label = "Total", value = "${stats.totalNappies}")
                    StatRow(label = "Pees", value = "${stats.totalPees}")
                    StatRow(label = "Poos", value = "${stats.totalPoos}")
                    if (selectedPeriod != SummaryPeriod.TODAY && stats.nappiesPerDay > 0f) {
                        StatRow(
                            label = "Per Day",
                            value = "%.1f".format(stats.nappiesPerDay)
                        )
                    }
                }
            }

            // Weight growth card — always whole-history based, independent of the period selector above
            WeightGrowthCard(weightStats)

            // Feeding chart — title and x-axis labels change with period
            val feedingChartTitle = when (selectedPeriod) {
                SummaryPeriod.TODAY -> "Feedings — Today by Hour"
                SummaryPeriod.THIS_WEEK -> "Feedings — This Week"
                SummaryPeriod.THIS_MONTH -> "Feedings — This Month"
            }
            val chartXSpacing = if (selectedPeriod == SummaryPeriod.THIS_MONTH) 7 else 1

            if (stats.feedingChartData.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = feedingChartTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BarChart(data = stats.feedingChartData, xLabelSpacing = chartXSpacing)
                    }
                }
            }

            // Nappy chart — title and x-axis labels change with period
            val nappyChartTitle = when (selectedPeriod) {
                SummaryPeriod.TODAY -> "Nappies — Today by Hour"
                SummaryPeriod.THIS_WEEK -> "Nappies — This Week"
                SummaryPeriod.THIS_MONTH -> "Nappies — This Month"
            }
            if (stats.nappyChartData.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = nappyChartTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BarChart(data = stats.nappyChartData, xLabelSpacing = chartXSpacing)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatSignedGrams(grams: Int?): String {
    if (grams == null) return "N/A"
    val sign = if (grams > 0) "+" else ""
    return "$sign${grams}g"
}

private fun formatPerDay(gramsPerDay: Float?): String {
    if (gramsPerDay == null) return "N/A"
    val sign = if (gramsPerDay > 0) "+" else ""
    return "$sign%.1fg/day".format(gramsPerDay)
}

@Composable
private fun WeightGrowthCard(weightStats: WeightGrowthStats) {
    val hasAnyData = weightStats.netChangeLastWeekGrams != null ||
        weightStats.netChangeLastMonthGrams != null ||
        weightStats.avgPerDayAllTimeGrams != null

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weight Growth",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!hasAnyData) {
                Text(
                    text = "Not enough weight records yet — add at least two to see growth trends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                StatRow(label = "Net Change (7 Days)", value = formatSignedGrams(weightStats.netChangeLastWeekGrams))
                StatRow(label = "Net Change (30 Days)", value = formatSignedGrams(weightStats.netChangeLastMonthGrams))

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Weekly Breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                weightStats.weeklyStats.forEach { period ->
                    StatRow(
                        label = period.label,
                        value = "${formatSignedGrams(period.netChangeGrams)} (${formatPerDay(period.avgPerDayGrams)})"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Monthly Breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                weightStats.monthlyStats.forEach { period ->
                    StatRow(
                        label = period.label,
                        value = "${formatSignedGrams(period.netChangeGrams)} (${formatPerDay(period.avgPerDayGrams)})"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                StatRow(label = "All-Time Avg", value = formatPerDay(weightStats.avgPerDayAllTimeGrams))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    xLabelSpacing: Int = 1
) {
    val entries = remember(data) {
        data.mapIndexed { index, (_, count) -> entryOf(index.toFloat(), count.toFloat()) }
    }
    val producer = remember(data) { ChartEntryModelProducer(entries) }

    ProvideChartStyle(m3ChartStyle()) {
        Chart(
            chart = columnChart(),
            chartModelProducer = producer,
            startAxis = rememberStartAxis(
                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5)
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    data.getOrNull(value.toInt())?.first ?: ""
                },
                itemPlacer = AxisItemPlacer.Horizontal.default(spacing = xLabelSpacing)
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
        )
    }
}
