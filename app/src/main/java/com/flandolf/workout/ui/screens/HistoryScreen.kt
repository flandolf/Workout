package com.flandolf.workout.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("History") })
        if (workouts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
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
                            .padding(vertical = 8.dp)
                            .animateContentSize(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(df.format(Date(w.workout.date)), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Duration: ${w.workout.durationSeconds / 60} min",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (expanded) "Collapse" else "Expand"
                                    )
                                }
                            }

                            if (expanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    for (ex in w.exercises) {
                                        Text(
                                            ex.exercise.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        for ((i, s) in ex.sets.withIndex()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Set ${i + 1}")
                                                Text("${s.reps} reps — ${s.weight} kg")
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
