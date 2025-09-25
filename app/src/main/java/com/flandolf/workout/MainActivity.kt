package com.flandolf.workout

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flandolf.workout.data.TemplateRepository
import com.flandolf.workout.data.WorkoutRepository
import com.flandolf.workout.data.sync.AuthState
import com.flandolf.workout.ui.components.BottomNavigationBar
import com.flandolf.workout.ui.screens.AddTemplateScreen
import com.flandolf.workout.ui.screens.EditWorkoutScreen
import com.flandolf.workout.ui.screens.ExerciseDetailScreen
import com.flandolf.workout.ui.screens.GraphDetailScreen
import com.flandolf.workout.ui.screens.HistoryScreen
import com.flandolf.workout.ui.screens.ProgressScreen
import com.flandolf.workout.ui.screens.SettingsScreen
import com.flandolf.workout.ui.screens.TemplateScreen
import com.flandolf.workout.ui.screens.WorkoutScreen
import com.flandolf.workout.ui.theme.WorkoutTheme
import com.flandolf.workout.ui.viewmodel.EditWorkoutViewModel
import com.flandolf.workout.ui.viewmodel.HistoryViewModel
import com.flandolf.workout.ui.viewmodel.SyncViewModel
import com.flandolf.workout.ui.viewmodel.TemplateViewModel
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val workoutVm: WorkoutViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val syncVm: SyncViewModel by viewModels()
    private val editVm: EditWorkoutViewModel by viewModels()
    private val templateVm: TemplateViewModel by viewModels()

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
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) {
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
                                onUpdateSet = { exerciseId, setId, reps, weight ->
                                    workoutVm.updateSet(exerciseId, setId, reps, weight)
                                },
                                onAddSet = { exerciseId, reps, weight ->
                                    workoutVm.addSet(
                                        exerciseId, reps, weight
                                    )
                                },
                                onDeleteSet = { exerciseId, setIndex ->
                                    workoutVm.deleteSet(
                                        exerciseId,
                                        setIndex
                                    )
                                },
                                onDeleteExercise = { workoutVm.deleteExercise(it) },
                                exerciseNameSuggestions = workoutVm.exerciseNameSuggestions.collectAsState().value,
                                isTimerRunning = isTimerRunning.value,
                                vm = workoutVm,
                                previousBestSets = previousBestSets.value,
                                onShowSnackbar = { msg -> snackbarHostState.showSnackbar(msg) },
                                onStartFromTemplate = { templateId ->
                                    // create workout from template and start
                                    workoutVm.startWorkoutFromTemplate(templateId)
                                },
                                templatesFlow = templateVm.templatesFlow(),
                            )
                        }
                        composable("history") {
                            val workouts = historyVm.workouts.collectAsState()
                            HistoryScreen(
                                workouts = workouts.value,
                                viewModel = historyVm,
                                onEditWorkout = { workoutId ->
                                    navController.navigate("edit_workout/$workoutId")
                                },
                                onConvertToTemplate = { workoutWithExercises ->
                                    coroutineScope.launch {
                                        val dateStr = SimpleDateFormat(
                                            "yyyy-MM-dd",
                                            Locale.getDefault()
                                        ).format(Date(workoutWithExercises.workout.date))
                                        val templateName = "Workout $dateStr"
                                        val exerciseNames = workoutWithExercises.exercises
                                            .sortedBy { it.exercise.position }
                                            .map { it.exercise.name }
                                        val templateId = templateVm.createTemplateFromWorkout(
                                            templateName,
                                            exerciseNames
                                        )
                                        // Navigate first so snackbar does not block transition
                                        navController.navigate("edit_template/$templateId")
                                        // Show snackbar asynchronously (optional delay-free)
                                        snackbarHostState.showSnackbar("Template created", duration = SnackbarDuration.Short)
                                    }
                                }
                            )

                            // If there are no local workouts, suggest syncing down when appropriate
                            LaunchedEffect(
                                workouts.value.size,
                                syncUiState.authState,
                                syncUiState.syncStatus.isOnline
                            ) {
                                if (workouts.value.isEmpty()) {
                                    if (syncUiState.authState == AuthState.AUTHENTICATED) {
                                        if (syncUiState.syncStatus.isOnline) {
                                            val res = snackbarHostState.showSnackbar(
                                                "No local workouts. Restore from cloud?",
                                                actionLabel = "Restore"
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                syncVm.performSync()
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

                            LaunchedEffect(
                                workouts.value.size,
                                syncUiState.authState,
                                syncUiState.syncStatus.isOnline
                            ) {
                                if (workouts.value.isEmpty()) {
                                    // Reuse same messaging as history
                                    if (syncUiState.authState == AuthState.AUTHENTICATED) {
                                        if (syncUiState.syncStatus.isOnline) {
                                            val res = snackbarHostState.showSnackbar(
                                                "No local workouts. Restore from cloud?",
                                                actionLabel = "Restore"
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                syncVm.performSync()
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
                        composable("edit_workout/{workoutId}") { backStackEntry ->
                            val workoutIdStr = backStackEntry.arguments?.getString("workoutId")
                            val workoutId = workoutIdStr?.toLongOrNull() ?: -1L
                            LaunchedEffect(workoutId) {
                                if (workoutId > 0) editVm.loadWorkout(workoutId)
                            }
                            val workoutState = editVm.workout.collectAsState()
                            EditWorkoutScreen(
                                workout = workoutState.value?.exercises ?: emptyList(),
                                onBack = {
                                    // Refresh history on return
                                    historyVm.loadWorkouts()
                                    navController.popBackStack()
                                },
                                onAddExercise = { name ->
                                    editVm.addExercise(name)
                                },
                                onDeleteExercise = { exId ->
                                    editVm.deleteExercise(exId)
                                },
                                onAddSet = { exId, reps, weight ->
                                    editVm.addSet(exId, reps, weight)
                                },
                                onUpdateSet = { exId, setIndex, reps, weight ->
                                    editVm.updateSet(exId, setIndex, reps, weight)
                                },
                                onDeleteSet = { exId, setIndex ->
                                    editVm.deleteSet(exId, setIndex)
                                },
                                vm = editVm
                            )
                        }
                        composable("templates") {
                            TemplateScreen(
                                vm = templateVm,
                                navController = navController
                            )
                        }
                        composable(
                            "edit_template/{templateId}"
                        ) { backStackEntry ->
                            val templateIdStr =
                                backStackEntry.arguments?.getString("templateId")
                            val templateId = templateIdStr?.toLongOrNull() ?: -1L
                            LaunchedEffect(templateId) {
                                if (templateId >= 0) templateVm.loadTemplate(templateId)
                            }
                            val templateState = templateVm.template.collectAsState()
                            AddTemplateScreen(
                                template = templateState.value?.exercises ?: emptyList(),
                                onBack = {
                                    // Discard empty template rows when leaving editor
                                    templateVm.discardIfEmpty()
                                    navController.popBackStack()
                                },
                                onAddExercise = { name ->
                                    templateVm.addExercise(name)
                                },
                                onDeleteExercise = { exId ->
                                    templateVm.deleteExercise(exId)
                                },
                                onAddSet = { exId, reps, weight ->
                                    templateVm.addSet(exId, reps, weight)
                                },
                                onUpdateSet = { exId, setIndex, reps, weight ->
                                    templateVm.updateSet(exId, setIndex, reps, weight)
                                },
                                onDeleteSet = { exId, setIndex ->
                                    templateVm.deleteSet(exId, setIndex)
                                },
                                vm = templateVm,
                                editVm = editVm,
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onExportCsv = { exportCsv() },
                                onImportStrongCsv = { onImportStrongCsv() },
                                onImportWorkoutCsv = { onImportWorkoutCsv() },
                                onResetAll = {
                                    lifecycleScope.launch {
                                        val repo = WorkoutRepository(
                                            applicationContext
                                        )
                                        try {
                                            repo.resetAllData()
                                            workoutVm.refreshCurrentWorkout()
                                            historyVm.loadWorkouts()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Reset failed: ${e.localizedMessage ?: "Unknown"}")
                                            }
                                        }
                                    }
                                },
                                syncViewModel = syncVm,
                                onManualSync = {
                                    // Guard against offline or unauthenticated
                                    if (!syncUiState.isInitialized || syncUiState.authState != AuthState.AUTHENTICATED) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Sign in to sync with the cloud")
                                        }
                                    } else if (!syncUiState.syncStatus.isOnline) {
                                        coroutineScope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                "Offline: will retry when online",
                                                actionLabel = "Retry"
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                syncVm.performSync()
                                            }
                                        }
                                    } else {
                                        syncVm.performSync()
                                    }
                                },
                                onImportTemplateCsv = { importTemplateCsv()
                                },
                                onExportTemplateCsv = {
                                        exportTemplateCsv()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun importTemplateCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        importTemplate.launch(intent)
    }

    private fun exportTemplateCsv() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "workout_templates_$timestamp.csv"

        lifecycleScope.launch {
            try {
                val repo = TemplateRepository(applicationContext)

                // Insert into MediaStore Downloads
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = applicationContext.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore record")

                resolver.openOutputStream(itemUri)?.use { outStream ->
                    repo.exportTemplatesCsv(outStream)
                } ?: throw IllegalStateException("Failed to open output stream")

                // Mark file as complete
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Toast.makeText(
                    this@MainActivity,
                    "Templates exported to Downloads: $fileName",
                    Toast.LENGTH_LONG
                ).show()

                // Share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, itemUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Workout Templates Export")
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    clipData = ClipData.newUri(
                        contentResolver,
                        fileName,
                        itemUri
                    )
                }

                startActivity(Intent.createChooser(shareIntent, "Share Templates CSV Export"))

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportCsv() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "workout_export_$timestamp.csv"

        lifecycleScope.launch {
            try {
                val repo = WorkoutRepository(applicationContext)

                // Insert into MediaStore Downloads
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = applicationContext.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore record")

                resolver.openOutputStream(itemUri)?.use { outStream ->
                    repo.exportCsv(outStream) // make repo accept OutputStream instead of File
                } ?: throw IllegalStateException("Failed to open output stream")

                // Mark file as complete
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                Toast.makeText(
                    this@MainActivity,
                    "CSV exported to Downloads: $fileName",
                    Toast.LENGTH_LONG
                ).show()

                // Share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, itemUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Workout Data Export")
                    putExtra(Intent.EXTRA_TITLE, fileName) // this helps the chooser UI
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    // Optional: force a user-friendly filename in ClipData
                    clipData = ClipData.newUri(
                        contentResolver,
                        fileName,
                        itemUri
                    )
                }

                startActivity(Intent.createChooser(shareIntent, "Share CSV Export"))

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onImportStrongCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        importStrong.launch(intent)
    }

    private fun onImportWorkoutCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        importWorkout.launch(intent)

    }

    var importWorkout =
        registerForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    lifecycleScope.launch {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val repo =
                                    WorkoutRepository(applicationContext)
                                val importedCount = repo.importWorkoutCsv(inputStream)
                                inputStream.close()

                                // Refresh data in view models
                                workoutVm.refreshCurrentWorkout()
                                historyVm.loadWorkouts()

                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import successful: $importedCount workouts imported",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import failed: Could not read file",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed: ${e.localizedMessage ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

    var importStrong =
        registerForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    lifecycleScope.launch {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val repo =
                                    WorkoutRepository(applicationContext)
                                val importedCount = repo.importStrongCsv(inputStream)
                                inputStream.close()

                                // Refresh data in view models
                                workoutVm.refreshCurrentWorkout()
                                historyVm.loadWorkouts()

                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import successful: $importedCount workouts imported",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import failed: Could not read file",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed: ${e.localizedMessage ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

    var importTemplate =
        registerForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    lifecycleScope.launch {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val repo = TemplateRepository(applicationContext)
                                val importedCount = repo.importTemplatesCsv(inputStream)
                                inputStream.close()

                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import successful: $importedCount templates imported",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Import failed: Could not read file",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed: ${e.localizedMessage ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

}
