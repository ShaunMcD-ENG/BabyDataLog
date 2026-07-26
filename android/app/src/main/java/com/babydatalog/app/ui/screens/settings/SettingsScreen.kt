package com.babydatalog.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.prefs.ThemeMode
import com.babydatalog.app.ui.components.ColorWheelPicker
import com.babydatalog.app.viewmodel.ExportViewModel
import com.babydatalog.app.viewmodel.SettingsViewModel
import java.time.LocalDate

private val AUTO_SAVE_INTERVAL_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)
private fun autoSaveLabel(minutes: Int) = if (minutes == 0) "Off" else "Every $minutes min"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExportViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val autoSaveMinutes by settingsViewModel.autoSaveIntervalMinutes.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val customColorArgb by settingsViewModel.customPrimaryColorArgb.collectAsStateWithLifecycle()

    // JSON export launcher
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportJson(context.contentResolver, it)
        }
    }

    // CSV export launcher (single document for combined CSV)
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportCsv(context.contentResolver, it)
        }
    }

    // JSON import launcher
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importJson(context.contentResolver, it)
        }
    }

    // Backup export launcher
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportBackupCsv(context.contentResolver, it)
        }
    }

    // Backup restore launcher
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBackupCsv(context.contentResolver, it)
        }
    }

    // Show success snackbar
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Show error snackbar
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Feeding Auto-Save",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "While a feeding timer is running, periodically save the start time in the " +
                    "background so a forgotten feeding isn't lost entirely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AutoSaveIntervalDropdown(
                selectedMinutes = autoSaveMinutes,
                onSelect = { settingsViewModel.setAutoSaveIntervalMinutes(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            AppearanceSection(
                themeMode = themeMode,
                customColorArgb = customColorArgb,
                onUseSystemColor = { settingsViewModel.useSystemColor() },
                onUseCustomColor = { settingsViewModel.useCustomColor(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.isLoading) {
                // Loading indicator shown once below
            } else {
                Button(
                    onClick = {
                        val filename = "babydatalog_backup_${LocalDate.now()}.csv"
                        exportBackupLauncher.launch(filename)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Backup")
                }

                OutlinedButton(
                    onClick = {
                        importBackupLauncher.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore from Backup")
                }

                Text(
                    text = "Backups include all data and are forward-compatible across app versions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Button(
                    onClick = {
                        exportJsonLauncher.launch("babydatalog_export.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export as JSON")
                }

                OutlinedButton(
                    onClick = {
                        exportCsvLauncher.launch("babydatalog_export.csv")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export as CSV")
                }

                OutlinedButton(
                    onClick = {
                        importJsonLauncher.launch("application/json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import from JSON")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Version 1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoSaveIntervalDropdown(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = autoSaveLabel(selectedMinutes),
            onValueChange = {},
            readOnly = true,
            label = { Text("Auto-save interval") },
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
            AUTO_SAVE_INTERVAL_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(autoSaveLabel(minutes)) },
                    onClick = {
                        onSelect(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    customColorArgb: Int,
    onUseSystemColor: () -> Unit,
    onUseCustomColor: (Int) -> Unit
) {
    var pickedColor by remember(customColorArgb) { mutableStateOf(Color(customColorArgb)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = onUseSystemColor,
                label = { Text("System Default") }
            )
            FilterChip(
                selected = themeMode == ThemeMode.CUSTOM,
                onClick = { onUseCustomColor(pickedColor.toArgb()) },
                label = { Text("Custom Colour") }
            )
        }

        if (themeMode == ThemeMode.CUSTOM) {
            ColorWheelPicker(
                initialColor = Color(customColorArgb),
                onColorChanged = { pickedColor = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Button(
                onClick = { onUseCustomColor(pickedColor.toArgb()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Colour")
            }
        } else {
            Text(
                text = "Following the system's Material You colour scheme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
