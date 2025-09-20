package com.flandolf.workout.ui.screens

import com.flandolf.workout.ui.components.ProgressGraph
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphDetailScreen(
    exerciseName: String,
    workouts: List<WorkoutWithExercises>,
    onBackClick: () -> Unit
) {
    // Calculate data points for the graph
    val dataPoints = workouts
        .sortedBy { it.workout.date }
        .mapNotNull { workout ->
            val exercise = workout.exercises.find { it.exercise.name == exerciseName }
            exercise?.sets?.maxByOrNull { it.weight }?.let { set ->
                Triple(workout.workout.date, set.weight, set.reps)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "$exerciseName Progress",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Weight Progress Over Time",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (dataPoints.isNotEmpty()) {
                Card {
                    ProgressGraph(
                        dataPoints = dataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No data available for this exercise",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
