package com.babydatalog.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.babydatalog.app.utils.toDisplayDateTime
import java.util.Calendar

@Composable
fun DateTimePickerRow(
    label: String,
    timestampMs: Long,
    onDateTimeSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val scope = rememberCoroutineScope()

    val displayText = timestampMs.toDisplayDateTime()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val calendar = Calendar.getInstance().apply { timeInMillis = timestampMs }

                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                DatePickerDialog(context, { _, selYear, selMonth, selDay ->
                    TimePickerDialog(context, { _, selHour, selMinute ->
                        val selectedCal = Calendar.getInstance().apply {
                            set(selYear, selMonth, selDay, selHour, selMinute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onDateTimeSelected(selectedCal.timeInMillis)
                    }, hour, minute, true).show()
                }, year, month, day).show()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Pick date and time",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
