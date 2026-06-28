package com.babydatalog.app.ui.screens.nappy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyType
import com.babydatalog.app.data.database.entity.PooColour
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup
import com.babydatalog.app.viewmodel.NappyViewModel

private fun pooColourDisplayName(colour: PooColour): String = when (colour) {
    PooColour.NA -> "N/A"
    PooColour.MECONIUM -> "Newborn Black (Meconium)"
    PooColour.DARK_GREEN -> "Dark Green / Brown"
    PooColour.YELLOW_SEEDY -> "Yellow Seedy (normal breastfed)"
    PooColour.BRIGHT_YELLOW -> "Bright Yellow"
    PooColour.GREEN -> "Green"
    PooColour.BROWN -> "Brown"
    PooColour.PALE_WHITE -> "Pale / White ⚠ See doctor"
    PooColour.RED_BLOOD -> "Red / Blood ⚠ See doctor"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NappyFormScreen(
    nappyId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: NappyViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pooColourExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(nappyId) {
        if (nappyId > 0L) {
            viewModel.loadNappy(nappyId)
        }
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

    val title = if (nappyId > 0L) "Edit Nappy Change" else "Add Nappy Change"
    val showPooColour = state.type == NappyType.POO || state.type == NappyType.BOTH

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
                label = "Time",
                timestampMs = state.timestampMs,
                onDateTimeSelected = { viewModel.updateTimestamp(it) }
            )

            // Contents
            SectionHeader("Contents")
            ToggleChipGroup(
                options = NappyType.entries,
                selected = state.type,
                onSelect = { viewModel.updateType(it) },
                label = { type ->
                    when (type) {
                        NappyType.PEE -> "Pee"
                        NappyType.POO -> "Poo"
                        NappyType.BOTH -> "Pee + Poo"
                    }
                }
            )

            // Amount
            SectionHeader("Amount")
            ToggleChipGroup(
                options = NappyAmount.entries,
                selected = state.amount,
                onSelect = { viewModel.updateAmount(it) },
                label = { amount ->
                    when (amount) {
                        NappyAmount.SMALL -> "Small"
                        NappyAmount.LARGE -> "Large"
                    }
                }
            )

            // Poo Colour (only when POO or BOTH)
            if (showPooColour) {
                SectionHeader("Poo Colour")
                ExposedDropdownMenuBox(
                    expanded = pooColourExpanded,
                    onExpandedChange = { pooColourExpanded = !pooColourExpanded }
                ) {
                    OutlinedTextField(
                        value = state.pooColour?.let { pooColourDisplayName(it) } ?: "Select colour",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Poo Colour") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pooColourExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = pooColourExpanded,
                        onDismissRequest = { pooColourExpanded = false }
                    ) {
                        PooColour.entries.forEach { colour ->
                            DropdownMenuItem(
                                text = { Text(pooColourDisplayName(colour)) },
                                onClick = {
                                    viewModel.updatePooColour(colour)
                                    pooColourExpanded = false
                                }
                            )
                        }
                    }
                }
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
                        onClick = { viewModel.saveNappy() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Nappy Change")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
