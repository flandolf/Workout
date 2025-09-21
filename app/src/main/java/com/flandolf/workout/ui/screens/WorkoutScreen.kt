package com.flandolf.workout.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.flandolf.workout.data.SetEntity
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    vm: WorkoutViewModel,
    previousBestSets: Map<String, SetEntity> = emptyMap()
) {
    // Toggle add set input for each exercise
    val addSetVisibleMap = remember { mutableStateMapOf<Long, Boolean>() }
    val editSetMap = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    // Visibility maps to animate deletions
    val exerciseVisibleMap = remember { mutableStateMapOf<Long, Boolean>() }
    val setVisibleMap = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    val coroutineScope = rememberCoroutineScope()
    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showIncompleteDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current


    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTimerRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }, actions = {
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

                    // Discard button
                    OutlinedButton(
                        onClick = { showDiscardDialog = true },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Discard Workout",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discard", style = MaterialTheme.typography.bodySmall)
                    }

                    // End workout button: check for incomplete sets before allowing
                    val hasIncompleteSets = remember(currentExercises) {
                        currentExercises.any { ex ->
                            ex.sets.isEmpty() || ex.sets.any { s -> s.reps <= 0 }
                        }
                    }

                    Button(
                        onClick = {
                            if (hasIncompleteSets) {
                                showIncompleteDialog = true
                            } else {
                                onEndWorkout()
                            }
                        },
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
                    Spacer(modifier = Modifier.width(4.dp))
                }

            })
        }) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 0.dp)
        ) {


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

            if (currentExercises.isNotEmpty()) {
                val listState = rememberLazyListState()
                Spacer(modifier = Modifier.height(8.dp))
                // Always render exercises sorted by position, then id
                val sortedExercises = remember(currentExercises) {
                    currentExercises.sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    itemsIndexed(sortedExercises, key = { _, ex -> ex.exercise.id }) { idx, ex ->
                        val exVisible = exerciseVisibleMap[ex.exercise.id] ?: true
                        AnimatedVisibility(
                            visible = exVisible,
                            enter = fadeIn(tween(220)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220)),
                            modifier = Modifier.animateItem(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
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
                                            FilledTonalButton(
                                                onClick = {
                                                    addSetVisibleMap[ex.exercise.id] = !isVisible
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = if (isVisible) "Cancel" else "Add set",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            // Reorder controls
                                            IconButton(onClick = { vm.moveExerciseUp(ex.exercise.id) }, enabled = idx > 0) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                            }
                                            IconButton(onClick = { vm.moveExerciseDown(ex.exercise.id) }, enabled = idx < sortedExercises.lastIndex) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(onClick = {
                                                // animate then delete
                                                val key = ex.exercise.id
                                                exerciseVisibleMap[key] = false
                                                coroutineScope.launch {
                                                    delay(260)
                                                    onDeleteExercise(key)
                                                    exerciseVisibleMap.remove(key)
                                                }
                                            }, modifier = Modifier.size(24.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Exercise",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    // Previous best set display
                                    val previousBest = previousBestSets[ex.exercise.name]
                                    if (previousBest != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                tonalElevation = 1.dp,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Previous",
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp, vertical = 4.dp
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Text(
                                                "${formatWeight(previousBest.weight)} kg × ${previousBest.reps} reps",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (ex.sets.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Add-set inputs placed above the existing sets list for improved UX
                                        AnimatedVisibility(visible = addSetVisibleMap[ex.exercise.id] == true) {
                                            val lastSet = ex.sets.lastOrNull()
                                            val prefillReps = lastSet?.reps?.toString() ?: ""
                                            val prefillWeight = lastSet?.weight?.toString() ?: ""

                                            var repsText by remember("reps_${ex.exercise.id}") {
                                                mutableStateOf("")
                                            }
                                            var weightText by remember("weight_${ex.exercise.id}") {
                                                mutableStateOf("")
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
                                                FilledTonalButton(
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
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add set", modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Add set")
                                                }
                                            }
                                        }


                                        if (ex.sets.isNotEmpty()) {
                                            ex.sets.forEachIndexed { i, s ->
                                                val editing = editSetMap[ex.exercise.id to i] == true
                                                // visibility state per set (defaults to true)
                                                val setVisible = setVisibleMap[ex.exercise.id to s.id] ?: true

                                                AnimatedVisibility(
                                                    visible = setVisible,
                                                    enter = fadeIn(tween(220)),
                                                    exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                                                ) {
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
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                    )
                                                                }

                                                                Text(
                                                                    "${formatWeight(s.weight)} kg × ${s.reps} reps",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.primary,
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
                                                                        // animate then delete
                                                                        setVisibleMap[ex.exercise.id to s.id] = false
                                                                        val capturedIndex = i
                                                                        coroutineScope.launch {
                                                                            delay(260)
                                                                            onDeleteSet(ex.exercise.id, capturedIndex)
                                                                            setVisibleMap.remove(ex.exercise.id to s.id)
                                                                        }
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
                                                        if (i < ex.sets.lastIndex) {
                                                            Spacer(Modifier.height(8.dp))
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                            Spacer(Modifier.height(8.dp))
                                                        }
                                                    } else {
                                                        // Reinitialize edit fields each time editing opens by using
                                                        // the editing boolean as the remember key. This ensures the
                                                        // fields are prefilled with the current set values.
                                                        var editReps by remember(editing) { mutableStateOf(s.reps.toString()) }
                                                        var editWeight by remember(editing) {
                                                            mutableStateOf(
                                                                s.weight.toString()
                                                            )
                                                        }

                                                        val editFocusRequester =
                                                            remember { FocusRequester() }

                                                        // When editing opens, request focus on the weight field and show keyboard
                                                        LaunchedEffect(Unit) {
                                                            // small delay to ensure the field is composed
                                                            delay(50)
                                                            try {
                                                                editFocusRequester.requestFocus()
                                                            } catch (_: Exception) {
                                                            }
                                                            keyboardController?.show()
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
                                                                    .padding(vertical = 4.dp)
                                                                    .focusRequester(editFocusRequester),
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
                                                                        ex.exercise.id,
                                                                        i,
                                                                        reps,
                                                                        weight
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

    // Discard confirmation dialog
    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard workout") },
            text = { Text("Are you sure you want to discard this workout? This will delete it permanently.") },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    // perform discard via ViewModel and then navigate back
                    vm.discardWorkout()
                    onEndWorkout()
                }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )) {
                    Text("Discard", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                Button(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Incomplete sets dialog
    if (showIncompleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showIncompleteDialog = false },
            title = { Text("Incomplete sets") },
            text = { Text("Some sets have missing reps or weight. Please complete or remove them before ending the workout.") },
            confirmButton = {
                Button(onClick = { showIncompleteDialog = false }) { Text("OK") }
            }
        )
    }
}
