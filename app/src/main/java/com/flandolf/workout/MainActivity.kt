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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.flandolf.workout.ui.viewmodel.SyncViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val workoutVm: WorkoutViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val syncVm: SyncViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        historyVm.loadWorkouts()
        setContent {
            WorkoutTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val syncUiState by syncVm.uiState.collectAsState()

                // Debounce simple snackbar messages to avoid spam
                LaunchedEffect(syncUiState.message, syncUiState.errorMessage) {
                    val msg = syncUiState.errorMessage ?: syncUiState.message
                    if (!msg.isNullOrBlank()) {
                        // show the snackbar once and then clear
                        snackbarHostState.showSnackbar(msg)
                        // clear messages so we don't spam the user
                        syncVm.clearMessages()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavigationBar(navController) },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) {
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
                            val previousBestSets = workoutVm.previousBestSets.collectAsState()
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
                                previousBestSets = previousBestSets.value
                            )
                        }
                        composable("history") {
                            val workouts = historyVm.workouts.collectAsState()
                                HistoryScreen(workouts = workouts.value, viewModel = historyVm)

                                // If there are no local workouts, suggest syncing down when appropriate
                                LaunchedEffect(workouts.value.size, syncUiState.authState, syncUiState.syncStatus.isOnline) {
                                    if (workouts.value.isEmpty()) {
                                        if (syncUiState.authState == com.flandolf.workout.data.sync.AuthState.AUTHENTICATED) {
                                            if (syncUiState.syncStatus.isOnline) {
                                                val res = snackbarHostState.showSnackbar("No local workouts. Restore from cloud?", actionLabel = "Restore")
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    syncVm.syncDown()
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("No local workouts. Go online to restore from cloud.")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("No local workouts. Sign in to restore cloud data.")
                                        }
                                    }
                                }
                        }
                        composable("progress") {
                            val workouts = historyVm.workouts.collectAsState()
                            ProgressScreen(
                                workouts = workouts.value,
                                viewModel = historyVm,
                                onExerciseClick = { exerciseName ->
                                    navController.navigate("exercise_detail/$exerciseName")
                                })

                                LaunchedEffect(workouts.value.size, syncUiState.authState, syncUiState.syncStatus.isOnline) {
                                    if (workouts.value.isEmpty()) {
                                        // Reuse same messaging as history
                                        if (syncUiState.authState == com.flandolf.workout.data.sync.AuthState.AUTHENTICATED) {
                                            if (syncUiState.syncStatus.isOnline) {
                                                val res = snackbarHostState.showSnackbar("No local workouts. Restore from cloud?", actionLabel = "Restore")
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    syncVm.syncDown()
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("No local workouts. Go online to restore from cloud.")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("No local workouts. Sign in to restore cloud data.")
                                        }
                                    }
                                }
                        }
                        composable("exercise_detail/{exerciseName}") { backStackEntry ->
                            val exerciseName =
                                backStackEntry.arguments?.getString("exerciseName") ?: ""
                            val workouts = historyVm.workouts.collectAsState()
                            ExerciseDetailScreen(
                                exerciseName = exerciseName,
                                workouts = workouts.value,
                                onBackClick = { navController.popBackStack() },
                                onGraphClick = { navController.navigate("graph_detail/$exerciseName") })
                        }
                        composable("graph_detail/{exerciseName}") { backStackEntry ->
                            val exerciseName =
                                backStackEntry.arguments?.getString("exerciseName") ?: ""
                            val workouts = historyVm.workouts.collectAsState()
                            GraphDetailScreen(
                                exerciseName = exerciseName,
                                workouts = workouts.value,
                                onBackClick = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onExportCsv = { exportCsv() },
                                onResetAll = {
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
                                            // Surface error to user via snackbar
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Reset failed: ${e.localizedMessage ?: "Unknown"}")
                                            }
                                        }
                                    }
                                },
                                syncViewModel = syncVm,
                                onManualSync = {
                                    // Guard against offline or unauthenticated
                                    if (!syncUiState.isInitialized || syncUiState.authState != com.flandolf.workout.data.sync.AuthState.AUTHENTICATED) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Sign in to sync with the cloud")
                                        }
                                    } else if (!syncUiState.syncStatus.isOnline) {
                                        coroutineScope.launch {
                                            val res = snackbarHostState.showSnackbar("Offline: will retry when online", actionLabel = "Retry")
                                            if (res == SnackbarResult.ActionPerformed) {
                                                syncVm.performSync()
                                            }
                                        }
                                    } else {
                                        syncVm.performSync()
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
