package com.babydatalog.app.ui.screens.growth

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.babydatalog.app.data.database.entity.GrowthMeasurement
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.EmptyStateMessage
import com.babydatalog.app.utils.toDisplayDateTime
import com.babydatalog.app.viewmodel.GrowthSortOrder
import com.babydatalog.app.viewmodel.GrowthViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun growthSortLabel(order: GrowthSortOrder): String = when (order) {
    GrowthSortOrder.NEWEST_FIRST -> "Newest First"
    GrowthSortOrder.OLDEST_FIRST -> "Oldest First"
    GrowthSortOrder.HEAVIEST_FIRST -> "Heaviest First"
    GrowthSortOrder.LIGHTEST_FIRST -> "Lightest First"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: GrowthViewModel = hiltViewModel()
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<GrowthMeasurement?>(null) }

    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.deleteMeasurement(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Growth") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add Growth Measurement")
            }
        }
    ) { innerPadding ->
        if (measurements.isEmpty()) {
            EmptyStateMessage(
                message = "No growth records yet",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mini line chart — weight over last 10 measurements
                    GrowthWeightChart(measurements = measurements)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Sort dropdown
                    GrowthSortDropdown(
                        current = sortOrder,
                        onSelect = { viewModel.setSortOrder(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(measurements, key = { it.id }) { measurement ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = measurement
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
                        GrowthListItem(
                            measurement = measurement,
                            onEditClick = { onNavigateToEdit(measurement.id) }
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
private fun GrowthSortDropdown(
    current: GrowthSortOrder,
    onSelect: (GrowthSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = growthSortLabel(current),
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
            GrowthSortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(growthSortLabel(option)) },
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
private fun GrowthWeightChart(measurements: List<GrowthMeasurement>) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("d/M").withZone(zone)

    // Take the last 10 measurements with a weight, sorted oldest→newest for charting
    val weightPoints = measurements
        .filter { it.weightGrams != null }
        .sortedBy { it.timestampMs }
        .takeLast(10)

    if (weightPoints.size < 2) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Weight over time",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Not enough data for chart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val chartData = weightPoints.map { m ->
        val label = dateFormatter.format(Instant.ofEpochMilli(m.timestampMs))
        label to m.weightGrams!!
    }

    val entries = remember(chartData) {
        chartData.mapIndexed { index, (_, grams) -> entryOf(index.toFloat(), grams.toFloat()) }
    }
    val producer = remember(chartData) { ChartEntryModelProducer(entries) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Weight over time (last ${weightPoints.size} records)",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProvideChartStyle(m3ChartStyle()) {
                Chart(
                    chart = lineChart(),
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

@Composable
private fun GrowthListItem(
    measurement: GrowthMeasurement,
    onEditClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = measurement.timestampMs.toDisplayDateTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (measurement.weightGrams != null) {
                val grams = measurement.weightGrams
                val weightDisplay = if (grams >= 1000) {
                    val kg = grams / 1000f
                    "${grams}g (${kg}kg)"
                } else {
                    "${grams}g"
                }
                Text(
                    text = "Weight: $weightDisplay",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (measurement.heightCm != null) {
                Text(
                    text = "Height: ${measurement.heightCm}cm",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (measurement.headCircumferenceCm != null) {
                Text(
                    text = "HC: ${measurement.headCircumferenceCm}cm",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val hasFootOrHand = measurement.footSizeMm != null || measurement.handSizeMm != null
            if (hasFootOrHand) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (measurement.footSizeMm != null) {
                        Text(
                            text = "Foot: ${measurement.footSizeMm}mm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (measurement.handSizeMm != null) {
                        Text(
                            text = "Hand: ${measurement.handSizeMm}mm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            val hasLimbLengths = measurement.legLengthCm != null ||
                measurement.armLengthCm != null || measurement.backLengthCm != null
            if (hasLimbLengths) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (measurement.legLengthCm != null) {
                        Text(
                            text = "Leg: ${measurement.legLengthCm}cm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (measurement.armLengthCm != null) {
                        Text(
                            text = "Arm: ${measurement.armLengthCm}cm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (measurement.backLengthCm != null) {
                        Text(
                            text = "Back: ${measurement.backLengthCm}cm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (!measurement.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = measurement.notes.take(50),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
