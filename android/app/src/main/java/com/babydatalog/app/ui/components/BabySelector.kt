package com.babydatalog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.babydatalog.app.data.database.entity.Baby

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabySelector(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(50)

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .clip(pillShape)
                .background(Color(0xFF424242), pillShape)
                .padding(horizontal = 4.dp)
        ) {
            Text(text = selectedBaby?.name ?: "Select Baby", color = Color.White)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select baby",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            babies.forEach { baby ->
                DropdownMenuItem(
                    text = { Text(baby.name) },
                    onClick = {
                        onSelectBaby(baby)
                        expanded = false
                    }
                )
            }

            if (babies.isNotEmpty()) {
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = { Text("+ Add Baby") },
                onClick = {
                    onAddBaby()
                    expanded = false
                }
            )
        }
    }
}
