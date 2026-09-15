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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.utils.SleepLensStats
import com.babydatalog.app.utils.SleepStatsPeriod
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.viewmodel.SleepStatsViewModel

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
