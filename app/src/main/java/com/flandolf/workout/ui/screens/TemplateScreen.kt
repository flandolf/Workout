package com.flandolf.workout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.Template
import com.flandolf.workout.data.TemplateWithExercises
import com.flandolf.workout.ui.viewmodel.TemplateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    vm: TemplateViewModel
) {
    val showAddTemplateDialogState = remember { mutableStateOf(false) }

    // Call the suspend function inside produceState so it's executed from a coroutine-safe scope.
    val templates: List<TemplateWithExercises> by produceState(
        initialValue = emptyList(),
        key1 = vm,
        key2 = showAddTemplateDialogState.value
    ) {
        value = vm.getAllTemplates()
    }

    val showAddTemplateDialog = showAddTemplateDialogState

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                actions = {
                    IconButton(
                        onClick = {
                            showAddTemplateDialog.value = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Template"
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (templates.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No templates",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(templates.size) { index ->
                        val tpl = templates[index]
                        Card (
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = tpl.template.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Template Dialog (very simple)
        if (showAddTemplateDialog.value) {
            var name by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddTemplateDialog.value = false },
                title = { Text("Add Template") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Template name") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isNotEmpty()) {
                                coroutineScope.launch {
                                    try {
                                        vm.insertTemplate(Template(name = trimmed))
                                    } catch (_: Exception) {
                                        // ignore failures for this simple dialog
                                    }
                                    // close dialog (this also triggers the produceState refresh via key)
                                    showAddTemplateDialog.value = false
                                }
                            } else {
                                // don't save empty name; just close
                                showAddTemplateDialog.value = false
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTemplateDialog.value = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

}
