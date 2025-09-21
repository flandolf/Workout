package com.flandolf.workout.ui.screens

import com.flandolf.workout.ui.components.ProgressGraph
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises
import com.flandolf.workout.data.formatWeight
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphDetailScreen(
    exerciseName: String, workouts: List<WorkoutWithExercises>, onBackClick: () -> Unit
) {
    // Calculate data points for different progressions
    val workoutData = remember(workouts, exerciseName) {
        workouts.sortedBy { it.workout.date }.mapNotNull { workout ->
                val exercise = workout.exercises.find { it.exercise.name == exerciseName }
                exercise?.let { ex ->
                    val totalVolume = ex.sets.sumOf { (it.reps * it.weight).toInt() }.toFloat()
                    val totalReps = ex.sets.sumOf { it.reps }
                    val maxWeight = ex.sets.maxOfOrNull { it.weight } ?: 0f
                    val maxReps = ex.sets.maxOfOrNull { it.reps } ?: 0
                    val estimated1RM = if (maxReps > 0 && maxWeight > 0) {
                        maxWeight * (36f / (37f - maxReps))
                    } else 0f

                    WorkoutProgressData(
                        date = workout.workout.date,
                        maxWeight = maxWeight,
                        totalVolume = totalVolume,
                        totalReps = totalReps,
                        estimated1RM = estimated1RM
                    )
                }
            }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Weight", "Volume", "Reps", "1RM")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(title = {
            Text(
                text = "$exerciseName Progress",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }, navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })

        if (workoutData.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val chartData = when (selectedTab) {
                    0 -> workoutData.map { Triple(it.date, it.maxWeight, 0) } // Weight
                    1 -> workoutData.map { Triple(it.date, it.totalVolume, 0) } // Volume
                    2 -> workoutData.map { Triple(it.date, it.totalReps.toFloat(), 0) } // Reps
                    3 -> workoutData.map { Triple(it.date, it.estimated1RM, 0) } // 1RM
                    else -> workoutData.map { Triple(it.date, it.maxWeight, 0) }
                }

                val dataType = when (selectedTab) {
                    0 -> "weight"
                    1 -> "volume"
                    2 -> "reps"
                    3 -> "1rm"
                    else -> "weight"
                }

                val chartTitle = when (selectedTab) {
                    0 -> "Maximum Weight Progress"
                    1 -> "Total Volume Progress"
                    2 -> "Total Reps Progress"
                    3 -> "Estimated 1RM Progress"
                    else -> "Progress"
                }

                Text(
                    chartTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    ProgressGraph(
                        dataPoints = chartData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(16.dp),
                        dataType = dataType
                    )
                }

                // Statistics summary
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Statistics Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        when (selectedTab) {
                            0 -> { // Weight
                                val current = workoutData.last().maxWeight
                                val best = workoutData.maxOf { it.maxWeight }
                                val improvement = if (workoutData.size > 1) {
                                    val first = workoutData.first().maxWeight
                                    ((current - first) / first * 100).toInt()
                                } else 0

                                StatRow("Current Max", "${formatWeight(current, true)} kg")
                                StatRow("Personal Best", "${formatWeight(best, true)} kg")
                                StatRow("Improvement", "$improvement%")
                            }

                            1 -> { // Volume
                                val current = workoutData.last().totalVolume
                                val best = workoutData.maxOf { it.totalVolume }
                                val avg = workoutData.map { it.totalVolume }.average()

                                StatRow("Current Volume", "${formatWeight(current, true)} kg")
                                StatRow("Best Volume", "${formatWeight(best, true)} kg")
                                StatRow("Average Volume", "${String.format(Locale.US, "%.1f", avg)} kg")
                            }

                            2 -> { // Reps
                                val current = workoutData.last().totalReps
                                val best = workoutData.maxOf { it.totalReps }
                                val total = workoutData.sumOf { it.totalReps }

                                StatRow("Current Reps", "$current")
                                StatRow("Best Session", "$best")
                                StatRow("Total Reps", "$total")
                            }

                            3 -> { // 1RM
                                val current = workoutData.last().estimated1RM
                                val best = workoutData.maxOf { it.estimated1RM }
                                val improvement = if (workoutData.size > 1) {
                                    val first = workoutData.first().estimated1RM
                                    if (first > 0) ((current - first) / first * 100).toInt() else 0
                                } else 0

                                StatRow("Current 1RM", "${formatWeight(current, true)} kg")
                                StatRow("Best 1RM", "${formatWeight(best, true)} kg")
                                StatRow("Improvement", "$improvement%")
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No data available for this exercise",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class WorkoutProgressData(
    val date: Long,
    val maxWeight: Float,
    val totalVolume: Float,
    val totalReps: Int,
    val estimated1RM: Float
)
