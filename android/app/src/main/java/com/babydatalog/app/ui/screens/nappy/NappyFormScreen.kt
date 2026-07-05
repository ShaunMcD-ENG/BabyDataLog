package com.babydatalog.app.ui.screens.nappy

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babydatalog.app.viewmodel.NappyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NappyFormScreen(
    nappyId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: NappyViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            NappyFormFields(
                timestampMs = state.timestampMs,
                onTimestampChange = { viewModel.updateTimestamp(it) },
                weeAmount = state.weeAmount,
                onWeeAmountChange = { viewModel.updateWeeAmount(it) },
                pooAmount = state.pooAmount,
                onPooAmountChange = { viewModel.updatePooAmount(it) },
                pooColour = state.pooColour,
                onPooColourChange = { viewModel.updatePooColour(it) },
                notes = state.notes,
                onNotesChange = { viewModel.updateNotes(it) }
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
