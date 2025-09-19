package com.flandolf.workout.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.ExerciseWithSets

@SuppressLint("DefaultLocale")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorkoutScreen(
    elapsedSeconds: Long,
    currentExercises: List<ExerciseWithSets>,
    onStartTick: () -> Unit,
    onPauseTick: () -> Unit,
    onEndWorkout: () -> Unit,
    onAddExercise: (String) -> Unit,
    onAddSet: (exerciseId: Long, reps: Int, weight: Float) -> Unit,
    onDeleteExercise: (exerciseId: Long) -> Unit,
    isTimerRunning: Boolean
) {
    // Toggle add set input for each exercise
    val addSetVisibleMap = remember { mutableStateMapOf<Long, Boolean>() }
    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // No add set dialog

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Workout", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onEndWorkout,
                icon = { Icon(Icons.Default.Delete, contentDescription = "End Workout") },
                text = { Text("End Workout") },
                containerColor = MaterialTheme.colorScheme.error
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Elapsed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Toggle Button for Start/Pause
                    Button(
                        onClick = { if (isTimerRunning) onPauseTick() else onStartTick() },
                        modifier = Modifier
                    ) {
                        Text(if (isTimerRunning) "Pause" else "Start")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Exercises", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val addVisible = addExerciseVisible
                val rot by animateFloatAsState(if (addVisible) 45f else 0f)
                IconButton(onClick = { addExerciseVisible = !addExerciseVisible }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (addVisible) "Hide" else "Add Exercise",
                        modifier = Modifier.rotate(rot)
                    )
                }
            }

            AnimatedVisibility(visible = addExerciseVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newExerciseName,
                        onValueChange = { newExerciseName = it },
                        label = { Text("Exercise name", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("e.g. Bench Press", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (newExerciseName.isNotBlank()) {
                                onAddExercise(newExerciseName.trim())
                                newExerciseName = ""
                                addExerciseVisible = false
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add exercise",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (currentExercises.isEmpty()) {
                Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(currentExercises) { ex ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ex.exercise.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteExercise(ex.exercise.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Exercise",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    val isVisible = addSetVisibleMap[ex.exercise.id] == true
                                    val rotation by animateFloatAsState(if (isVisible) 45f else 0f)
                                    IconButton(
                                        onClick = {
                                            addSetVisibleMap[ex.exercise.id] = !isVisible
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = if (isVisible) "Hide Add Set" else "Show Add Set",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.rotate(rotation)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (ex.sets.isEmpty()) {
                                        Text("No sets yet.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        ex.sets.forEachIndexed { i, s ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Set ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                                                Text("${s.reps} reps", style = MaterialTheme.typography.bodyMedium)
                                                Text("${s.weight} kg", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = addSetVisibleMap[ex.exercise.id] == true) {
                                        var repsText by remember("reps_${ex.exercise.id}") { mutableStateOf("") }
                                        var weightText by remember("weight_${ex.exercise.id}") { mutableStateOf("") }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .animateContentSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = repsText,
                                                onValueChange = { repsText = it },
                                                label = { Text("Reps", style = MaterialTheme.typography.bodySmall) },
                                                placeholder = {
                                                    Text(
                                                        "10",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                                    .padding(vertical = 4.dp),
                                                shape = MaterialTheme.shapes.small,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Next
                                                ),
                                                keyboardActions = KeyboardActions(onNext = {
                                                    focusManager.moveFocus(
                                                        FocusDirection.Next
                                                    )
                                                }),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedTextField(
                                                value = weightText,
                                                onValueChange = { weightText = it },
                                                label = {
                                                    Text(
                                                        "Weight (kg)",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                placeholder = {
                                                    Text(
                                                        "60.0",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                                    .padding(vertical = 4.dp),
                                                shape = MaterialTheme.shapes.small,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    val reps = repsText.toIntOrNull() ?: 0
                                                    val weight = weightText.toFloatOrNull() ?: 0f
                                                    if (reps > 0) {
                                                        onAddSet(ex.exercise.id, reps, weight)
                                                        repsText = ""
                                                        weightText = ""
                                                        addSetVisibleMap[ex.exercise.id] = false
                                                    }
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Set",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}