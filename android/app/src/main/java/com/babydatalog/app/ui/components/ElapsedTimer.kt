package com.babydatalog.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Seconds elapsed since [referenceMs], ticking every second. 0 while [referenceMs] is null. */
@Composable
fun rememberElapsedSeconds(referenceMs: Long?): Long {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(referenceMs) {
        if (referenceMs != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - referenceMs) / 1000L
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }
    return elapsedSeconds
}

/** "2h 15m" once past an hour, otherwise "15m". */
fun formatElapsed(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) "%dh %02dm".format(hours, mins) else "%dm".format(mins)
}
