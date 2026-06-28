package com.babydatalog.app.ui.screens.feeding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.BabyState
import com.babydatalog.app.data.database.entity.BreastSide
import com.babydatalog.app.data.database.entity.LatchQuality
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup
import com.babydatalog.app.viewmodel.FeedingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedingFormScreen(
    feedingId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: FeedingViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedDurationTab by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var manualMins by remember { mutableStateOf("") }
    var manualSecs by remember { mutableStateOf("") }

    // Load existing feeding for edit
    LaunchedEffect(feedingId) {
        if (feedingId > 0L) {
            viewModel.loadFeeding(feedingId)
        }
    }

    // Navigate back on success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.resetForm()
            onNavigateBack()
        }
    }

    // Show error in snackbar
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Sync manual fields whenever the user switches to the manual tab,
    // reading elapsedSeconds so it works whether the timer is running, paused,
    // or the form was loaded from a saved entry.
    LaunchedEffect(selectedDurationTab) {
        if (selectedDurationTab == 1) {
            val secs = elapsedSeconds
            manualMins = if (secs > 0L) (secs / 60).toString() else ""
            manualSecs = if (secs > 0L) (secs % 60).toString() else ""
        }
    }

    // Live timer — includes already-accumulated seconds
    LaunchedEffect(state.isTimerRunning, state.timerStartMs, state.accumulatedSeconds) {
        if (state.isTimerRunning && state.timerStartMs != null) {
            while (true) {
                elapsedSeconds = state.accumulatedSeconds + (System.currentTimeMillis() - state.timerStartMs!!) / 1000L
                delay(1000L)
            }
        } else {
            elapsedSeconds = state.accumulatedSeconds
        }
    }

    val title = if (feedingId > 0L) "Edit Feeding" else "Add Feeding"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Date & Time
            SectionHeader("Date & Time")
            DateTimePickerRow(
                label = "Start Time",
                timestampMs = state.startTimeMs,
                onDateTimeSelected = { viewModel.updateStartTime(it) }
            )

            // Breast Side
            SectionHeader("Breast Side")
            ToggleChipGroup(
                options = BreastSide.entries,
                selected = state.breastSide,
                onSelect = { viewModel.updateBreastSide(it) },
                label = { side ->
                    when (side) {
                        BreastSide.LEFT -> "Left"
                        BreastSide.RIGHT -> "Right"
                        BreastSide.BOTH -> "Both"
                        BreastSide.BOTTLE -> "Bottle"
                    }
                }
            )

            // Duration
            SectionHeader("Duration")
            TabRow(selectedTabIndex = selectedDurationTab) {
                Tab(
                    selected = selectedDurationTab == 0,
                    onClick = { selectedDurationTab = 0 },
                    text = { Text("Timer") }
                )
                Tab(
                    selected = selectedDurationTab == 1,
                    onClick = { selectedDurationTab = 1 },
                    text = { Text("Manual") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (selectedDurationTab) {
                0 -> {
                    // Timer tab
                    val hasAccumulated = elapsedSeconds > 0L

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Display
                        val displaySecs = elapsedSeconds
                        val hours = displaySecs / 3600
                        val mins = (displaySecs % 3600) / 60
                        val secs = displaySecs % 60
                        Text(
                            text = if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
                                   else "%02d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (hasAccumulated || state.isTimerRunning)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            state.isTimerRunning -> {
                                Button(onClick = { viewModel.pauseTimer() }) {
                                    Text("Pause")
                                }
                            }
                            hasAccumulated -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.continueTimer() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Continue Timer")
                                    }
                                    Button(
                                        onClick = { viewModel.resetTimer() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Reset")
                                    }
                                }
                            }
                            else -> {
                                Button(onClick = { viewModel.startTimer() }) {
                                    Text("Start Timer")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Manual tab — minutes and seconds side by side, linked to timer state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualMins,
                            onValueChange = { v ->
                                manualMins = v.filter { it.isDigit() }
                                val m = manualMins.toIntOrNull() ?: 0
                                val s = manualSecs.toIntOrNull() ?: 0
                                val totalSecs = (m * 60 + s).toLong()
                                viewModel.setAccumulatedSeconds(
                                    if (manualMins.isBlank() && manualSecs.isBlank()) 0L else totalSecs
                                )
                            },
                            label = { Text("Minutes") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualSecs,
                            onValueChange = { v ->
                                manualSecs = v.filter { it.isDigit() }
                                val m = manualMins.toIntOrNull() ?: 0
                                val s = manualSecs.toIntOrNull() ?: 0
                                val totalSecs = (m * 60 + s).toLong()
                                viewModel.setAccumulatedSeconds(
                                    if (manualMins.isBlank() && manualSecs.isBlank()) 0L else totalSecs
                                )
                            },
                            label = { Text("Seconds") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // Baby State
            SectionHeader("Baby State (optional)")
            val babyStateOptions: List<BabyState?> = listOf(null) + BabyState.entries
            ToggleChipGroup(
                options = babyStateOptions,
                selected = state.babyState,
                onSelect = { viewModel.updateBabyState(it) },
                label = { bs ->
                    when (bs) {
                        null -> "Not recorded"
                        BabyState.SLEEPY -> "Sleepy"
                        BabyState.ENGAGED -> "Engaged"
                    }
                }
            )

            // Latch Quality (hidden for BOTTLE)
            if (state.breastSide != BreastSide.BOTTLE) {
                SectionHeader("Latch Quality (optional)")
                val latchOptions: List<LatchQuality?> = listOf(null) + LatchQuality.entries
                ToggleChipGroup(
                    options = latchOptions,
                    selected = state.latchQuality,
                    onSelect = { viewModel.updateLatchQuality(it) },
                    label = { lq ->
                        when (lq) {
                            null -> "Not recorded"
                            LatchQuality.GOOD -> "Good"
                            LatchQuality.POOR -> "Poor"
                        }
                    }
                )
            }

            // Notes
            SectionHeader("Notes")
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { viewModel.saveFeeding() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Feeding")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
