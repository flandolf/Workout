package com.flandolf.workout.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.flandolf.workout.data.CommonExercises
import com.flandolf.workout.data.ExerciseWithSets
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.viewmodel.EditWorkoutViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun EditWorkoutScreen(
    workout: List<ExerciseWithSets>,
    onBack: () -> Unit,
    onAddExercise: (String) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onAddSet: (String, Int, Float) -> Unit,
    onUpdateSet: (String, Int, Int, Float) -> Unit,
    onDeleteSet: (exerciseId: String, setIndex: Int) -> Unit,
    vm: EditWorkoutViewModel,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }

    val addSetVisibleMap = remember { mutableStateMapOf<String, Boolean>() }
    val editSetMap = remember { mutableStateMapOf<Pair<String, Int>, Boolean>() }
    // Visibility maps for animations
    val exerciseVisibleMap = remember { mutableStateMapOf<String, Boolean>() }
    val setVisibleMap = remember { mutableStateMapOf<Pair<String, Int>, Boolean>() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit Workout", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
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
                val visible = addExerciseVisible
                FilledTonalButton(
                    onClick = {
                        addExerciseVisible = !addExerciseVisible
                        if (!addExerciseVisible) newExerciseName = ""
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (visible) "Cancel" else "Add Exercise")
                }
            }

            AnimatedVisibility(visible = addExerciseVisible) {
                Column(Modifier.fillMaxWidth()) {
                    val commonExercises = CommonExercises().exercises

                    // Cache suggestions from the DB once and keep them in a state container.
                    val suggestionsState = remember { mutableStateOf<List<String>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        // load suggestions once; vm will call back with the list
                        vm.loadExerciseNameSuggestions { fromDb ->
                            // merge once and keep stable order; distinct to avoid duplicates
                            suggestionsState.value = (commonExercises + fromDb).distinct()
                        }
                    }

                    // --- Debounced filtering to avoid dropdown flicker while typing ---
                    // Precompute lowercase pairs to avoid repeated lowercasing of suggestions while typing.
                    val combinedSuggestionsList = remember(suggestionsState.value) {
                        (commonExercises + suggestionsState.value).distinct()
                    }
                    val combinedLowercase = remember(combinedSuggestionsList) {
                        combinedSuggestionsList.map { it to it.lowercase() }
                    }

                    // A small mutable list that will be populated by the debounced collector.
                    val filteredSuggestionsState = remember { mutableStateListOf<String>() }

                    // Local control for whether the dropdown should be visible.
                    var showDropdown by remember { mutableStateOf(false) }

                    // Debounce input and update filteredSuggestionsState after the user pauses typing.
                    LaunchedEffect(combinedLowercase) {
                        snapshotFlow { newExerciseName.trim() }
                            .debounce(250)
                            .collectLatest { query ->
                                filteredSuggestionsState.clear()
                                if (query.length < 2) {
                                    showDropdown = false
                                    return@collectLatest
                                }
                                val term = query.lowercase()
                                var added = 0
                                for ((orig, low) in combinedLowercase) {
                                    if (low.contains(term)) {
                                        filteredSuggestionsState.add(orig)
                                        added++
                                        if (added >= 6) break
                                    }
                                }
                                // Only show the dropdown if the query we filtered for still matches
                                // the current input. Add a short confirmation delay to avoid
                                // reopening the dropdown when the user continues typing quickly
                                // after the debounce interval.
                                val current = newExerciseName.trim()
                                if (current == query) {
                                    // Double-check stability: wait a short moment and make sure
                                    // the input hasn't changed before showing. This prevents
                                    // brief flashes/pops while the user types.
                                    delay(100)
                                    showDropdown = if (newExerciseName.trim() == query) {
                                        filteredSuggestionsState.isNotEmpty()
                                    } else {
                                        false
                                    }
                                } else {
                                    // User continued typing since the debounced value was captured.
                                    // Defer showing until the next debounce cycle completes.
                                    showDropdown = false
                                }
                            }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newExerciseName,
                                onValueChange = {
                                    // Update typed text immediately and hide the dropdown right away to
                                    // avoid it popping open while the user is still typing. The
                                    // debounced collector will repopulate and open it after the
                                    // user pauses for the debounce duration.
                                    newExerciseName = it
                                    // Immediately hide any visible dropdown for a smoother typing UX
                                    showDropdown = false
                                    // Clear stale results; the debounced collector will fill this when ready
                                    filteredSuggestionsState.clear()
                                },
                                label = { Text("Exercise name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            DropdownMenu(
                                expanded = showDropdown && filteredSuggestionsState.isNotEmpty(),
                                onDismissRequest = { showDropdown = false },
                                modifier = Modifier.fillMaxWidth(),
                                properties = PopupProperties(focusable = false)
                            ) {
                                // Render the precomputed, debounced list
                                filteredSuggestionsState.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = {
                                        newExerciseName = s
                                        showDropdown = false
                                        onAddExercise(s)
                                        newExerciseName = ""
                                        addExerciseVisible = false
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val name = newExerciseName.trim()
                            if (name.isNotBlank()) {
                                onAddExercise(name)
                                newExerciseName = ""
                                addExerciseVisible = false
                            }
                        }, modifier = Modifier.height(56.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
            }
            val sortedExercises = remember(workout) {
                workout.sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                .fillMaxWidth()
                                .animateContentSize(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ex.exercise.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            addSetVisibleMap[ex.exercise.id] =
                                                !(addSetVisibleMap[ex.exercise.id] ?: false)
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add set")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { vm.moveExerciseUp(ex.exercise.id) },
                                            enabled = idx > 0
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move up"
                                            )
                                        }
                                        IconButton(
                                            onClick = { vm.moveExerciseDown(ex.exercise.id) },
                                            enabled = idx < sortedExercises.lastIndex
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move down"
                                            )
                                        }
                                        IconButton(onClick = {
                                            val key = ex.exercise.id
                                            exerciseVisibleMap[key] = false
                                            coroutineScope.launch {
                                                delay(260)
                                                onDeleteExercise(key)
                                                exerciseVisibleMap.remove(key)
                                            }
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete exercise",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = addSetVisibleMap[ex.exercise.id] == true) {
                                    var reps by remember { mutableStateOf("") }
                                    var weight by remember { mutableStateOf("") }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = weight,
                                            onValueChange = { weight = it },
                                            label = { Text("Weight (kg)") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Next
                                            ),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = reps,
                                            onValueChange = { reps = it },
                                            label = { Text("Reps") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = {
                                            val r = reps.toIntOrNull() ?: 0
                                            val w = weight.toFloatOrNull() ?: 0f
                                            if (r > 0) {
                                                onAddSet(ex.exercise.id, r, w)
                                                reps = ""
                                                weight = ""
                                                addSetVisibleMap[ex.exercise.id] = false
                                                keyboardController?.hide()
                                            }
                                        }) {
                                            Text("Add")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Column(Modifier.fillMaxWidth()) {
                                    ex.sets.forEachIndexed { i, s ->
                                        val editing = editSetMap[ex.exercise.id to i] == true
                                        val setKey: Pair<String, Int> = ex.exercise.id to i
                                        val setVisible = setVisibleMap[setKey] ?: true
                                        if (!editing) {
                                            AnimatedVisibility(
                                                visible = setVisible,
                                                enter = fadeIn(tween(200)),
                                                exit = fadeOut(tween(200)) + shrinkVertically(
                                                    tween(
                                                        200
                                                    )
                                                )
                                            ) {
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
                                                        }) { Text("Edit") }
                                                        IconButton(onClick = {
                                                            setVisibleMap[setKey] = false
                                                            val capturedIndex = i
                                                            coroutineScope.launch {
                                                                delay(260)
                                                                onDeleteSet(
                                                                    ex.exercise.id,
                                                                    capturedIndex
                                                                )
                                                                setVisibleMap.remove(ex.exercise.id to capturedIndex)
                                                            }
                                                        }) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Delete set",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            var editReps by remember(editing) { mutableStateOf(s.reps.toString()) }
                                            var editWeight by remember(editing) { mutableStateOf(s.weight.toString()) }
                                            val focusRequester = remember { FocusRequester() }
                                            LaunchedEffect(Unit) {
                                                delay(50)
                                                try {
                                                    focusRequester.requestFocus()
                                                } catch (_: Exception) {
                                                }
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
                                                        .padding(vertical = 4.dp)
                                                        .focusRequester(focusRequester),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                OutlinedTextField(
                                                    value = editReps,
                                                    onValueChange = { editReps = it },
                                                    label = { Text("Reps") },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = 4.dp),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Done
                                                    ),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Button(onClick = {
                                                    val r = editReps.toIntOrNull() ?: 0
                                                    val w = editWeight.toFloatOrNull() ?: 0f
                                                    if (r > 0) {
                                                        onUpdateSet(ex.exercise.id, i, r, w)
                                                        editSetMap[ex.exercise.id to i] = false
                                                        keyboardController?.hide()
                                                    }
                                                }) { Text("Save") }
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
