package com.flandolf.workout.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises
import kotlin.math.max

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exerciseName: String, workouts: List<WorkoutWithExercises>, onBackClick: () -> Unit
) {
    // Calculate exercise-specific data
    val exerciseData = remember(exerciseName, workouts) {
        val dataPoints = mutableListOf<Triple<Long, Float, Int>>() // date, best weight, total reps
        var totalReps = 0
        var totalSets = 0
        var totalVolume = 0f
        var bestWeight = 0f
        var bestReps = 0

        for (workout in workouts.sortedBy { it.workout.date }) {
            val exercise = workout.exercises.find { it.exercise.name == exerciseName }
            if (exercise != null && exercise.sets.isNotEmpty()) {
                val workoutReps = exercise.sets.sumOf { it.reps }
                val workoutVolume = exercise.sets.sumOf { it.reps.toInt() * it.weight.toInt() }
                val workoutBestWeight = exercise.sets.maxOf { it.weight }
                val workoutBestReps = exercise.sets.maxOf { it.reps }

                totalReps += workoutReps
                totalSets += exercise.sets.size
                totalVolume += workoutVolume
                bestWeight = max(bestWeight, workoutBestWeight)
                bestReps = max(bestReps, workoutBestReps)

                dataPoints.add(Triple(workout.workout.date, workoutBestWeight, workoutReps))
            }
        }

        ExerciseStats(
            totalReps = totalReps,
            totalSets = totalSets,
            totalVolume = totalVolume,
            bestWeight = bestWeight,
            bestReps = bestReps,
            averageWeight = if (dataPoints.isNotEmpty()) dataPoints.map { it.second }.average()
                .toFloat() else 0f,
            dataPoints = dataPoints
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text(exerciseName, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 16.dp, bottom = 120.dp
            ) // Account for bottom navigation
        ) {
            // Statistics Cards
            item {
                Text(
                    "Exercise Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // First row of stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total Reps",
                        value = exerciseData.totalReps.toString(),
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Sets",
                        value = exerciseData.totalSets.toString(),
                        icon = Icons.Default.Repeat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Second row of stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Best Weight",
                        value = "${String.format("%.1f", exerciseData.bestWeight)} kg",
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Total Volume",
                        value = "${String.format("%.1f", exerciseData.totalVolume)} kg",
                        icon = Icons.Default.BarChart,
                        modifier = Modifier.weight(1f)
                    )
                }
            }


            // Progress Graph
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Progress Over Time",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (exerciseData.dataPoints.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No data points available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            ProgressGraph(
                                dataPoints = exerciseData.dataPoints,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Workouts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${exerciseData.dataPoints.size} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(exerciseData.dataPoints.takeLast(10).reversed()) { (date, weight, reps) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            java.text.SimpleDateFormat(
                                "EEEE, MMM dd", java.util.Locale.getDefault()
                            ).format(java.util.Date(date)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Main workout info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side - workout details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Weight info
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.FitnessCenter,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${String.format("%.1f", weight)} kg",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Reps info
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Repeat,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "$reps reps", style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            // Right side - volume
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Volume",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${String.format("%.1f", reps * weight)} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }


}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon with background circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Value with better typography
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProgressGraph(
    dataPoints: List<Triple<Long, Float, Int>>, modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas

        val padding = 40f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        // Find min/max values
        val minWeight = dataPoints.minOf { it.second }
        val maxWeight = dataPoints.maxOf { it.second }
        val weightRange = max(maxWeight - minWeight, 1f)

        // Draw grid lines
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = padding + (graphHeight / gridLines) * i
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.2f),
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1f
            )
        }

        // Draw data points and line
        val path = Path()
        dataPoints.forEachIndexed { index, (date, weight, reps) ->
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
            val y = padding + graphHeight - ((weight - minWeight) / weightRange) * graphHeight

            // Draw point
            drawCircle(
                color = primaryColor, radius = 4f, center = Offset(x, y)
            )

            // Draw line to next point
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the progress line
        drawPath(
            path = path, color = primaryColor, style = Stroke(width = 2f)
        )
    }
}

private data class ExerciseStats(
    val totalReps: Int,
    val totalSets: Int,
    val totalVolume: Float,
    val bestWeight: Float,
    val bestReps: Int,
    val averageWeight: Float,
    val dataPoints: List<Triple<Long, Float, Int>>
)
