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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.CommonExercises
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
    exerciseNameSuggestions: List<String> = emptyList(),
    minWordsForSuggestions: Int = 1,
    onAddSet: (exerciseId: Long, reps: Int, weight: Float) -> Unit,
    onUpdateSet: (exerciseId: Long, setIndex: Int, reps: Int, weight: Float) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (exerciseId: Long, setIndex: Int) -> Unit = { _, _ -> },
    onDeleteExercise: (exerciseId: Long) -> Unit,
    isTimerRunning: Boolean
) {
    // Toggle add set input for each exercise
    val addSetVisibleMap = remember { mutableStateMapOf<Long, Boolean>() }
    val editSetMap = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // No add set dialog

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Workout", fontWeight = FontWeight.SemiBold) }) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                // Start/Pause and End Workout actions grouped at the right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (isTimerRunning) onPauseTick() else onStartTick() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isTimerRunning) "Pause" else "Start")
                    }
                    Button(
                        onClick = onEndWorkout,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "End Workout",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End")
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
                    val commonExercises = CommonExercises().exercises
                    val combinedSuggestions = (commonExercises + exerciseNameSuggestions).distinct()
                    var showSuggestions by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = newExerciseName,
                            onValueChange = {
                                val wc = it.trim().split("\\s+".toRegex()).count { w -> w.isNotBlank() }
                                newExerciseName = it
                                showSuggestions = wc >= minWordsForSuggestions
                            },
                            label = { Text("Exercise name", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("e.g. Bench Press", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )

                        val wordCount = newExerciseName.trim().split("\\s+".toRegex()).count { w -> w.isNotBlank() }
                        DropdownMenu(
                            expanded = showSuggestions && wordCount >= minWordsForSuggestions && combinedSuggestions.any {
                                it.contains(
                                    newExerciseName,
                                    ignoreCase = true
                                )
                            },
                            onDismissRequest = { showSuggestions = false },
                            modifier = Modifier.fillMaxWidth(),
                            properties = PopupProperties(focusable = false)
                        ) {
                            combinedSuggestions.filter { it.contains(newExerciseName, ignoreCase = true) }
                                .forEach { suggestion ->
                                    DropdownMenuItem(text = { Text(suggestion) }, onClick = {
                                        newExerciseName = suggestion
                                        showSuggestions = false
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
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            ex.exercise.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = "${ex.sets.size} sets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isVisible = addSetVisibleMap[ex.exercise.id] == true
                                        val rotation by animateFloatAsState(if (isVisible) 45f else 0f)
                                        IconButton(
                                            onClick = {
                                                addSetVisibleMap[ex.exercise.id] = !isVisible
                                            },
                                            modifier = Modifier.size(36.dp)
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
                                            modifier = Modifier.size(36.dp)
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
                                        Text("No sets yet.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        ex.sets.forEachIndexed { i, s ->
                                            val editing = editSetMap[ex.exercise.id to i] == true
                                            if (!editing) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
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
                                                            "${s.reps} reps",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            "${s.weight} kg",
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
                                                            onClick = { onDeleteSet(ex.exercise.id, i) },
                                                            modifier = Modifier.size(28.dp)
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
                                                        value = editReps,
                                                        onValueChange = { editReps = it },
                                                        label = { Text("Reps") },
                                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                                            .padding(vertical = 4.dp),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number,
                                                            imeAction = ImeAction.Next
                                                        ),
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    OutlinedTextField(
                                                        value = editWeight,
                                                        onValueChange = { editWeight = it },
                                                        label = { Text("Weight (kg)") },
                                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                                            .padding(vertical = 4.dp),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Decimal,
                                                            imeAction = ImeAction.Done
                                                        ),
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    TextButton(onClick = {
                                                        val reps = editReps.toIntOrNull() ?: 0
                                                        val weight = editWeight.toFloatOrNull() ?: 0f
                                                        if (reps > 0) {
                                                            onUpdateSet(ex.exercise.id, i, reps, weight)
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
                                        // Prefill with last set values when opening add-set row
                                        val lastSet = ex.sets.lastOrNull()
                                        val prefillReps = lastSet?.reps?.toString() ?: ""
                                        val prefillWeight = lastSet?.weight?.toString() ?: ""

                                        var repsText by remember("reps_${ex.exercise.id}") { mutableStateOf("") }
                                        var weightText by remember("weight_${ex.exercise.id}") { mutableStateOf("") }

                                        LaunchedEffect(addSetVisibleMap[ex.exercise.id]) {
                                            if (addSetVisibleMap[ex.exercise.id] == true) {
                                                repsText = prefillReps
                                                weightText = prefillWeight
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
                                                    .padding(vertical = 4.dp)
                                                    .onFocusChanged { state ->
                                                        if (state.isFocused && repsText == prefillReps && prefillReps.isNotEmpty()) {
                                                            repsText = ""
                                                        }
                                                    },
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
                                                    .padding(vertical = 4.dp)
                                                    .onFocusChanged { state ->
                                                        if (state.isFocused && weightText == prefillWeight && prefillWeight.isNotEmpty()) {
                                                            weightText = ""
                                                        }
                                                    },
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