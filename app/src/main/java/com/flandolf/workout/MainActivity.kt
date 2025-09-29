package com.flandolf.workout

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.flandolf.workout.ui.viewmodel.SyncUiState
import com.flandolf.workout.ui.viewmodel.TemplateViewModel
import com.flandolf.workout.ui.viewmodel.ThemeViewModel
import com.flandolf.workout.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val workoutVm: WorkoutViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val syncVm: SyncViewModel by viewModels()
    private val editVm: EditWorkoutViewModel by viewModels()
    private val templateVm: TemplateViewModel by viewModels()
    private val themeVm: ThemeViewModel by viewModels()

    private val importWorkoutLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { handleWorkoutImport(it) }
        }

    private val importStrongLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { handleStrongImport(it) }
        }

    private val importTemplateLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { handleTemplateImport(it) }
        }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeVm.themeMode.collectAsStateWithLifecycle()
            WorkoutTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current
                val syncUiState by syncVm.uiState.collectAsStateWithLifecycle()
                val templatesFlow = remember(templateVm) { templateVm.templatesFlow }

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
                            val elapsed by workoutVm.elapsedSeconds.collectAsStateWithLifecycle()
                            val currentWorkout by workoutVm.currentWorkout.collectAsStateWithLifecycle()
                            val isTimerRunning by workoutVm.isTimerRunning.collectAsStateWithLifecycle()
                            val previousBestSets by workoutVm.previousBestSets.collectAsStateWithLifecycle()
                            val exerciseSuggestions by workoutVm.exerciseNameSuggestions.collectAsStateWithLifecycle()
                            // load suggestions and pass into screen
                            LaunchedEffect(Unit) { workoutVm.loadExerciseNameSuggestions() }
                            WorkoutScreen(
                                elapsedSeconds = elapsed,
                                currentExercises = currentWorkout?.exercises ?: emptyList(),
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
                                exerciseNameSuggestions = exerciseSuggestions,
                                isTimerRunning = isTimerRunning,
                                vm = workoutVm,
                                previousBestSets = previousBestSets,
                                onShowSnackbar = { msg -> snackbarHostState.showSnackbar(msg) },
                                onStartFromTemplate = { templateId ->
                                    // create workout from template and start
                                    workoutVm.startWorkoutFromTemplate(templateId)
                                },
                                templatesFlow = templatesFlow,
                            )
                        }
                        composable("history") {
                            val workouts by historyVm.workouts.collectAsStateWithLifecycle()
                            HistoryScreen(
                                workouts = workouts,
                                viewModel = historyVm,
                                onEditWorkout = { workoutId ->
                                    navController.navigate("edit_workout/$workoutId")
                                },
                                onConvertToTemplate = { workoutWithExercises ->
                                    lifecycleScope.launch {
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
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.snackbar_template_created),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )

                            // If there are no local workouts, suggest syncing down when appropriate
                            LaunchedEffect(
                                workouts.size,
                                syncUiState.authState,
                                syncUiState.syncStatus.isOnline
                            ) {
                                snackbarHostState.handleEmptyWorkouts(
                                    workoutsEmpty = workouts.isEmpty(),
                                    syncUiState = syncUiState,
                                    restorePrompt = context.getString(R.string.snackbar_no_workouts_restore_prompt),
                                    restoreAction = context.getString(R.string.action_restore),
                                    offlineMessage = context.getString(R.string.snackbar_no_workouts_go_online),
                                    signInMessage = context.getString(R.string.snackbar_no_workouts_sign_in)
                                ) {
                                    syncVm.performSync()
                                }
                            }
                        }
                        composable("progress") {
                            val workouts by historyVm.workouts.collectAsStateWithLifecycle()
                            ProgressScreen(
                                workouts = workouts,
                                viewModel = historyVm,
                                onExerciseClick = { exerciseName ->
                                    navController.navigate("exercise_detail/$exerciseName")
                                })

                            LaunchedEffect(
                                workouts.size,
                                syncUiState.authState,
                                syncUiState.syncStatus.isOnline
                            ) {
                                snackbarHostState.handleEmptyWorkouts(
                                    workoutsEmpty = workouts.isEmpty(),
                                    syncUiState = syncUiState,
                                    restorePrompt = context.getString(R.string.snackbar_no_workouts_restore_prompt),
                                    restoreAction = context.getString(R.string.action_restore),
                                    offlineMessage = context.getString(R.string.snackbar_no_workouts_go_online),
                                    signInMessage = context.getString(R.string.snackbar_no_workouts_sign_in)
                                ) {
                                    syncVm.performSync()
                                }
                            }
                        }
                        composable("exercise_detail/{exerciseName}") { backStackEntry ->
                            val exerciseName =
                                backStackEntry.arguments?.getString("exerciseName") ?: ""
                            val workouts by historyVm.workouts.collectAsStateWithLifecycle()
                            ExerciseDetailScreen(
                                exerciseName = exerciseName,
                                workouts = workouts,
                                onBackClick = { navController.popBackStack() },
                                onGraphClick = { navController.navigate("graph_detail/$exerciseName") })
                        }
                        composable("graph_detail/{exerciseName}") { backStackEntry ->
                            val exerciseName =
                                backStackEntry.arguments?.getString("exerciseName") ?: ""
                            val workouts by historyVm.workouts.collectAsStateWithLifecycle()
                            GraphDetailScreen(
                                exerciseName = exerciseName,
                                workouts = workouts,
                                onBackClick = { navController.popBackStack() })
                        }
                        composable("edit_workout/{workoutId}") { backStackEntry ->
                            val workoutIdStr = backStackEntry.arguments?.getString("workoutId")
                            val workoutId = workoutIdStr?.toLongOrNull() ?: -1L
                            LaunchedEffect(workoutId) {
                                if (workoutId > 0) editVm.loadWorkout(workoutId)
                            }
                            val workoutState by editVm.workout.collectAsStateWithLifecycle()
                            EditWorkoutScreen(
                                workout = workoutState?.exercises ?: emptyList(),
                                onBack = {
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
                            val templateState by templateVm.template.collectAsStateWithLifecycle()
                            AddTemplateScreen(
                                template = templateState?.exercises ?: emptyList(),
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
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.snackbar_reset_failed,
                                                    e.localizedMessage ?: context.getString(R.string.error_unknown)
                                                )
                                            )
                                        }
                                    }
                                },
                                syncViewModel = syncVm,
                                onManualSync = {
                                    // Guard against offline or unauthenticated
                                    if (!syncUiState.isInitialized || syncUiState.authState != AuthState.AUTHENTICATED) {
                                        lifecycleScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.snackbar_sign_in_to_sync)
                                            )
                                        }
                                    } else if (!syncUiState.syncStatus.isOnline) {
                                        lifecycleScope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                context.getString(R.string.snackbar_offline_retry),
                                                actionLabel = context.getString(R.string.snackbar_retry_action)
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                syncVm.performSync()
                                            }
                                        }
                                    } else {
                                        syncVm.performSync()
                                    }
                                },
                                onImportTemplateCsv = {
                                    importTemplateCsv()
                                },
                                onExportTemplateCsv = {
                                    exportTemplateCsv()
                                },
                                themeViewModel = themeVm,
                                version = getAppVersion()
                            )
                        }
                    }
                }
            }
        }
    }

    fun getAppVersion(): String {
        return try {
            val pm = packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val pInfo = pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
                pInfo.versionName ?: "N/A"
            } else {
                @Suppress("DEPRECATION")
                val pInfo = pm.getPackageInfo(packageName, 0)
                pInfo.versionName ?: "N/A"
            }
        } catch (_: Exception) {
            "N/A"
        }
    }

    private fun importTemplateCsv() {
        importTemplateLauncher.launch(arrayOf("text/*"))
    }

    private fun exportTemplateCsv() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = "workout_templates_$timestamp.csv"

        lifecycleScope.launch {
            val repo = TemplateRepository(applicationContext)
            try {
                val itemUri = withContext(Dispatchers.IO) {
                    exportToDownloads(fileName, CSV_MIME_TYPE) { outStream ->
                        repo.exportTemplatesCsv(outStream)
                    }
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_templates_exported, fileName),
                    Toast.LENGTH_LONG
                ).show()

                shareExport(
                    uri = itemUri,
                    mimeType = CSV_MIME_TYPE,
                    subject = getString(R.string.share_templates_subject),
                    chooserTitle = getString(R.string.chooser_share_templates),
                    fileName = fileName
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.toast_export_failed,
                        e.localizedMessage ?: getString(R.string.error_unknown)
                    ),
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
            val repo = WorkoutRepository(applicationContext)
            try {
                val itemUri = withContext(Dispatchers.IO) {
                    exportToDownloads(fileName, CSV_MIME_TYPE) { outStream ->
                        repo.exportCsv(outStream)
                    }
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_csv_exported, fileName),
                    Toast.LENGTH_LONG
                ).show()

                shareExport(
                    uri = itemUri,
                    mimeType = CSV_MIME_TYPE,
                    subject = getString(R.string.share_csv_subject),
                    chooserTitle = getString(R.string.chooser_share_csv),
                    fileName = fileName
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.toast_export_failed,
                        e.localizedMessage ?: getString(R.string.error_unknown)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onImportStrongCsv() {
        importStrongLauncher.launch(arrayOf("text/*"))
    }

    private fun onImportWorkoutCsv() {
        importWorkoutLauncher.launch(arrayOf("text/*"))
    }

    private fun handleWorkoutImport(uri: Uri) {
        handleImport(
            uri = uri,
            successMessage = { count ->
                getString(R.string.toast_import_success_workouts, count)
            },
            postSuccess = {
                workoutVm.refreshCurrentWorkout()
            }
        ) { inputStream ->
            val repo = WorkoutRepository(applicationContext)
            repo.importWorkoutCsv(inputStream)
        }
    }

    private fun handleStrongImport(uri: Uri) {
        handleImport(
            uri = uri,
            successMessage = { count ->
                getString(R.string.toast_import_success_workouts, count)
            },
            postSuccess = {
                workoutVm.refreshCurrentWorkout()
            }
        ) { inputStream ->
            val repo = WorkoutRepository(applicationContext)
            repo.importStrongCsv(inputStream)
        }
    }

    private fun handleTemplateImport(uri: Uri) {
        handleImport(
            uri = uri,
            successMessage = { count ->
                getString(R.string.toast_import_success_templates, count)
            }
        ) { inputStream ->
            val repo = TemplateRepository(applicationContext)
            repo.importTemplatesCsv(inputStream)
        }
    }

    private fun handleImport(
        uri: Uri,
        successMessage: (Int) -> String,
        postSuccess: suspend () -> Unit = {},
        importAction: suspend (InputStream) -> Int
    ) {
        lifecycleScope.launch {
            try {
                persistReadPermission(uri)
                val count = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        importAction(inputStream)
                    } ?: throw IllegalStateException(
                        getString(R.string.toast_import_failed_read)
                    )
                }
                postSuccess()
                Toast.makeText(
                    this@MainActivity,
                    successMessage(count),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: IllegalStateException) {
                Toast.makeText(
                    this@MainActivity,
                    e.message ?: getString(
                        R.string.toast_import_failed_generic,
                        getString(R.string.error_unknown)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.toast_import_failed_generic,
                        e.localizedMessage ?: getString(R.string.error_unknown)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // If the provider does not allow persistable permissions, ignore.
        }
    }

    private suspend fun exportToDownloads(
        fileName: String,
        mimeType: String,
        writeAction: suspend (OutputStream) -> Unit
    ): Uri {
        val resolver = applicationContext.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore record")
        return try {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                writeAction(outStream)
            } ?: throw IllegalStateException("Failed to open output stream")

            val finalizeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, finalizeValues, null, null)
            itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
    }

    private fun shareExport(
        uri: Uri,
        mimeType: String,
        subject: String,
        chooserTitle: String,
        fileName: String
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, fileName, uri)
        }

        if (shareIntent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(shareIntent, chooserTitle))
        }
    }

    companion object {
        private const val CSV_MIME_TYPE = "text/csv"
    }
}

private suspend fun SnackbarHostState.handleEmptyWorkouts(
    workoutsEmpty: Boolean,
    syncUiState: SyncUiState,
    restorePrompt: String,
    restoreAction: String,
    offlineMessage: String,
    signInMessage: String,
    onRestore: suspend () -> Unit
) {
    if (!workoutsEmpty) return
    when (syncUiState.authState) {
        AuthState.AUTHENTICATED -> {
            if (syncUiState.syncStatus.isOnline) {
                val result = showSnackbar(restorePrompt, actionLabel = restoreAction)
                if (result == SnackbarResult.ActionPerformed) {
                    onRestore()
                }
            } else {
                showSnackbar(offlineMessage)
            }
        }

        else -> {
            showSnackbar(signInMessage)
        }
    }
}
