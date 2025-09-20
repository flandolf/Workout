package com.flandolf.workout.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flandolf.workout.data.WorkoutWithExercises
import com.flandolf.workout.ui.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    workouts: List<WorkoutWithExercises>,
    viewModel: HistoryViewModel? = null
) {
    // Pull fresh info on load
    LaunchedEffect(Unit) {
        viewModel?.loadWorkouts()
    }

    // Aggregate total reps and sets per exercise name
    val totals = remember(workouts) {
        val map = mutableMapOf<String, Pair<Int, Int>>() // name -> (reps, sets)
        for (w in workouts) {
            for (ex in w.exercises) {
                val (oldReps, oldSets) = map.getOrDefault(ex.exercise.name, 0 to 0)
                var reps = 0
                for (s in ex.sets) reps += s.reps
                map[ex.exercise.name] = (oldReps + reps) to (oldSets + ex.sets.size)
            }
        }
        map
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Progress", fontWeight = FontWeight.Bold) })
        if (totals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No progress yet. Start logging workouts!", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(totals.entries.sortedByDescending { it.value.first }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.key,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    "Total reps: ${entry.value.first}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Total sets: ${entry.value.second}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
