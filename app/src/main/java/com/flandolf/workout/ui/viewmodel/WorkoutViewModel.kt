package com.flandolf.workout.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.Workout
import com.flandolf.workout.data.WorkoutRepository
import com.flandolf.workout.data.WorkoutWithExercises
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.core.content.edit

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkoutRepository(application.applicationContext)
    private val prefs: SharedPreferences =
        application.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENT_WORKOUT_ID = "current_workout_id"
        private const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
        private const val KEY_IS_TIMER_RUNNING = "is_timer_running"
        private const val KEY_LAST_SAVE_TIME = "last_save_time"
    }

    private fun clearSavedState() {
        prefs.edit {
            remove(KEY_CURRENT_WORKOUT_ID)
            remove(KEY_ELAPSED_SECONDS)
            remove(KEY_IS_TIMER_RUNNING)
            remove(KEY_LAST_SAVE_TIME)
        }
    }

    private fun restoreWorkoutState() {
        val savedWorkoutId = prefs.getLong(KEY_CURRENT_WORKOUT_ID, -1L)
        val savedElapsedSeconds = prefs.getLong(KEY_ELAPSED_SECONDS, 0L)
        val savedIsTimerRunning = prefs.getBoolean(KEY_IS_TIMER_RUNNING, false)
        val lastSaveTime = prefs.getLong(KEY_LAST_SAVE_TIME, 0L)

        if (savedWorkoutId != -1L) {
            _currentWorkoutId.value = savedWorkoutId

            // Calculate elapsed time since last save if timer was running
            val currentTime = System.currentTimeMillis()
            val timeDiffSeconds = (currentTime - lastSaveTime) / 1000L

            if (savedIsTimerRunning && timeDiffSeconds > 0) {
                _elapsedSeconds.value = savedElapsedSeconds + timeDiffSeconds
                _isTimerRunning.value = true
                startTimerJob()
            } else {
                _elapsedSeconds.value = savedElapsedSeconds
                _isTimerRunning.value = savedIsTimerRunning
            }

            // Load the current workout data
            viewModelScope.launch {
                _currentWorkout.value = repo.getWorkout(savedWorkoutId)
            }
        }
    }

    private val _currentWorkoutId = MutableStateFlow<Long?>(null)
    val currentWorkoutId: StateFlow<Long?> = _currentWorkoutId

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds
    private var timerJob: kotlinx.coroutines.Job? = null

    private val _currentWorkout = MutableStateFlow<WorkoutWithExercises?>(null)
    val currentWorkout: StateFlow<WorkoutWithExercises?> = _currentWorkout

    private val _exerciseNameSuggestions = MutableStateFlow<List<String>>(emptyList())
    val exerciseNameSuggestions: StateFlow<List<String>> = _exerciseNameSuggestions

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    init {
        restoreWorkoutState()
    }

    fun startWorkout() {
        // Only create a new workout if none is active
        if (_currentWorkoutId.value == null) {
            viewModelScope.launch {
                val id = repo.startWorkout()
                _currentWorkoutId.value = id
                _elapsedSeconds.value = 0L
                _currentWorkout.value = repo.getWorkout(id)
                _isTimerRunning.value = true
                startTimerJob()
                saveWorkoutState()
            }
        } else {
            resumeTimer()
        }
    }

    fun resumeTimer() {
        if (timerJob == null) {
            _isTimerRunning.value = true
            startTimerJob()
            saveWorkoutState()
        }
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                _elapsedSeconds.value = _elapsedSeconds.value + 1
            }
        }
    }

    fun endWorkout() {
        viewModelScope.launch {
            val id = _currentWorkoutId.value
            if (id != null) {
                repo.endWorkout(id, _elapsedSeconds.value)
                _currentWorkoutId.value = null
                _elapsedSeconds.value = 0L
                timerJob?.cancel()
                timerJob = null
                _isTimerRunning.value = false
                _currentWorkout.value = null
                clearSavedState()
            }
        }
    }

    fun tickOneSecond() {
        _elapsedSeconds.value = _elapsedSeconds.value + 1
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _isTimerRunning.value = false
        saveWorkoutState()
    }

    fun addExercise(name: String) {
        viewModelScope.launch {
            val id = _currentWorkoutId.value
            if (id != null) repo.addExercise(id, name)
            _currentWorkout.value = _currentWorkoutId.value?.let { repo.getWorkout(it) }
        }
    }

    fun addSet(exerciseId: Long, reps: Int, weight: Float) {
        viewModelScope.launch {
            repo.addSet(exerciseId, reps, weight)
            val id = _currentWorkoutId.value
            if (id != null) _currentWorkout.value = repo.getWorkout(id)
        }
    }

    fun refreshCurrentWorkout() {
        viewModelScope.launch {
            val id = _currentWorkoutId.value
            if (id != null) _currentWorkout.value = repo.getWorkout(id)
        }
    }

    fun loadExerciseNameSuggestions() {
        viewModelScope.launch {
            _exerciseNameSuggestions.value = repo.getDistinctExerciseNames()
        }
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            val id = _currentWorkoutId.value
            val workout = id?.let { repo.getWorkout(it) }
            val exercise = workout?.exercises?.find { it.exercise.id == exerciseId }?.exercise
            if (exercise != null) {
                repo.deleteExercise(exercise)
                _currentWorkout.value = id.let { repo.getWorkout(it!!) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveWorkoutState()
    }

    private fun saveWorkoutState() {
        val workoutId = _currentWorkoutId.value
        if (workoutId != null) {
            prefs.edit {
                putLong(KEY_CURRENT_WORKOUT_ID, workoutId)
                putLong(KEY_ELAPSED_SECONDS, _elapsedSeconds.value)
                putBoolean(KEY_IS_TIMER_RUNNING, _isTimerRunning.value)
                putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
            }
        } else {
            clearSavedState()
        }
    }
}
