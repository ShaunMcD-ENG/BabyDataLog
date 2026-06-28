package com.babydatalog.app.ui.screens.growth

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.viewmodel.GrowthViewModel
import com.babydatalog.app.viewmodel.WeightUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthFormScreen(
    measurementId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: GrowthViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Local text field state
    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var headCircText by remember { mutableStateOf("") }
    var footSizeText by remember { mutableStateOf("") }
    var handSizeText by remember { mutableStateOf("") }
    var legLengthText by remember { mutableStateOf("") }
    var armLengthText by remember { mutableStateOf("") }
    var backLengthText by remember { mutableStateOf("") }

    // Load existing measurement for edit
    LaunchedEffect(measurementId) {
        if (measurementId > 0L) {
            viewModel.loadMeasurement(measurementId)
        }
    }

    // Reformat weight text whenever grams value OR unit changes
    LaunchedEffect(formState.weightGrams, formState.weightUnit) {
        weightText = gramsToDisplay(formState.weightGrams, formState.weightUnit)
    }
    LaunchedEffect(formState.heightCm) {
        heightText = formState.heightCm?.toString() ?: ""
    }
    LaunchedEffect(formState.headCircumferenceCm) {
        headCircText = formState.headCircumferenceCm?.toString() ?: ""
    }
    LaunchedEffect(formState.footSizeMm) {
        footSizeText = formState.footSizeMm?.toString() ?: ""
    }
    LaunchedEffect(formState.handSizeMm) {
        handSizeText = formState.handSizeMm?.toString() ?: ""
    }
    LaunchedEffect(formState.legLengthCm) {
        legLengthText = formState.legLengthCm?.toString() ?: ""
    }
    LaunchedEffect(formState.armLengthCm) {
        armLengthText = formState.armLengthCm?.toString() ?: ""
    }
    LaunchedEffect(formState.backLengthCm) {
        backLengthText = formState.backLengthCm?.toString() ?: ""
    }

    // Navigate back on success
    LaunchedEffect(formState.saveSuccess) {
        if (formState.saveSuccess) {
            viewModel.resetForm()
            onNavigateBack()
        }
    }

    // Show error in snackbar
    LaunchedEffect(formState.error) {
        formState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val title = if (measurementId > 0L) "Edit Measurement" else "Log Measurement"

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
                label = "Measurement Date & Time",
                timestampMs = formState.timestampMs,
                onDateTimeSelected = { viewModel.updateTimestamp(it) }
            )

            // Weight
            SectionHeader("Weight (optional)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = formState.weightUnit == WeightUnit.GRAMS,
                    onClick = { viewModel.updateWeightUnit(WeightUnit.GRAMS) },
                    label = { Text("Grams") }
                )
                FilterChip(
                    selected = formState.weightUnit == WeightUnit.KG,
                    onClick = { viewModel.updateWeightUnit(WeightUnit.KG) },
                    label = { Text("kg") }
                )
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = { value ->
                    weightText = value.filter { it.isDigit() || it == '.' }
                    viewModel.updateWeightGrams(displayToGrams(value, formState.weightUnit))
                },
                label = { Text(if (formState.weightUnit == WeightUnit.GRAMS) "Weight (grams)" else "Weight (kg)") },
                placeholder = { Text(if (formState.weightUnit == WeightUnit.GRAMS) "e.g. 4200" else "e.g. 4.2") },
                suffix = { Text(if (formState.weightUnit == WeightUnit.GRAMS) "g" else "kg") },
                supportingText = { Text("Leave blank if not recording weight today") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (formState.weightUnit == WeightUnit.GRAMS) KeyboardType.Number else KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Height
            SectionHeader("Height (optional)")
            OutlinedTextField(
                value = heightText,
                onValueChange = { value ->
                    heightText = value
                    viewModel.updateHeightCm(value.toFloatOrNull())
                },
                label = { Text("Height (cm)") },
                suffix = { Text("cm") },
                supportingText = { Text("Leave blank if not recording height today") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Head Circumference
            SectionHeader("Head Circumference (optional)")
            OutlinedTextField(
                value = headCircText,
                onValueChange = { value ->
                    headCircText = value
                    viewModel.updateHeadCircumferenceCm(value.toFloatOrNull())
                },
                label = { Text("Head Circumference (cm)") },
                suffix = { Text("cm") },
                supportingText = { Text("Measured at health visitor checks") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Foot Size
            SectionHeader("Foot Size (optional)")
            OutlinedTextField(
                value = footSizeText,
                onValueChange = { value ->
                    footSizeText = value
                    viewModel.updateFootSizeMm(value.toIntOrNull())
                },
                label = { Text("Foot Size (mm)") },
                suffix = { Text("mm") },
                supportingText = { Text("Foot length in millimetres") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Hand Size
            SectionHeader("Hand Size (optional)")
            OutlinedTextField(
                value = handSizeText,
                onValueChange = { value ->
                    handSizeText = value
                    viewModel.updateHandSizeMm(value.toIntOrNull())
                },
                label = { Text("Hand Size (mm)") },
                suffix = { Text("mm") },
                supportingText = { Text("Hand length in millimetres") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Leg Length
            SectionHeader("Leg Length (optional)")
            OutlinedTextField(
                value = legLengthText,
                onValueChange = { value ->
                    legLengthText = value
                    viewModel.updateLegLengthCm(value.toFloatOrNull())
                },
                label = { Text("Leg Length (cm)") },
                suffix = { Text("cm") },
                supportingText = { Text("Leg length (hip to heel)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Arm Length
            SectionHeader("Arm Length (optional)")
            OutlinedTextField(
                value = armLengthText,
                onValueChange = { value ->
                    armLengthText = value
                    viewModel.updateArmLengthCm(value.toFloatOrNull())
                },
                label = { Text("Arm Length (cm)") },
                suffix = { Text("cm") },
                supportingText = { Text("Arm length (shoulder to wrist)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Back Length
            SectionHeader("Back Length (optional)")
            OutlinedTextField(
                value = backLengthText,
                onValueChange = { value ->
                    backLengthText = value
                    viewModel.updateBackLengthCm(value.toFloatOrNull())
                },
                label = { Text("Back Length (cm)") },
                suffix = { Text("cm") },
                supportingText = { Text("Crown to rump length") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Notes
            SectionHeader("Notes")
            OutlinedTextField(
                value = formState.notes,
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
                if (formState.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { viewModel.saveMeasurement() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Convert stored grams → display string for the current unit
private fun gramsToDisplay(grams: Int?, unit: WeightUnit): String {
    if (grams == null) return ""
    return when (unit) {
        WeightUnit.GRAMS -> grams.toString()
        WeightUnit.KG -> {
            val kg = grams / 1000.0
            "%.3f".format(kg).trimEnd('0').trimEnd('.')
        }
    }
}

// Convert user's display string → grams for storage
private fun displayToGrams(input: String, unit: WeightUnit): Int? {
    val cleaned = input.trim()
    if (cleaned.isEmpty()) return null
    return when (unit) {
        WeightUnit.GRAMS -> cleaned.toIntOrNull()
        WeightUnit.KG -> cleaned.toDoubleOrNull()?.let { (it * 1000).roundToInt() }
    }
}
