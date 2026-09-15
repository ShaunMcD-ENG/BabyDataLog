package com.babydatalog.app.ui.screens.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.SleepType
import com.babydatalog.app.ui.components.ConfirmDeleteDialog
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup
import com.babydatalog.app.viewmodel.SleepViewModel
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepFormScreen(
    sleepId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(sleepId) {
        if (sleepId > 0L) viewModel.loadSleep(sleepId) else viewModel.resetForm()
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.resetForm()
            onNavigateBack()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSleep(
                    com.babydatalog.app.data.database.entity.SleepSession(
                        id = state.id, syncUuid = state.syncUuid, babyId = state.babyId,
                        startTimeMs = state.startTimeMs, endTimeMs = state.endTimeMs, type = state.type,
                        notes = state.notes.ifBlank { null }, createdAtMs = state.createdAtMs
                    )
                )
                onNavigateBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    val title = if (sleepId > 0L) "Edit Sleep" else "Add Sleep"

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

            SectionHeader("Type")
            ToggleChipGroup(
                options = SleepType.entries,
                selected = state.type,
                onSelect = { viewModel.updateType(it) },
                label = { if (it == SleepType.NAP) "Nap" else "Night Sleep" }
            )

            SectionHeader("Start")
            DateTimePickerRow(
                label = "Start Time",
                timestampMs = state.startTimeMs,
                onDateTimeSelected = { viewModel.updateStartTime(it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.stillAsleep,
                    onCheckedChange = { viewModel.updateStillAsleep(it) }
                )
                Text("Still asleep (no end time yet)")
            }

            if (!state.stillAsleep) {
                SectionHeader("End")
                DateTimePickerRow(
                    label = "End Time",
                    timestampMs = state.endTimeMs ?: state.startTimeMs,
                    onDateTimeSelected = { viewModel.updateEndTime(it) }
                )
            }

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

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { viewModel.saveSleep() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Sleep Session")
                    }
                }
            }

            if (sleepId > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
