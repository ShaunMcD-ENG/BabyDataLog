package com.babydatalog.app.ui.screens.nappy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.EmptyStateMessage
import com.babydatalog.app.utils.toDisplayDateTime
import com.babydatalog.app.viewmodel.NappySortOrder
import com.babydatalog.app.viewmodel.NappyViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun nappySortLabel(order: NappySortOrder): String = when (order) {
    NappySortOrder.NEWEST_FIRST -> "Newest First"
    NappySortOrder.OLDEST_FIRST -> "Oldest First"
    NappySortOrder.POO_FIRST -> "Poo First"
    NappySortOrder.PEE_FIRST -> "Pee First"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NappyListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: NappyViewModel = hiltViewModel()
) {
    val nappies by viewModel.nappies.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<NappyChange?>(null) }

    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.deleteNappy(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nappy Changes") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add Nappy")
            }
        }
    ) { innerPadding ->
        if (nappies.isEmpty()) {
            EmptyStateMessage(
                message = "No nappy changes recorded yet",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mini chart — nappies per day for last 7 days
                    NappyMiniChart(nappies = nappies)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Sort dropdown
                    NappySortDropdown(
                        current = sortOrder,
                        onSelect = { viewModel.setSortOrder(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(nappies, key = { it.id }) { nappy ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = nappy
                            }
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        NappyListItem(
                            nappy = nappy,
                            onClick = { onNavigateToEdit(nappy.id) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NappySortDropdown(
    current: NappySortOrder,
    onSelect: (NappySortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = nappySortLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text("Sort by") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            NappySortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(nappySortLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NappyMiniChart(nappies: List<NappyChange>) {
    val zone = ZoneId.systemDefault()
    val dayFormatter = DateTimeFormatter.ofPattern("EEE").withZone(zone)
    val nowMs = System.currentTimeMillis()
    val todayMidnightMs = Instant.ofEpochMilli(nowMs)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    val chartData = (6 downTo 0).map { daysAgo ->
        val dayStartMs = todayMidnightMs - daysAgo * 86_400_000L
        val dayEndMs = dayStartMs + 86_400_000L
        val label = dayFormatter.format(Instant.ofEpochMilli(dayStartMs))
        val count = nappies.count { it.timestampMs in dayStartMs until dayEndMs }
        label to count
    }

    val entries = remember(chartData) {
        chartData.mapIndexed { index, (_, count) -> entryOf(index.toFloat(), count.toFloat()) }
    }
    val producer = remember(chartData) { ChartEntryModelProducer(entries) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Nappies per day — last 7 days",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProvideChartStyle(m3ChartStyle()) {
                Chart(
                    chart = columnChart(),
                    chartModelProducer = producer,
                    startAxis = rememberStartAxis(
                        itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5)
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            chartData.getOrNull(value.toInt())?.first ?: ""
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NappyListItem(
    nappy: NappyChange,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = nappy.timestampMs.toDisplayDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(
                            when (nappy.type) {
                                NappyType.PEE -> "Pee"
                                NappyType.POO -> "Poo"
                                NappyType.BOTH -> "Pee + Poo"
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = {
                        Text(
                            nappy.amount.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                    }
                )
                if (nappy.pooColour != null && nappy.type != NappyType.PEE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = nappy.pooColour.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
