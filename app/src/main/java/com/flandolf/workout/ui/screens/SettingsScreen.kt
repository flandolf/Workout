package com.flandolf.workout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        LazyColumn (
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom=120.dp).fillMaxWidth()
        ) {
            item {
                if (syncViewModel != null && syncUiState != null) {
                    Text(
                        text = "Cloud Sync",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            headlineContent = { Text("Sync Settings", fontSize = 18.sp) },
                            supportingContent = {
                                Text("Manage cloud synchronization and backup for your workout data across devices.")
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inline sync settings
                    SyncSettingsScreen(
                        syncUiState,
                        syncViewModel,
                        showAuthDialog,
                        { syncViewModel.hideAuthDialog() },
                        onManualSync
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            item {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        headlineContent = { Text("Export Workout Data", fontSize = 18.sp) },
                        supportingContent = {
                            Text("Export all workouts as CSV file with detailed set information, volume calculations, and timestamps. File will be saved to Downloads and shared for easy access.")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onExportCsv() }
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        headlineContent = { Text("Import Strong Data", fontSize = 18.sp) },
                        supportingContent = {
                            Text("Import workouts from Strong App. This will add the workouts to your existing data without creating duplicates.")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onImportStrongCsv() }
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        headlineContent = { Text("Import Workout Data", fontSize = 18.sp) },
                        supportingContent = {
                            Text("Import workouts from this app. This will add the workouts to your existing data without creating duplicates.")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onImportWorkoutCsv() }
                    )
                }
            }
            item {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Reset All Data",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text(
                                "This will permanently delete all your workouts and exercises from this device. Cloud data will remain if you're signed in.",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { showResetDialog.value = true }
                    )
                }
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
