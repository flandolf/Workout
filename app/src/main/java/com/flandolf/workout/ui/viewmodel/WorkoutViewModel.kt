package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.WorkoutRepository
import com.flandolf.workout.data.WorkoutWithExercises
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkoutRepository(application.applicationContext)

    private val _currentWorkoutId = MutableStateFlow<Long?>(null)
    val currentWorkoutId: StateFlow<Long?> = _currentWorkoutId

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds
    private var timerJob: kotlinx.coroutines.Job? = null

    private val _currentWorkout = MutableStateFlow<WorkoutWithExercises?>(null)
    val currentWorkout: StateFlow<WorkoutWithExercises?> = _currentWorkout

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

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
            }
        } else {
            resumeTimer()
        }
    }

    fun resumeTimer() {
        if (timerJob == null) {
            _isTimerRunning.value = true
            startTimerJob()
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


}
