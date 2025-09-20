package com.flandolf.workout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onExportCsv: () -> Unit, onResetAll: (() -> Unit)? = null
) {
    val showResetDialog = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Start)
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
                    modifier = Modifier.clickable { onExportCsv() })
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                ListItem(headlineContent = {
                    Text(
                        "Reset All Data", fontSize = 18.sp, color = MaterialTheme.colorScheme.error
                    )
                }, supportingContent = {
                    Text(
                        "This will permanently delete all your workouts and exercises.",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }, leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }, modifier = Modifier.clickable { showResetDialog.value = true })
            }
        }

        if (showResetDialog.value) {
            AlertDialog(
                onDismissRequest = { showResetDialog.value = false },
                title = { Text("Reset All Data", color = MaterialTheme.colorScheme.error) },
                text = { Text("Are you sure you want to permanently delete all workout data? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog.value = false
                            onResetAll?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete Everything", color = Color.White) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResetDialog.value = false }) { Text("Cancel") }
                })
        }
    }
}
