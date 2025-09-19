package com.flandolf.workout

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flandolf.workout.ui.screens.*
import com.flandolf.workout.ui.theme.WorkoutTheme
import com.flandolf.workout.ui.viewmodel.HistoryViewModel
import java.io.File
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val workoutVm: WorkoutViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    // Ensure workouts are loaded immediately when the app starts
    historyVm.loadWorkouts()
        setContent {
            WorkoutTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onStartWorkout = { navController.navigate("workout") },
                                onViewHistory = { navController.navigate("history") },
                                onSettings = { navController.navigate("settings") },
                                onViewProgress = { navController.navigate("progress") }
                            )
                        }
                        composable("workout") {
                            val elapsed = workoutVm.elapsedSeconds.collectAsState()
                            val currentWorkout = workoutVm.currentWorkout.collectAsState()
                            val currentWorkoutId = workoutVm.currentWorkoutId.collectAsState()
                            val isTimerRunning = workoutVm.isTimerRunning.collectAsState()
                            LaunchedEffect(Unit) {
                                historyVm.loadWorkouts()
                                if (currentWorkoutId.value == null) {
                                    workoutVm.startWorkout()
                                }
                            }
                                // load suggestions and pass into screen
                                LaunchedEffect(Unit) { workoutVm.loadExerciseNameSuggestions() }
                                WorkoutScreen(
                                elapsedSeconds = elapsed.value,
                                currentExercises = currentWorkout.value?.exercises ?: emptyList(),
                                onStartTick = { workoutVm.resumeTimer() },
                                onPauseTick = { workoutVm.pauseTimer() },
                                onEndWorkout = {
                                    workoutVm.endWorkout()
                                    navController.navigateUp()
                                },
                                onAddExercise = { workoutVm.addExercise(it) },
                                onAddSet = { exerciseId, reps, weight -> workoutVm.addSet(exerciseId, reps, weight) },
                                    onDeleteExercise = { workoutVm.deleteExercise(it) },
                                    exerciseNameSuggestions = workoutVm.exerciseNameSuggestions.collectAsState().value,
                                isTimerRunning = isTimerRunning.value
                            )
                        }
                        composable("history") {
                            val workouts = historyVm.workouts.collectAsState()
                            HistoryScreen(workouts = workouts.value, onSelect = { /* TODO: show details */ }, viewModel = historyVm)
                        }
                        composable("progress") {
                            val workouts = historyVm.workouts.collectAsState()
                            ProgressScreen(workouts = workouts.value, viewModel = historyVm)
                        }
                        composable("settings") {
                            SettingsScreen(
                                onExportCsv = { exportCsv() },
                                onResetAll = {
                                    lifecycleScope.launch {
                                        val repo = com.flandolf.workout.data.WorkoutRepository(applicationContext)
                                        try {
                                            repo.resetAllData()
                                            workoutVm.refreshCurrentWorkout()
                                            historyVm.loadWorkouts()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exportCsv() {
        val file = File(filesDir, "workouts_export.csv")
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                val repo = com.flandolf.workout.data.WorkoutRepository(applicationContext)
                try {
                    repo.exportCsv(file)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}
