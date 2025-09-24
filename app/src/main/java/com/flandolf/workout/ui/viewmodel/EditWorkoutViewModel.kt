package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.WorkoutRepository
import com.flandolf.workout.data.WorkoutWithExercises
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = WorkoutRepository(application.applicationContext)

    private val _workout = MutableStateFlow<WorkoutWithExercises?>(null)
    val workout: StateFlow<WorkoutWithExercises?> = _workout

    private var workoutId: Long? = null

    fun loadWorkout(id: Long) {
        workoutId = id
        viewModelScope.launch {
            _workout.value = repo.getWorkout(id)
        }
    }

    fun addExercise(name: String) {
        val id = workoutId ?: return
        viewModelScope.launch {
            repo.addExercise(id, name)
            _workout.value = repo.getWorkout(id)
        }
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            val ex = _workout.value?.exercises?.find { it.exercise.id == exerciseId }?.exercise
            if (ex != null) {
                repo.deleteExercise(ex)
                workoutId?.let { _workout.value = repo.getWorkout(it) }
            }
        }
    }

    fun addSet(exerciseId: Long, reps: Int, weight: Float) {
        viewModelScope.launch {
            repo.addSet(exerciseId, reps, weight)
            workoutId?.let { _workout.value = repo.getWorkout(it) }
        }
    }

    fun updateSet(exerciseId: Long, setIndex: Int, reps: Int, weight: Float) {
        viewModelScope.launch {
            val ex = _workout.value?.exercises?.find { it.exercise.id == exerciseId }
            val set = ex?.sets?.getOrNull(setIndex)
            if (set != null) {
                val updated = set.copy(reps = reps, weight = weight)
                repo.updateSet(updated)
                workoutId?.let { _workout.value = repo.getWorkout(it) }
            }
        }
    }

    fun deleteSet(exerciseId: Long, setIndex: Int) {
        viewModelScope.launch {
            val ex = _workout.value?.exercises?.find { it.exercise.id == exerciseId }
            val set = ex?.sets?.getOrNull(setIndex)
            if (set != null) {
                repo.deleteSet(set)
                workoutId?.let { _workout.value = repo.getWorkout(it) }
            }
        }
    }

    fun loadExerciseNameSuggestions(onLoaded: (List<String>) -> Unit) {
        viewModelScope.launch {
            onLoaded(repo.getDistinctExerciseNames())
        }
    }

    fun moveExerciseUp(exerciseId: Long) {
        viewModelScope.launch {
            val w = _workout.value ?: return@launch
            val sorted =
                w.exercises.sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))

            // Normalize positions to be sequential
            var changed = false
            sorted.forEachIndexed { idx, ex ->
                if (ex.exercise.position != idx) {
                    changed = true
                    repo.updateExercise(ex.exercise.copy(position = idx))
                }
            }
            val refreshed = if (changed) workoutId?.let { repo.getWorkout(it) } else w
            val list = refreshed?.exercises?.sortedWith(
                compareBy(
                    { it.exercise.position },
                    { it.exercise.id })
            ) ?: return@launch

            val index = list.indexOfFirst { it.exercise.id == exerciseId }
            if (index > 0) {
                val current = list[index].exercise
                val prev = list[index - 1].exercise
                val currentPos = current.position
                val prevPos = prev.position
                repo.updateExercise(current.copy(position = prevPos))
                repo.updateExercise(prev.copy(position = currentPos))
                workoutId?.let { _workout.value = repo.getWorkout(it) }
            }
        }
    }

    fun moveExerciseDown(exerciseId: Long) {
        viewModelScope.launch {
            val w = _workout.value ?: return@launch
            val sorted =
                w.exercises.sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))

            // Normalize positions to be sequential
            var changed = false
            sorted.forEachIndexed { idx, ex ->
                if (ex.exercise.position != idx) {
                    changed = true
                    repo.updateExercise(ex.exercise.copy(position = idx))
                }
            }
            val refreshed = if (changed) workoutId?.let { repo.getWorkout(it) } else w
            val list = refreshed?.exercises?.sortedWith(
                compareBy(
                    { it.exercise.position },
                    { it.exercise.id })
            ) ?: return@launch

            val index = list.indexOfFirst { it.exercise.id == exerciseId }
            if (index >= 0 && index < list.lastIndex) {
                val current = list[index].exercise
                val next = list[index + 1].exercise
                val currentPos = current.position
                val nextPos = next.position
                repo.updateExercise(current.copy(position = nextPos))
                repo.updateExercise(next.copy(position = currentPos))
                workoutId?.let { _workout.value = repo.getWorkout(it) }
            }
        }
    }
}
