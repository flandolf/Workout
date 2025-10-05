package com.flandolf.workout.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flandolf.workout.data.CommonExercises
import com.flandolf.workout.data.ExerciseWithSets
import com.flandolf.workout.data.SetEntity
import com.flandolf.workout.data.TemplateWithExercises
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
fun WorkoutScreen(
    elapsedSeconds: Long,
    currentExercises: List<ExerciseWithSets>,
    onStartTick: () -> Unit,
    onPauseTick: () -> Unit,
    onEndWorkout: () -> Unit,
    onAddExercise: (String) -> Unit,
    exerciseNameSuggestions: List<String> = emptyList(),
    minLettersForSuggestions: Int = 4,
    onAddSet: (exerciseId: String, reps: Int, weight: Float) -> Unit,
    onUpdateSet: (exerciseId: String, setIndex: Int, reps: Int, weight: Float) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (exerciseId: String, setIndex: Int) -> Unit = { _, _ -> },
    onDeleteExercise: (exerciseId: String) -> Unit,
    isTimerRunning: Boolean,
    vm: WorkoutViewModel,
    previousBestSets: Map<String, SetEntity> = emptyMap(),
    onShowSnackbar: suspend (String) -> Unit, // new parameter to use root snackbar host
    // New callback: when a template is tapped in the grid
    // New callback: start a workout from template
    onStartFromTemplate: (String) -> Unit = {},
    // New: reactive templates flow from TemplateViewModel
    templatesFlow: Flow<List<TemplateWithExercises>> = kotlinx.coroutines.flow.flowOf()
) {
    // Toggle add set input for each exercise
    val addSetVisibleMap = remember { mutableStateMapOf<String, Boolean>() }
    // Visibility maps to animate deletions
    val exerciseVisibleMap = remember { mutableStateMapOf<String, Boolean>() }
    val setVisibleMap = remember { mutableStateMapOf<Pair<String, Int>, Boolean>() }
    val coroutineScope = rememberCoroutineScope()
    var addExerciseVisible by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val showCircularProgress = remember { mutableStateOf(false) }

    // Observe sync status for toast notifications
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()

    // Handle sync status changes
    LaunchedEffect(syncStatus) {
        Log.d("WorkoutScreen", "Sync status: $syncStatus")
        when (syncStatus) {
            is WorkoutViewModel.SyncStatus.Success -> {
                // Call the provided suspend lambda to show snackbar at root level
                onShowSnackbar("Workout synced successfully")
                vm.clearSyncStatus()
            }

            is WorkoutViewModel.SyncStatus.Error -> {
                onShowSnackbar("Workout sync error: ${(syncStatus as WorkoutViewModel.SyncStatus.Error).message}")
                vm.clearSyncStatus()
            }

            is WorkoutViewModel.SyncStatus.Syncing -> {
                showCircularProgress.value = true
                delay(800)
                showCircularProgress.value = false
            }

            WorkoutViewModel.SyncStatus.Idle -> {
                showCircularProgress.value = false
            }
        }
    }

    // collect templates reactively
    val templates by templatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val currentWorkoutId by vm.currentWorkoutId.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTimerRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }, navigationIcon = {
                        if (showCircularProgress.value) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },

                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = if (addVisible) "Hide" else "Add Exercise",
                                    modifier = Modifier.rotate(rot)
                                )
                            }
                            Button(
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
                            }

                            // End workout button: check for incomplete sets before allowing
                            val hasIncompleteSets by remember(currentExercises) {
                                derivedStateOf {
                                    currentExercises.any { ex ->
                                        ex.sets.isEmpty() || ex.sets.any { s -> s.reps <= 0 }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (hasIncompleteSets) {
                                        showDiscardDialog = true
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
                            }
                        }

                    })
            },
        ) { innerPadding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 0.dp)) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                    AnimatedVisibility(visible = addExerciseVisible) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val commonExercises = remember { CommonExercises().exercises }

                                // Combine common + dynamic suggestions and keep stable across recompositions.
                                val combinedSuggestions = remember(exerciseNameSuggestions) {
                                    (commonExercises + exerciseNameSuggestions).distinct()
                                }

                                // Precompute lowercase versions once to avoid lowercasing every item while typing.
                                val combinedLowercase = remember(combinedSuggestions) {
                                    combinedSuggestions.map { it to it.lowercase() }
                                }

                                // A state list that will be updated by a debounced snapshotFlow collector.
                                val filteredSuggestionsState = remember { mutableStateListOf<String>() }

                                // Debounce input changes and update filteredSuggestionsState on pause. This avoids
                                // doing filtering work on every keystroke and prevents UI jank.
                                LaunchedEffect(addExerciseVisible, combinedLowercase) {
                                    if (!addExerciseVisible) {
                                        filteredSuggestionsState.clear()
                                        return@LaunchedEffect
                                    }
                                    snapshotFlow { newExerciseName.trim() }
                                        .debounce(150)
                                        .collectLatest { query ->
                                            filteredSuggestionsState.clear()
                                            if (query.length < minLettersForSuggestions) return@collectLatest
                                            val term = query.lowercase()
                                            var added = 0
                                            for ((orig, low) in combinedLowercase) {
                                                if (low.contains(term)) {
                                                    filteredSuggestionsState.add(orig)
                                                    added++
                                                    if (added >= 6) break
                                                }
                                            }
                                        }
                                }

                                // Expose an immutable view for the UI to consume
                                val filteredSuggestions: List<String> = filteredSuggestionsState

                                var showSuggestions by remember { mutableStateOf(false) }

                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = newExerciseName,
                                        onValueChange = {
                                            newExerciseName = it
                                            showSuggestions = it.trim().length >= minLettersForSuggestions
                                        },
                                        label = { Text("Exercise name") },
                                        placeholder = { Text("e.g. Bench Press") },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    DropdownMenu(
                                        expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                                        onDismissRequest = { showSuggestions = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        // Use the cached, limited filtered list produced by the debounced collector
                                        filteredSuggestions.forEach { suggestion ->
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
                    if (currentWorkoutId == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (templates.isNotEmpty()) {
                                item {
                                    Text(
                                        "Templates",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }
                            itemsIndexed(templates, key = { _, tpl -> tpl.template.id }) { _, tpl ->
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clickable {
                                            onStartFromTemplate(tpl.template.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = tpl.template.name.ifBlank { "Untitled template" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        HorizontalDivider()

                                        // Show exercises as a vertical list with ellipsis. Limit to 3 items and show a +N more indicator.
                                        val exerciseCount = tpl.exercises.size
                                        val maxShown = 3
                                        val toShow = tpl.exercises.take(maxShown)

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            toShow.forEach { item ->
                                                Text(
                                                    text = item.exercise.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (exerciseCount > maxShown) {
                                                Text(
                                                    text = "+${exerciseCount - maxShown} more",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        return@Column

                    }

                    if (currentExercises.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        Spacer(modifier = Modifier.height(8.dp))
                        // Always render exercises sorted by position, then id
                        val sortedExercises by remember(currentExercises) {
                            derivedStateOf {
                                currentExercises.sortedWith(
                                    compareBy(
                                        { it.exercise.position },
                                        { it.exercise.id }
                                    )
                                )
                            }
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
                                    ExerciseItem(
                                        idx = idx,
                                        ex = ex,
                                        listState = listState,
                                        vm = vm,
                                        addSetVisibleMap = addSetVisibleMap,
                                        setVisibleMap = setVisibleMap,
                                        onAddSet = onAddSet,
                                        onUpdateSet = onUpdateSet,
                                        onDeleteSet = onDeleteSet,
                                        onDeleteExercise = onDeleteExercise,
                                        coroutineScope = coroutineScope,
                                        sortedExercises = sortedExercises,
                                        exerciseVisibleMap = exerciseVisibleMap,
                                        previousBestSets = previousBestSets,
                                        keyboardController = keyboardController,
                                        focusManager = focusManager,
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Workout") },
            text = { Text("You may have incomplete sets.\nAre you sure you want to discard this workout? This will delete it permanently.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        vm.discardWorkout()
                        onEndWorkout()
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                Button(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ExerciseItem(
    idx: Int,
    ex: ExerciseWithSets,
    listState: LazyListState,
    vm: WorkoutViewModel,
    addSetVisibleMap: SnapshotStateMap<String, Boolean>,
    setVisibleMap: SnapshotStateMap<Pair<String, Int>, Boolean>,
    onAddSet: (String, Int, Float) -> Unit,
    onUpdateSet: (String, Int, Int, Float) -> Unit,
    onDeleteSet: (String, Int) -> Unit,
    onDeleteExercise: (String) -> Unit,
    coroutineScope: CoroutineScope,
    sortedExercises: List<ExerciseWithSets>,
    exerciseVisibleMap: SnapshotStateMap<String, Boolean>,
    previousBestSets: Map<String, SetEntity>,
    keyboardController: SoftwareKeyboardController?,
    focusManager: FocusManager,
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
                        onClick = { onAddSet(ex.exercise.id, 0, 0f) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add set",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Reorder controls
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
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            exerciseVisibleMap[ex.exercise.id] = false
                            coroutineScope.launch {
                                delay(260)
                                onDeleteExercise(ex.exercise.id)
                                exerciseVisibleMap.remove(ex.exercise.id)
                            }
                        },
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

            if (ex.sets.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(
                        vertical = 8.dp
                    ),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                // Add-set inputs placed above the existing sets list for improved UX
                AnimatedVisibility(visible = addSetVisibleMap[ex.exercise.id] == true) {
                    val lastSet = ex.sets.lastOrNull()
                    val prefillReps = lastSet?.reps?.toString() ?: ""
                    val prefillWeight = lastSet?.weight?.toString() ?: ""

                    var repsText by remember(ex.exercise.id) {
                        mutableStateOf("")
                    }
                    var weightText by remember(ex.exercise.id) {
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
                            .padding(top = 8.dp, bottom = 4.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            placeholder = {
                                Text(
                                    "kg",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .focusRequester(focusRequester),
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = {
                                focusManager.moveFocus(FocusDirection.Next)
                            })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.TextField(
                            value = repsText,
                            onValueChange = { repsText = it },
                            placeholder = {
                                Text(
                                    "reps",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                val reps = repsText.toIntOrNull() ?: 0
                                val weight = weightText.toFloatOrNull() ?: 0f
                                if (reps > 0 || weight > 0f) {
                                    onAddSet(ex.exercise.id, reps, weight)
                                    repsText = ""
                                    weightText = ""
                                    addSetVisibleMap[ex.exercise.id] = false
                                }
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val reps = repsText.toIntOrNull() ?: 0
                                val weight = weightText.toFloatOrNull() ?: 0f
                                onAddSet(ex.exercise.id, reps, weight)
                                repsText = ""
                                weightText = ""
                                addSetVisibleMap[ex.exercise.id] = false
                            },
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add set",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }


                if (ex.sets.isNotEmpty()) {
                    ex.sets.forEachIndexed { i, s ->
                        val setKey: Pair<String, Int> = ex.exercise.id to i
                        val setVisible = setVisibleMap[setKey] ?: true

                        AnimatedVisibility(
                            visible = setVisible,
                            enter = fadeIn(tween(220)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                        ) {
                            {
                                setVisibleMap[ex.exercise.id to i] = false
                                val capturedIndex = i
                                coroutineScope.launch {
                                    delay(260)
                                    onDeleteSet(ex.exercise.id, capturedIndex)
                                    setVisibleMap.remove(ex.exercise.id to capturedIndex)
                                }
                            }
                            val dismissState = rememberSwipeToDismissBoxState(
                                initialValue = SwipeToDismissBoxValue.Settled,
                                positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                onDismiss = {
                                    setVisibleMap[ex.exercise.id to i] = false
                                    val capturedIndex = i
                                    coroutineScope.launch {
                                        delay(260)
                                        onDeleteSet(ex.exercise.id, capturedIndex)
                                        setVisibleMap.remove(ex.exercise.id to capturedIndex)
                                    }
                                },
                                content = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            tonalElevation = 1.dp,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "Set ${i + 1}",
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        var weightText by remember(s.weight) { mutableStateOf(s.weight.toString()) }
                                        var repsText by remember(s.reps) { mutableStateOf(s.reps.toString()) }
                                        var isCompleted by remember(s.reps) { mutableStateOf(s.reps > 0) }

                                        androidx.compose.material3.TextField(
                                            value = weightText,
                                            onValueChange = {
                                                weightText = it
                                                val weight = it.toFloatOrNull() ?: 0f
                                                val reps = repsText.toIntOrNull() ?: 0
                                                onUpdateSet(ex.exercise.id, i, reps, weight)
                                            },
                                            placeholder = {
                                                Text(
                                                    "kg",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .height(56.dp),
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.small,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Next
                                            ),
                                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        androidx.compose.material3.TextField(
                                            value = repsText,
                                            onValueChange = {
                                                repsText = it
                                                val reps = it.toIntOrNull() ?: 0
                                                val weight = weightText.toFloatOrNull() ?: 0f
                                                onUpdateSet(ex.exercise.id, i, reps, weight)
                                            },
                                            placeholder = {
                                                Text(
                                                    "reps",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(0.7f)
                                                .height(56.dp),
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.small,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        IconButton(
                                            onClick = {
                                                isCompleted = !isCompleted
                                                if (isCompleted && (repsText.toIntOrNull()
                                                        ?: 0) == 0
                                                ) {
                                                    repsText = "1"
                                                    onUpdateSet(
                                                        ex.exercise.id,
                                                        i,
                                                        1,
                                                        weightText.toFloatOrNull() ?: 0f
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = if (isCompleted) "Set completed" else "Mark set as completed",
                                                tint = if (isCompleted)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        if (i < ex.sets.lastIndex) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
