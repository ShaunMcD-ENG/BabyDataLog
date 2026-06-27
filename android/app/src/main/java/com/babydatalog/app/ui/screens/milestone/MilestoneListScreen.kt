package com.babydatalog.app.ui.screens.milestone

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.Milestone
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.EmptyStateMessage
import com.babydatalog.app.utils.toDisplayDate
import com.babydatalog.app.viewmodel.MilestoneSortOrder
import com.babydatalog.app.viewmodel.MilestoneViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

private fun categoryDisplayName(category: MilestoneCategory): String = when (category) {
    MilestoneCategory.DEVELOPMENT -> "Development"
    MilestoneCategory.MEDICAL -> "Medical"
    MilestoneCategory.SOCIAL -> "Social"
    MilestoneCategory.PHYSICAL -> "Physical"
    MilestoneCategory.FIRST_TIME -> "First Time"
}

private fun milestoneSortLabel(order: MilestoneSortOrder): String = when (order) {
    MilestoneSortOrder.NEWEST_FIRST -> "Newest First"
    MilestoneSortOrder.OLDEST_FIRST -> "Oldest First"
    MilestoneSortOrder.BY_CATEGORY -> "By Category"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneListScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: MilestoneViewModel = hiltViewModel()
) {
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Milestone?>(null) }

    if (pendingDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                pendingDelete?.let { viewModel.deleteMilestone(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Milestones") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add Milestone")
            }
        }
    ) { innerPadding ->
        if (milestones.isEmpty()) {
            EmptyStateMessage(
                message = "No milestones recorded yet",
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
                    // Mini chart — milestones by category
                    MilestoneCategoryChart(milestones = milestones)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Sort dropdown
                    MilestoneSortDropdown(
                        current = sortOrder,
                        onSelect = { viewModel.setSortOrder(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(milestones, key = { it.id }) { milestone ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = milestone
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
                        MilestoneListItem(
                            milestone = milestone,
                            onClick = { onNavigateToEdit(milestone.id) }
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
private fun MilestoneSortDropdown(
    current: MilestoneSortOrder,
    onSelect: (MilestoneSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = milestoneSortLabel(current),
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
            MilestoneSortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(milestoneSortLabel(option)) },
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
private fun MilestoneCategoryChart(milestones: List<Milestone>) {
    val categories = MilestoneCategory.entries
    val chartData = categories.map { cat ->
        categoryDisplayName(cat) to milestones.count { it.category == cat }
    }

    val entries = remember(chartData) {
        chartData.mapIndexed { index, (_, count) -> entryOf(index.toFloat(), count.toFloat()) }
    }
    val producer = remember(chartData) { ChartEntryModelProducer(entries) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Milestones by category",
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
private fun MilestoneListItem(
    milestone: Milestone,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = milestone.timestampMs.toDisplayDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(categoryDisplayName(milestone.category)) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!milestone.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
