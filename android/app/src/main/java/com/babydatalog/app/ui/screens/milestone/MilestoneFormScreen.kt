package com.babydatalog.app.ui.screens.milestone

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.babydatalog.app.data.database.entity.MilestoneCategory
import com.babydatalog.app.ui.components.DateTimePickerRow
import com.babydatalog.app.ui.components.SectionHeader
import com.babydatalog.app.ui.components.ToggleChipGroup
import com.babydatalog.app.viewmodel.MilestoneViewModel

private fun categoryDisplayName(category: MilestoneCategory): String = when (category) {
    MilestoneCategory.DEVELOPMENT -> "Development"
    MilestoneCategory.MEDICAL -> "Medical"
    MilestoneCategory.SOCIAL -> "Social"
    MilestoneCategory.PHYSICAL -> "Physical"
    MilestoneCategory.FIRST_TIME -> "First Time"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneFormScreen(
    milestoneId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: MilestoneViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.updatePhotoUri(uri?.toString())
    }

    LaunchedEffect(milestoneId) {
        if (milestoneId > 0L) {
            viewModel.loadMilestone(milestoneId)
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

    val title = if (milestoneId > 0L) "Edit Milestone" else "Add Milestone"

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
                label = "Date & Time",
                timestampMs = state.timestampMs,
                onDateTimeSelected = { viewModel.updateTimestamp(it) }
            )

            // Category
            SectionHeader("Category")
            ToggleChipGroup(
                options = MilestoneCategory.entries,
                selected = state.category,
                onSelect = { viewModel.updateCategory(it) },
                label = { categoryDisplayName(it) }
            )

            // Title
            SectionHeader("Title")
            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title (required)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error != null && state.title.isBlank()
            )

            // Description
            SectionHeader("Description")
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            // Photo
            SectionHeader("Photo")
            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.photoUri != null) "Change Photo" else "Add Photo")
            }
            if (state.photoUri != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = state.photoUri,
                    contentDescription = "Milestone photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

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
                        onClick = { viewModel.saveMilestone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Milestone")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
