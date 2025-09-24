package com.flandolf.workout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flandolf.workout.ui.viewmodel.SyncViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onExportCsv: () -> Unit,
    onImportStrongCsv: () -> Unit,
    onImportWorkoutCsv: () -> Unit,
    onResetAll: (() -> Unit)? = null,
    syncViewModel: SyncViewModel? = null,
    onManualSync: (() -> Unit)? = null
) {
    val showResetDialog = remember { mutableStateOf(false) }

    // Collect sync state
    val syncUiState = syncViewModel?.uiState?.collectAsState()?.value
    val showAuthDialog = syncViewModel?.showAuthDialog?.collectAsState()?.value ?: false

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 80.dp)
                .fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (syncViewModel != null && syncUiState != null) {
                    Text(
                        text = "Cloud Sync",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SyncSettingsScreen(
                        syncUiState,
                        syncViewModel,
                        showAuthDialog,
                        { syncViewModel.hideAuthDialog() },
                        onManualSync
                    )
                }
            }
            item {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                SettingsCard(
                    action = onExportCsv,
                    icon = Icons.Default.Download,
                    title = "Export Workout Data",
                    description = "Export all your workout data to a CSV file. This can be imported back into the app later."
                )
            }
            item {
                SettingsCard(
                    action = onImportStrongCsv,
                    icon = Icons.Default.Upload,
                    title = "Import from Strong App",
                    description = "Import workouts from the Strong app using a CSV export from Strong. This will add the workouts to your existing data without creating duplicates."
                )
            }
            item {
                SettingsCard(
                    action = onImportWorkoutCsv,
                    icon = Icons.Default.Upload,
                    title = "Import from Workout App",
                    description = "Import workouts from the Workout app using a CSV export from Workout. This will add the workouts to your existing data without creating duplicates."
                )
            }
            item {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            item {
                SettingsCard(
                    action = { showResetDialog.value = true },
                    icon = Icons.Default.Delete,
                    title = "Reset All Data",
                    description = "Permanently delete all workout data from this device. This action cannot be undone.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (showResetDialog.value) {
            AlertDialog(
                onDismissRequest = { showResetDialog.value = false },
                title = { Text("Reset All Data", color = MaterialTheme.colorScheme.error) },
                text = {
                    Text(
                        "Are you sure you want to permanently delete all workout data from this device? " +
                                "This action cannot be undone. If you're signed in to cloud sync, your data will remain " +
                                "in the cloud and can be re-synced later."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog.value = false
                            onResetAll?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Everything", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResetDialog.value = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsCard(
    action: () -> Unit,
    icon: ImageVector,
    title: String,
    description: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ListItem(
            headlineContent = { Text(title, fontSize = 18.sp) },
            supportingContent = {
                Text(description)
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color
                )
            },
            modifier = Modifier.clickable { action() }
        )
    }
}