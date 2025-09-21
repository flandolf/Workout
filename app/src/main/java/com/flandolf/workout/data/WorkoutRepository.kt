package com.flandolf.workout.data

import android.content.Context
import com.flandolf.workout.data.sync.SyncRepository
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkoutRepository(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.workoutDao() }
    val syncRepository by lazy { SyncRepository(context) }

    suspend fun startWorkout(): Long {
        val id = dao.insertWorkout(Workout())
        android.util.Log.d("WorkoutRepository", "Started workout local id=$id")
        
        // Trigger sync for the new workout
        try {
            val workout = dao.getWorkoutWithExercises(id)?.workout
            if (workout != null) {
                syncRepository.syncWorkout(workout)
            }
        } catch (e: Exception) {
            // Sync failed, but continue with local operation
            android.util.Log.w("WorkoutRepository", "Failed to sync new workout", e)
        }
        
        return id
    }

    suspend fun endWorkout(id: Long, durationSeconds: Long) {
        val existingWith = dao.getWorkoutWithExercises(id)
        val existing = existingWith?.workout
        android.util.Log.d("WorkoutRepository", "Ending workout local id=$id, exists=${existing != null}")
        if (existing != null) {
            val hasSets = existingWith.exercises.any { ex -> ex.sets.isNotEmpty() }
            android.util.Log.d("WorkoutRepository", "Workout $id hasSets=$hasSets")
            if (!hasSets) {
                // No sets logged, delete the workout entirely
                dao.deleteWorkout(existing)
                syncRepository.deleteWorkoutFromFirestore(id)
            } else {
                val updated = existing.copy(durationSeconds = durationSeconds)
                dao.updateWorkout(updated)
                syncRepository.syncWorkout(updated)
            }
        }
    }

    suspend fun addExercise(workoutId: Long, name: String): Long {
        return dao.insertExercise(ExerciseEntity(workoutId = workoutId, name = name))
    }

    suspend fun addSet(exerciseId: Long, reps: Int, weight: Float): Long {
        return dao.insertSet(SetEntity(exerciseId = exerciseId, reps = reps, weight = weight))
    }

    suspend fun getAllWorkouts(): List<WorkoutWithExercises> = dao.getAllWorkoutsWithExercises()

    suspend fun getWorkout(id: Long): WorkoutWithExercises? = dao.getWorkoutWithExercises(id)

    suspend fun getLatestWorkout(): WorkoutWithExercises? = dao.getLatestWorkoutWithExercises()

    suspend fun getDistinctExerciseNames(): List<String> = dao.getDistinctExerciseNames()

    suspend fun getBestSetFromLastWorkout(exerciseName: String, currentWorkoutId: Long): SetEntity? = 
        dao.getBestSetFromLastWorkout(exerciseName, currentWorkoutId)

    /**
     * Exports all workout data to a CSV file with comprehensive information including:
     * - Date and time of workout
     * - Workout duration (both in seconds and formatted)
     * - Exercise details with set-by-set breakdown
     * - Weight, reps, and calculated volume for each set
     * - Proper CSV escaping for special characters
     */
    suspend fun exportCsv(file: File): File {
        val workouts = getAllWorkouts()
        FileWriter(file).use { writer ->
            // Enhanced CSV header with more useful information
            writer.append("Date,Time,Workout Duration (seconds),Workout Duration (formatted),Exercise Name,Set Number,Reps,Weight (kg),Volume (kg),Notes\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (w in workouts) {
                val date = dateFormat.format(Date(w.workout.date))
                val time = timeFormat.format(Date(w.workout.date))
                val durationSeconds = w.workout.durationSeconds
                val durationText = formatDuration(durationSeconds)

                for (ex in w.exercises) {
                    var setIndex = 1
                    for (s in ex.sets) {
                        val volume = s.reps * s.weight
                        val notes = "" // Could be extended to include notes in the future

                        writer.append(escapeCsv(date)).append(',')
                            .append(escapeCsv(time)).append(',')
                            .append(durationSeconds.toString()).append(',')
                            .append(escapeCsv(durationText)).append(',')
                            .append(escapeCsv(ex.exercise.name)).append(',')
                            .append(setIndex.toString()).append(',')
                            .append(s.reps.toString()).append(',')
                            .append(String.format("%.2f", s.weight)).append(',')
                            .append(String.format("%.2f", volume)).append(',')
                            .append(escapeCsv(notes)).append('\n')
                        setIndex++
                    }
                }
            }
        }
        return file
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else value
    }

    suspend fun deleteExercise(exercise: ExerciseEntity) {
        dao.deleteExercise(exercise)
        // Note: Exercise deletion doesn't need immediate sync since it's part of workout
    }

    suspend fun deleteWorkout(workout: Workout) {
        dao.deleteWorkout(workout)
        // Sync the deletion to Firestore
        try {
            syncRepository.deleteWorkoutFromFirestore(workout.id)
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to sync workout deletion", e)
        }
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        return "$mins min"
    }

    suspend fun resetAllData() {
        // Clear local database
        try {
            dao.getAllWorkoutsWithExercises().forEach { workout ->
                dao.deleteWorkout(workout.workout)
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutRepository", "Failed to reset local data", e)
        }
    }
    
    /**
     * Initialize sync functionality
     */
    suspend fun initializeSync() {
        try {
            syncRepository.initialize()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to initialize sync", e)
        }
    }
    
    /**
     * Perform manual sync
     */
    suspend fun performSync() {
        try {
            syncRepository.performFullSync()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to perform sync", e)
        }
    }
    
    /**
     * Sync down from remote database only (download remote changes to local)
     */
    suspend fun syncDown() {
        try {
            syncRepository.syncDown()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to sync down", e)
        }
    }
}
