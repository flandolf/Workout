package com.flandolf.workout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.sync.AuthState
import com.flandolf.workout.ui.viewmodel.SyncUiState
import com.flandolf.workout.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    uiState: SyncUiState,
    syncViewModel: SyncViewModel,
    showAuthDialog: Boolean,
    onDismissAuthDialog: () -> Unit,
    onManualSync: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Authentication Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.authState == AuthState.AUTHENTICATED) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.authState == AuthState.AUTHENTICATED) {
                            Icons.Default.CloudDone
                        } else {
                            Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = if (uiState.authState == AuthState.AUTHENTICATED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (uiState.authState) {
                                AuthState.AUTHENTICATED -> "Sync Enabled"
                                AuthState.UNAUTHENTICATED -> "Sync Disabled"
                                AuthState.LOADING -> "Loading..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = when (uiState.authState) {
                                AuthState.AUTHENTICATED -> {
                                    val email = syncViewModel.getCurrentUserEmail()
                                    if (email != null) {
                                        "Signed in as: $email"
                                    } else {
                                        "Authenticated user"
                                    }
                                }

                                AuthState.UNAUTHENTICATED -> "Sign in to sync your workouts across devices"
                                AuthState.LOADING -> "Checking authentication status..."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (uiState.authState == AuthState.AUTHENTICATED) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onManualSync?.invoke() ?: syncViewModel.performSync() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now")
                            }
                        }

                        Button(
                            onClick = { syncViewModel.signOut() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                        Button(
                            onClick = {
                                // Show confirmation dialog before nuking data
                                syncViewModel.nukeFirestoreData()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuke Cloud Data")
                        }
                    }
                }
            }
        }

        // Show a live "sync in progress" card when a sync is running
        if (uiState.isSyncing && uiState.syncStartTime > 0L) {
            // Local clock that updates every second while syncing so we can show duration
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(true, uiState.syncStartTime) {
                if (uiState.isSyncing) {
                    while (true) {
                        delay(1000L)
                        now = System.currentTimeMillis()
                    }
                }
            }

            val elapsedSec = ((now - uiState.syncStartTime) / 1000).coerceAtLeast(0L)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        "Sync in progress (${elapsedSec}s)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Sync Status Card
        if (uiState.authState == AuthState.AUTHENTICATED) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Sync Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    SyncStatusRow(
                        "Connection",
                        if (uiState.syncStatus.isOnline) "Online" else "Offline"
                    )
                    SyncStatusRow("Local Workouts", "${uiState.localWorkoutCount}")
                    SyncStatusRow("Server Workouts", "${uiState.remoteWorkoutCount}")

                    if (uiState.syncStatus.lastSyncTime > 0) {
                        val lastSync = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(Date(uiState.syncStatus.lastSyncTime))
                        SyncStatusRow("Last Sync", lastSync)
                    }

                    if (uiState.syncStatus.pendingUploads > 0) {
                        SyncStatusRow("Pending Uploads", "${uiState.syncStatus.pendingUploads}")
                    }

                    if (uiState.syncStatus.hasConflicts) {
                        SyncStatusRow("Status", "Conflicts detected", isError = true)
                    }

                    uiState.syncStatus.errorMessage?.let { error ->
                        SyncStatusRow("Error", error, isError = true)
                    }
                }
            }
        }

        // Authentication Actions
        if (uiState.authState == AuthState.UNAUTHENTICATED) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Sign In Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { syncViewModel.showAuthDialog() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Email")
                        }

                        // Anonymous sign-in removed; only email/provider sign-in available
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Email accounts enable full cross-device sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Processing...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Messages
        uiState.message?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { syncViewModel.clearMessages() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Column {
                        TextButton(onClick = { syncViewModel.clearMessages() }) {
                            Text("Dismiss")
                        }
                        // If the error looks like a network issue, offer a retry by reopening the auth dialog
                        if (error.contains(
                                "network",
                                ignoreCase = true
                            ) || error.contains("timeout", ignoreCase = true)
                        ) {
                            TextButton(onClick = { syncViewModel.showAuthDialog() }) {
                                Text("Retry Sign In")
                            }
                        }
                    }
                }
            }
        }
    }

    // Authentication Dialog
    if (showAuthDialog) {
        AuthDialog(
            onDismiss = onDismissAuthDialog,
            onSignIn = { email, password -> syncViewModel.signInWithEmail(email, password) },
            onCreateAccount = { email, password -> syncViewModel.createAccount(email, password) },
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun SyncStatusRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun AuthDialog(
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isCreatingAccount) "Create Account" else "Sign In")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { isCreatingAccount = !isCreatingAccount },
                        enabled = !isLoading
                    ) {
                        Text(
                            if (isCreatingAccount) "Already have an account? Sign in"
                            else "Don't have an account? Create one"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCreatingAccount) {
                        onCreateAccount(email, password)
                    } else {
                        onSignIn(email, password)
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(if (isCreatingAccount) "Create Account" else "Sign In")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}