package com.flandolf.workout.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises
import com.flandolf.workout.data.formatWeight
import com.flandolf.workout.ui.components.BarChart
import com.flandolf.workout.ui.viewmodel.HistoryViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    workouts: List<WorkoutWithExercises>,
    viewModel: HistoryViewModel? = null,
    onExerciseClick: (String) -> Unit = {}
) {
    // Pull fresh info on load
    LaunchedEffect(Unit) {
        viewModel?.loadWorkouts()
    }

    // Aggregate total reps, sets, and weight lifted per exercise name
    val totals by remember(workouts) {
        derivedStateOf {
            val map = mutableMapOf<String, Triple<Int, Int, Float>>() // name -> (reps, sets, kg)
            var totalKgLifted = 0f
            var totalReps = 0

            for (w in workouts) {
                for (ex in w.exercises) {
                    val (oldReps, oldSets, oldKg) = map.getOrDefault(
                        ex.exercise.name,
                        Triple(0, 0, 0f)
                    )
                    var exerciseReps = 0
                    var exerciseKg = 0f

                    for (s in ex.sets) {
                        exerciseReps += s.reps
                        exerciseKg += s.reps * s.weight
                    }

                    map[ex.exercise.name] =
                        Triple(oldReps + exerciseReps, oldSets + ex.sets.size, oldKg + exerciseKg)
                    totalReps += exerciseReps
                    totalKgLifted += exerciseKg
                }
            }
            map to Pair(totalKgLifted, totalReps)
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
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No progress yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Start logging workouts to see your progress!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
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

                // Exercise Progress List (non-lazy so the whole screen scrolls as one)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exerciseTotals.entries.sortedByDescending { it.value.first }.forEach { entry ->
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
                                    Text(
                                        entry.key,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
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
