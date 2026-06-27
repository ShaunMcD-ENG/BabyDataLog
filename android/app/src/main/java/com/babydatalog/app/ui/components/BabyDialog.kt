package com.babydatalog.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babydatalog.app.utils.toDisplayDate
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBabyDialog(
    isEditing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    birthDateMs: Long?,
    onBirthDateChange: (Long?) -> Unit,
    birthWeightGrams: Int?,
    onBirthWeightChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Baby" else "Add Baby") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Birth date picker trigger
                val birthDateDisplay = birthDateMs?.toDisplayDate() ?: "Not set"
                OutlinedTextField(
                    value = birthDateDisplay,
                    onValueChange = {},
                    label = { Text("Birth Date (optional)") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    if (birthDateMs != null) timeInMillis = birthDateMs
                                }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selected = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth, 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        onBirthDateChange(selected.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Text(if (birthDateMs != null) "Change" else "Pick")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = birthWeightGrams?.toString() ?: "",
                    onValueChange = { text ->
                        onBirthWeightChange(text.toIntOrNull())
                    },
                    label = { Text("Birth Weight (grams, optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Baby", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
