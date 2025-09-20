package com.flandolf.workout

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.lifecycle.lifecycleScope
import com.flandolf.workout.ui.components.BottomNavigationBar
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val workoutVm: WorkoutViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        historyVm.loadWorkouts()
        setContent {
            WorkoutTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavigationBar(navController) }) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "workout",
                        enterTransition = {
                            EnterTransition.None
                        },
                        exitTransition = {
                            ExitTransition.None
                        },
                    ) {
                        composable("workout") {
                            val elapsed = workoutVm.elapsedSeconds.collectAsState()
                            val currentWorkout = workoutVm.currentWorkout.collectAsState()
                            val isTimerRunning = workoutVm.isTimerRunning.collectAsState()
                            LaunchedEffect(Unit) {
                                historyVm.loadWorkouts()
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
                                    // Stay on workout screen instead of navigating up
                                },
                                onAddExercise = { workoutVm.addExercise(it) },
                                onAddSet = { exerciseId, reps, weight ->
                                    workoutVm.addSet(
                                        exerciseId, reps, weight
                                    )
                                },
                                onDeleteExercise = { workoutVm.deleteExercise(it) },
                                exerciseNameSuggestions = workoutVm.exerciseNameSuggestions.collectAsState().value,
                                isTimerRunning = isTimerRunning.value,
                                vm = workoutVm,
                            )
                        }
                        composable("history") {
                            val workouts = historyVm.workouts.collectAsState()
                            HistoryScreen(workouts = workouts.value, viewModel = historyVm)
                        }
                        composable("progress") {
                            val workouts = historyVm.workouts.collectAsState()
                            ProgressScreen(
                                workouts = workouts.value,
                                viewModel = historyVm,
                                onExerciseClick = { exerciseName ->
                                    navController.navigate("exercise_detail/$exerciseName")
                                })
                        }
                        composable("exercise_detail/{exerciseName}") { backStackEntry ->
                            val exerciseName =
                                backStackEntry.arguments?.getString("exerciseName") ?: ""
                            val workouts = historyVm.workouts.collectAsState()
                            ExerciseDetailScreen(
                                exerciseName = exerciseName,
                                workouts = workouts.value,
                                onBackClick = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(onExportCsv = { exportCsv() }, onResetAll = {
                                lifecycleScope.launch {
                                    val repo = com.flandolf.workout.data.WorkoutRepository(
                                        applicationContext
                                    )
                                    try {
                                        repo.resetAllData()
                                        workoutVm.refreshCurrentWorkout()
                                        historyVm.loadWorkouts()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun exportCsv() {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val fileName = "workout_export_$timestamp.csv"

        // Use Downloads directory for better accessibility
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = java.io.File(downloadsDir, fileName)

        lifecycleScope.launch {
            try {
                val repo = com.flandolf.workout.data.WorkoutRepository(applicationContext)
                val exportedFile = repo.exportCsv(file)

                // Show success message and offer to share
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "CSV exported to Downloads: $fileName",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    // Create share intent
                    val shareIntent =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(
                                android.content.Intent.EXTRA_STREAM,
                                androidx.core.content.FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${applicationContext.packageName}.fileprovider",
                                    exportedFile
                                )
                            )
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Workout Data Export")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                    // Show share dialog
                    startActivity(
                        android.content.Intent.createChooser(
                            shareIntent, "Share CSV Export"
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

}
