package com.flandolf.workout.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flandolf.workout.data.WorkoutWithExercises
import com.flandolf.workout.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    workouts: List<WorkoutWithExercises>,
    onSelect: (Long) -> Unit,
    viewModel: HistoryViewModel? = null
) {
    LaunchedEffect(Unit) {
        viewModel?.loadWorkouts()
    }
    val df = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("History") })
        if (workouts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "No workouts logged yet. Start your first workout!",
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(workouts) { w ->
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        df.format(Date(w.workout.date)),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (expanded) "Collapse" else "Expand"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Exercises list: when collapsed show exercise name + best set; when expanded show full sets
                            Column(modifier = Modifier.fillMaxWidth()) {
                                for (ex in w.exercises) {
                                    // compute best set by max weight, then max reps as tiebreaker
                                    val best =
                                        ex.sets.maxWithOrNull(compareBy({ it.weight }, { it.reps }))

                                    // Table-like layout: name column (weight 0.6) and best set column (weight 0.4)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Text(
                                            ex.exercise.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (best != null) "${best.reps} x ${best.weight}kg" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(0.25f)
                                        )
                                    }

                                    if (expanded) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        for ((i, s) in ex.sets.withIndex()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Set ${i + 1}", style = MaterialTheme.typography.bodySmall)
                                                Text("${s.reps} reps — ${s.weight} kg", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }

                            // Bottom summary row: timer icon + duration, weight icon + total weight lifted
                            Spacer(modifier = Modifier.height(8.dp))
                            val totalWeight =
                                w.exercises.sumOf { ex -> ex.sets.sumOf { it.reps * it.weight.toInt() } }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = "Duration")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "${w.workout.durationSeconds / 60}m ${w.workout.durationSeconds % 60}s",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = "Total weight"
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "$totalWeight kg",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                // Trash/delete button on the bottom-right
                                IconButton(onClick = { /* TODO: implement delete action */ }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete workout")
                                }
                            }
                        }
                    }
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )
                }
            }
        }
    }
}
