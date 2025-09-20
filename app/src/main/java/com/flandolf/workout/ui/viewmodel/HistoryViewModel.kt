package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.Workout
import com.flandolf.workout.data.WorkoutRepository
import com.flandolf.workout.data.WorkoutWithExercises
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkoutRepository(application.applicationContext)

    private val _workouts = MutableStateFlow<List<WorkoutWithExercises>>(emptyList())
    val workouts: StateFlow<List<WorkoutWithExercises>> = _workouts

    fun loadWorkouts() {
        viewModelScope.launch {
            _workouts.value = repo.getAllWorkouts()
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            repo.deleteWorkout(workout)
            // Reload workouts after deletion
            loadWorkouts()
        }
    }
}
