package com.flandolf.workout.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.flandolf.workout.data.CommonExercises
import com.flandolf.workout.data.ExerciseWithSets
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.delay

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
    exerciseNameSuggestions: List<String> = emptyList(),
    minLettersForSuggestions: Int = 4,
    onAddSet: (exerciseId: Long, reps: Int, weight: Float) -> Unit,
    onUpdateSet: (exerciseId: Long, setIndex: Int, reps: Int, weight: Float) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (exerciseId: Long, setIndex: Int) -> Unit = { _, _ -> },
    onDeleteExercise: (exerciseId: Long) -> Unit,
    isTimerRunning: Boolean,
    vm: WorkoutViewModel
) {
    // Toggle add set input for each exercise
    val addSetVisibleMap = remember { mutableStateMapOf<Long, Boolean>() }
    val editSetMap = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current


    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTimerRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (isTimerRunning) {
                                onPauseTick()
                            } else {
                                if (vm.currentWorkoutId.value == null) {
                                    vm.startWorkout()
                                } else {
                                    onStartTick()
                                }
                            }
                        },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTimerRunning) "Pause Timer" else "Start Timer",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isTimerRunning) "Pause" else "Start",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = onEndWorkout,
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "End Workout",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    "Exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val addVisible = addExerciseVisible
                val rot by animateFloatAsState(if (addVisible) 45f else 0f)
                FilledTonalButton(
                    onClick = {
                        if (vm.currentWorkoutId.value == null) {
                            vm.startWorkout()
                        }
                        addExerciseVisible = !addExerciseVisible
                        if (!addExerciseVisible) {
                            newExerciseName = ""
                        }
                        focusManager.clearFocus()
                        keyboardController?.hide()

                    }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (addVisible) "Hide" else "Add Exercise",
                        modifier = Modifier.rotate(rot)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (addVisible) "Cancel" else "Add Exercise")
                }
            }

            AnimatedVisibility(visible = addExerciseVisible) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val commonExercises = CommonExercises().exercises
                        val combinedSuggestions =
                            (commonExercises + exerciseNameSuggestions).distinct()
                        var showSuggestions by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newExerciseName,
                                onValueChange = {
                                    val charCount = it.count { ch -> !ch.isWhitespace() }
                                    newExerciseName = it
                                    showSuggestions = charCount >= minLettersForSuggestions
                                },
                                label = { Text("Exercise name") },
                                placeholder = { Text("e.g. Bench Press") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val charCount = newExerciseName.count { ch -> !ch.isWhitespace() }
                            DropdownMenu(
                                expanded = showSuggestions && charCount >= minLettersForSuggestions && combinedSuggestions.any {
                                    it.contains(
                                        newExerciseName, ignoreCase = true
                                    )
                                },
                                onDismissRequest = { showSuggestions = false },
                                modifier = Modifier.fillMaxWidth(),
                                properties = PopupProperties(focusable = false)
                            ) {
                                combinedSuggestions.filter {
                                    it.contains(
                                        newExerciseName, ignoreCase = true
                                    )
                                }.forEach { suggestion ->
                                    DropdownMenuItem(text = { Text(suggestion) }, onClick = {
                                        newExerciseName = suggestion
                                        showSuggestions = false
                                        // Automatically add the exercise when selected from dropdown
                                        if (suggestion.isNotBlank()) {
                                            onAddExercise(suggestion.trim())
                                            newExerciseName = ""
                                            addExerciseVisible = false
                                        }
                                    })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newExerciseName.isNotBlank()) {
                                    onAddExercise(newExerciseName.trim())
                                    newExerciseName = ""
                                    addExerciseVisible = false
                                } else {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            }, modifier = Modifier.size(48.dp), // keeps it square
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add exercise",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            if (currentExercises.isEmpty()) {
                Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val listState = rememberLazyListState()
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    itemsIndexed(currentExercises) { idx, ex ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            ex.exercise.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isVisible = addSetVisibleMap[ex.exercise.id] == true
                                        LaunchedEffect(isVisible) {
                                            if (isVisible) {
                                                listState.animateScrollToItem(idx)
                                            }
                                        }
                                        val rotation by animateFloatAsState(if (isVisible) 45f else 0f)
                                        IconButton(
                                            onClick = {
                                                addSetVisibleMap[ex.exercise.id] = !isVisible
                                            }, modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = if (isVisible) "Hide Add Set" else "Show Add Set",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.rotate(rotation)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteExercise(ex.exercise.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Exercise",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (ex.sets.isEmpty()) {
                                        Text(
                                            "No sets yet.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        ex.sets.forEachIndexed { i, s ->
                                            val editing = editSetMap[ex.exercise.id to i] == true
                                            if (!editing) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = MaterialTheme.shapes.small,
                                                            tonalElevation = 1.dp,
                                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                                            modifier = Modifier.padding(end = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = "Set ${i + 1}",
                                                                modifier = Modifier.padding(
                                                                    horizontal = 10.dp,
                                                                    vertical = 6.dp
                                                                ),
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }

                                                        Text(
                                                            "${formatWeight(s.weight)} kg",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            "${s.reps} reps",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )

                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        TextButton(onClick = {
                                                            editSetMap[ex.exercise.id to i] = true
                                                        }) {
                                                            Text("Edit")
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                onDeleteSet(
                                                                    ex.exercise.id, i
                                                                )
                                                            }, modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete set",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                var editReps by remember("edit_reps_${ex.exercise.id}_$i") {
                                                    mutableStateOf(
                                                        s.reps.toString()
                                                    )
                                                }
                                                var editWeight by remember("edit_weight_${ex.exercise.id}_$i") {
                                                    mutableStateOf(
                                                        s.weight.toString()
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = editWeight,
                                                        onValueChange = { editWeight = it },
                                                        label = { Text("Weight (kg)") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 56.dp)
                                                            .padding(vertical = 4.dp),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Decimal,
                                                            imeAction = ImeAction.Done
                                                        ),
                                                    )

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    OutlinedTextField(
                                                        value = editReps,
                                                        onValueChange = { editReps = it },
                                                        label = { Text("Reps") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 56.dp)
                                                            .padding(vertical = 4.dp),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number,
                                                            imeAction = ImeAction.Next
                                                        ),
                                                    )

                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    TextButton(onClick = {
                                                        val reps = editReps.toIntOrNull() ?: 0
                                                        val weight =
                                                            editWeight.toFloatOrNull() ?: 0f
                                                        if (reps > 0) {
                                                            onUpdateSet(
                                                                ex.exercise.id, i, reps, weight
                                                            )
                                                            editSetMap.remove(ex.exercise.id to i)
                                                        }
                                                    }) {
                                                        Text("Save")
                                                    }
                                                    TextButton(onClick = { editSetMap.remove(ex.exercise.id to i) }) {
                                                        Text("Cancel")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = addSetVisibleMap[ex.exercise.id] == true) {
                                        val lastSet = ex.sets.lastOrNull()
                                        val prefillReps = lastSet?.reps?.toString() ?: ""
                                        val prefillWeight = lastSet?.weight?.toString() ?: ""

                                        var repsText by remember("reps_${ex.exercise.id}") {
                                            mutableStateOf(
                                                ""
                                            )
                                        }
                                        var weightText by remember("weight_${ex.exercise.id}") {
                                            mutableStateOf(
                                                ""
                                            )
                                        }

                                        val focusRequester = remember { FocusRequester() }

                                        LaunchedEffect(addSetVisibleMap[ex.exercise.id]) {
                                            if (addSetVisibleMap[ex.exercise.id] == true) {
                                                repsText = prefillReps
                                                weightText = prefillWeight
                                                delay(150)
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .animateContentSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {


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
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 56.dp)
                                                    .padding(vertical = 4.dp)
                                                    .focusRequester(focusRequester),
                                                shape = MaterialTheme.shapes.small,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedTextField(
                                                value = repsText,
                                                onValueChange = { repsText = it },
                                                label = {
                                                    Text(
                                                        "Reps",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                placeholder = {
                                                    Text(
                                                        "10",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 56.dp)
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
                                                }, modifier = Modifier.size(40.dp)
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

