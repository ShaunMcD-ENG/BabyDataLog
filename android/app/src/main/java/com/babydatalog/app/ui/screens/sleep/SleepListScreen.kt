package com.babydatalog.app.ui.screens.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.SleepSession
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.EmptyStateMessage
import com.babydatalog.app.ui.components.formatElapsed
import com.babydatalog.app.ui.components.rememberElapsedSeconds
import com.babydatalog.app.utils.CurrentSleepStatus
import com.babydatalog.app.utils.SleepAwakeSummary
import com.babydatalog.app.utils.WakeWindowStats
import com.babydatalog.app.utils.formatDuration
import com.babydatalog.app.utils.toDisplayDateTime
import com.babydatalog.app.viewmodel.SleepViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val sessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val sleepStatus by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val last24HourSummary by viewModel.last24HourSummary.collectAsStateWithLifecycle()
    val last24HourWakeWindows by viewModel.last24HourWakeWindows.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SleepSession?>(null) }

    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.deleteSleep(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                actions = {
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Sleep Stats")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add Sleep Session")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SleepToggleCard(
                sleepStatus = sleepStatus,
                last24HourSummary = last24HourSummary,
                wakeWindows = last24HourWakeWindows,
                onStart = viewModel::quickStart,
                onEnd = viewModel::quickEnd
            )

            if (sessions.isEmpty()) {
                EmptyStateMessage(message = "No sleep sessions recorded yet")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) pendingDelete = session
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                        MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surface,
                                    label = "swipe_bg"
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(color).padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        ) {
                            SleepListItem(session = session, onClick = { onNavigateToEdit(session.id) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SleepToggleCard(
    sleepStatus: CurrentSleepStatus,
    last24HourSummary: SleepAwakeSummary,
    wakeWindows: WakeWindowStats,
    onStart: () -> Unit,
    onEnd: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (sleepStatus.isAsleep) {
                val elapsedSeconds = rememberElapsedSeconds(sleepStatus.sinceMs)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Baby is asleep", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = formatElapsed(elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onEnd) { Text("Woke Up") }
                }
            } else {
                val elapsedSeconds = rememberElapsedSeconds(sleepStatus.sinceMs)
                Column {
                    Text("Baby is awake", style = MaterialTheme.typography.titleMedium)
                    if (sleepStatus.sinceMs != null) {
                        Text(
                            text = "Awake for ${formatElapsed(elapsedSeconds)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onStart) { Text("Went to Sleep") }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Asleep (24h): ${formatDuration(last24HourSummary.sleepMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Awake (24h): ${formatDuration(last24HourSummary.awakeMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                wakeWindows.day.avgSessionDurationMinutes?.let {
                    Text(
                        text = "Day wake avg: ${formatDuration(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if ((wakeWindows.night.sessionCount ?: 0) > 0) {
                    Text(
                        text = "Night wakes: ${wakeWindows.night.sessionCount} · ${formatDuration(wakeWindows.night.totalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepListItem(session: SleepSession, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (session.type == SleepType.NAP) "Nap" else "Night Sleep",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (session.endTimeMs == null) "In progress"
                    else formatDuration((session.endTimeMs - session.startTimeMs) / 60_000f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.startTimeMs.toDisplayDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
