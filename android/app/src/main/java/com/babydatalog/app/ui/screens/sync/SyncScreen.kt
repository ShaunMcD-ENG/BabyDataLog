package com.babydatalog.app.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.viewmodel.SyncUiState
import com.babydatalog.app.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncScreen(viewModel: SyncViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Sync",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        when (val state = uiState) {
            is SyncUiState.NotConfigured -> NotConfiguredContent(onConnect = viewModel::connect)
            is SyncUiState.Pending -> PendingContent(state = state, onCancel = viewModel::disconnect)
            is SyncUiState.Connected -> ConnectedContent(state = state, onSync = viewModel::syncNow, onWipeAndResync = viewModel::wipeAndResync, onDisconnect = viewModel::disconnect)
            is SyncUiState.Syncing -> SyncingContent(state = state)
            is SyncUiState.Error -> ErrorContent(state = state, onDismiss = viewModel::dismissError)
        }
    }
}

@Composable
private fun NotConfiguredContent(onConnect: (url: String, name: String) -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }

    Text(
        text = "Connect to a BabyDataLog sync server to keep your data in sync with other devices.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 28.dp, top = 8.dp)
    )

    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server URL") },
        placeholder = { Text("http://192.168.1.100:3000") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )

    OutlinedTextField(
        value = deviceName,
        onValueChange = { deviceName = it },
        label = { Text("Device Name") },
        placeholder = { Text("Shaun's Pixel 8") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    )

    Button(
        onClick = { onConnect(serverUrl.trim(), deviceName.trim()) },
        enabled = serverUrl.isNotBlank() && deviceName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Connect")
    }
}

@Composable
private fun PendingContent(state: SyncUiState.Pending, onCancel: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Waiting for Approval",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        text = "Open the server dashboard and approve \"${state.deviceName}\". Enter the pairing code shown below to verify this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 28.dp)
    )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 32.dp)
        ) {
            Text(
                text = "Pairing Code",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.pairingCode,
                fontSize = 42.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Checking for approval...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@Composable
private fun ConnectedContent(
    state: SyncUiState.Connected,
    onSync: () -> Unit,
    onWipeAndResync: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showWipeConfirm by remember { mutableStateOf(false) }
    var secondsUntilSync by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.lastSyncMs) {
        if (state.lastSyncMs <= 0L) { secondsUntilSync = 0L; return@LaunchedEffect }
        while (true) {
            val nextMs = state.lastSyncMs + AUTO_SYNC_INTERVAL_MS
            secondsUntilSync = maxOf(0L, (nextMs - System.currentTimeMillis()) / 1000L)
            delay(1_000L)
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe & Resync?") },
            text = { Text("This will delete all local data on this device and re-download everything from the server. Any unsynced local changes will be lost.") },
            confirmButton = {
                Button(
                    onClick = { showWipeConfirm = false; onWipeAndResync() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe & Resync") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Spacer(Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Connected as ${state.deviceName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = state.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.lastSyncMs > 0) "Last sync: ${formatSyncTime(state.lastSyncMs)}" else "Never synced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.lastSyncMs > 0L) {
            Text(
                text = if (secondsUntilSync > 0L) "Next: ${formatCountdown(secondsUntilSync)}" else "Syncing soon…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.syncError?.let { error ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    Button(onClick = onSync, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sync Now")
    }

    OutlinedButton(
        onClick = { showWipeConfirm = true },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text("Wipe Local & Resync")
    }

    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("Disconnect")
    }
}

@Composable
private fun SyncingContent(state: SyncUiState.Syncing) {
    Spacer(Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Connected as ${state.deviceName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = state.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Syncing data...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }

    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Syncing...")
    }
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text("Disconnect")
    }
}

@Composable
private fun ErrorContent(state: SyncUiState.Error, onDismiss: () -> Unit) {
    Spacer(Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.canRetry) "Try Again" else "Back")
    }
}

private const val AUTO_SYNC_INTERVAL_MS = 30 * 60 * 1000L

private fun formatSyncTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))
    }
}

private fun formatCountdown(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
