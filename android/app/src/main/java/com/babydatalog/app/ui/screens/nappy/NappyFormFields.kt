package com.babydatalog.app.ui.screens.nappy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.PooColour
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup

// Shared nappy-entry fields (time, wee/poo amounts, poo colour, notes) used by
// both the full-screen NappyFormScreen and the in-place quick-add sheet on
// the feeding screen, so the two stay in sync.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NappyFormFields(
    timestampMs: Long,
    onTimestampChange: (Long) -> Unit,
    weeAmount: NappyAmount,
    onWeeAmountChange: (NappyAmount) -> Unit,
    pooAmount: NappyAmount,
    onPooAmountChange: (NappyAmount) -> Unit,
    pooColour: PooColour?,
    onPooColourChange: (PooColour) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pooColourExpanded by remember { mutableStateOf(false) }
    val showPooColour = pooAmount != NappyAmount.NONE

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Date & Time")
        DateTimePickerRow(
            label = "Time",
            timestampMs = timestampMs,
            onDateTimeSelected = onTimestampChange
        )

        SectionHeader("Wee")
        ToggleChipGroup(
            options = NappyAmount.entries,
            selected = weeAmount,
            onSelect = onWeeAmountChange,
            label = { it.displayLabel() }
        )

        SectionHeader("Poo")
        ToggleChipGroup(
            options = NappyAmount.entries,
            selected = pooAmount,
            onSelect = onPooAmountChange,
            label = { it.displayLabel() }
        )

        if (showPooColour) {
            SectionHeader("Poo Colour")
            ExposedDropdownMenuBox(
                expanded = pooColourExpanded,
                onExpandedChange = { pooColourExpanded = !pooColourExpanded }
            ) {
                OutlinedTextField(
                    value = pooColour?.displayLabel() ?: "Select colour",
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
                            text = { Text(colour.displayLabel()) },
                            onClick = {
                                onPooColourChange(colour)
                                pooColourExpanded = false
                            }
                        )
                    }
                }
            }
        }

        SectionHeader("Notes")
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )
    }
}
