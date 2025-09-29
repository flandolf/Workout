package com.flandolf.workout.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.components.BarChart
import com.flandolf.workout.ui.components.EmptyStateCard
import com.flandolf.workout.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class SortOption(val label: String) {
    REPS("Reps"),
    SETS("Sets"),
    VOLUME("Volume")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun ProgressScreen(
    workouts: List<WorkoutWithExercises>,
    viewModel: HistoryViewModel? = null,
    onExerciseClick: (String) -> Unit = {}
) {
    // Aggregate total reps, sets, and weight lifted per exercise name
    val totals by remember(workouts) {
        derivedStateOf {
            val map = mutableMapOf<String, Triple<Int, Int, Float>>() // name -> (reps, sets, kg)
            var totalVolumeKg = 0.0
            var totalReps = 0

            for (w in workouts) {
                for (ex in w.exercises) {
                    val (oldReps, oldSets, oldVolume) = map.getOrDefault(
                        ex.exercise.name,
                        Triple(0, 0, 0f)
                    )
                    var exerciseReps = 0
                    var exerciseVolume = 0.0

                    for (s in ex.sets) {
                        val setVolume = s.reps * s.weight.toDouble()
                        exerciseReps += s.reps
                        exerciseVolume += setVolume
                    }

                    val updatedVolume = oldVolume.toDouble() + exerciseVolume

                    map[ex.exercise.name] =
                        Triple(
                            oldReps + exerciseReps,
                            oldSets + ex.sets.size,
                            updatedVolume.toFloat()
                        )
                    totalReps += exerciseReps
                    totalVolumeKg += exerciseVolume
                }
            }
            map to Pair(totalVolumeKg.toFloat(), totalReps)
        }
    }

    val (exerciseTotals, lifetimeStats) = totals
    val (totalKgLifted, totalReps) = lifetimeStats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = if (exerciseTotals.isEmpty()) Arrangement.Center else Arrangement.Top
        ) {
            if (exerciseTotals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Default.BarChart,
                        title = "No progress yet",
                        message = "Start logging workouts to see your progress!",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            } else {
                // Progress Summary Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )

                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Your Progress",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Lifetime Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Lifetime Stats",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    "${formatWeight(totalKgLifted)} kg",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Total lifted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Spacer(modifier = Modifier.height(28.dp)) // Align with title
                                Text(
                                    "$totalReps",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Total reps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Workout Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "${workouts.size} workouts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${exerciseTotals.size} exercises",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Workouts per Week Chart
                val workoutsPerWeek by remember(workouts) {
                    derivedStateOf {
                        val calendar = Calendar.getInstance()
                        val weekMap = mutableMapOf<String, Int>()

                        workouts.forEach { workout ->
                            calendar.timeInMillis = workout.workout.date
                            val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
                            val year = calendar.get(Calendar.YEAR)
                            val weekKey = "$year-W${weekOfYear.toString().padStart(2, '0')}"

                            weekMap[weekKey] = weekMap.getOrDefault(weekKey, 0) + 1
                        }

                        // Get last 8 weeks
                        val sortedWeeks = weekMap.entries
                            .sortedByDescending { it.key }
                            .take(8)
                            .sortedBy { it.key }
                            .map { it.key.takeLast(3) to it.value.toFloat() } // Just show W01, W02, etc.

                        sortedWeeks
                    }
                }

                if (workoutsPerWeek.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Workouts Per Week",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            BarChart(
                                dataPoints = workoutsPerWeek,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                barColor = MaterialTheme.colorScheme.secondary,
                                valueFormatter = { it.toInt().toString() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Last 8 weeks of activity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                var searchQuery by remember { mutableStateOf("") }
                var sortOption by remember { mutableStateOf(SortOption.REPS) }
                var isSortMenuExpanded by remember { mutableStateOf(false) }
                val totalExercises = exerciseTotals.size

                val filteredExercises by remember(searchQuery, sortOption, exerciseTotals) {
                    derivedStateOf {
                        val comparator = when (sortOption) {
                            SortOption.REPS -> compareByDescending { it.value.first }
                            SortOption.SETS -> compareByDescending<Map.Entry<String, Triple<Int, Int, Float>>> { it.value.second }
                            SortOption.VOLUME -> compareByDescending { it.value.third }
                        }

                        exerciseTotals.entries
                            .filter {
                                searchQuery.isBlank() || it.key.contains(
                                    searchQuery,
                                    ignoreCase = true
                                )
                            }
                            .sortedWith(comparator.thenBy { it.key })
                    }
                }

                val resultCount = filteredExercises.size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search exercises") },
                        placeholder = { Text("Type an exercise name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }),
                        shape = MaterialTheme.shapes.medium
                    )

                    Text(
                        "Showing $resultCount of $totalExercises exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

//                    AnimatedVisibility(visible = searchQuery.isBlank() && topSuggestions.isNotEmpty()) {
//                        LazyRow(
//                            horizontalArrangement = Arrangement.spacedBy(8.dp),
//                        ) {
//                            topSuggestions.forEach { suggestion ->
//                                item {
//                                    AssistChip(
//                                        onClick = { searchQuery = suggestion },
//                                        leadingIcon = {
//                                            Icon(
//                                                imageVector = Icons.Default.Search,
//                                                contentDescription = null
//                                            )
//                                        },
//                                        label = { Text(suggestion) },
//                                    )
//                                }
//                            }
//                        }
//                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sort by",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box {
                            FilledTonalButton(onClick = { isSortMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(sortOption.label)
                            }
                            DropdownMenu(
                                expanded = isSortMenuExpanded,
                                onDismissRequest = { isSortMenuExpanded = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            isSortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Exercise Progress List (non-lazy so the whole screen scrolls as one)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredExercises.isEmpty()) {
                        Text(
                            "No exercises match your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        filteredExercises.forEach { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onExerciseClick(entry.key) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        HighlightedText(
                                            text = entry.key,
                                            query = searchQuery,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier,
                                            highlightStyle = SpanStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(
                                                "${entry.value.first} reps",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "${entry.value.second} sets",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                "${formatWeight(entry.value.third)} kg",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
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

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    highlightStyle: SpanStyle
) {
    if (query.isBlank()) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val annotated = buildAnnotatedString {
        var startIndex = 0
        var matchIndex = lowerText.indexOf(lowerQuery, startIndex)

        while (matchIndex >= 0) {
            append(text.substring(startIndex, matchIndex))
            withStyle(highlightStyle) {
                append(text.substring(matchIndex, matchIndex + lowerQuery.length))
            }
            startIndex = matchIndex + lowerQuery.length
            matchIndex = lowerText.indexOf(lowerQuery, startIndex)
        }

        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }

    Text(text = annotated, style = style, modifier = modifier)
}
