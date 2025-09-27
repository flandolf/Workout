package com.flandolf.workout.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)


    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("SELECT * FROM workout_exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: Long): ExerciseEntity?

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Delete
    suspend fun deleteSet(set: SetEntity)

    @Query("SELECT * FROM workout_sets WHERE id = :setId LIMIT 1")
    suspend fun getSetById(setId: Int): SetEntity?

    // Lookups to support upsert during download
    @Query("SELECT * FROM workout_exercises WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getExerciseByFirestoreId(firestoreId: String): ExerciseEntity?

    @Query("SELECT * FROM workout_exercises WHERE id = :localId AND workoutId = :localWorkoutId LIMIT 1")
    suspend fun getExerciseByLocalId(localId: Long, localWorkoutId: Long): ExerciseEntity?

    @Query("SELECT * FROM workout_sets WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getSetByFirestoreId(firestoreId: String): SetEntity?

    @Query("SELECT * FROM workout_sets WHERE id = :localId AND exerciseId = :localExerciseId LIMIT 1")
    suspend fun getSetByLocalId(localId: Long, localExerciseId: Long): SetEntity?

    @Update
    suspend fun updateSet(set: SetEntity)

    @Query("UPDATE workouts SET updatedAt = :updatedAt WHERE id = :workoutId")
    suspend fun updateWorkoutUpdatedAt(workoutId: Long, updatedAt: Long)

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    suspend fun getAllWorkoutsWithExercises(): List<WorkoutWithExercises>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun observeAllWorkoutsWithExercises(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithExercises(id: Long): WorkoutWithExercises?

    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    suspend fun getWorkoutByDate(date: Long): Workout?

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC LIMIT 1")
    suspend fun getLatestWorkoutWithExercises(): WorkoutWithExercises?

    @Query("SELECT * FROM workouts WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getWorkoutByFirestoreId(firestoreId: String): Workout?

    @Transaction
    @Query("SELECT * FROM workouts WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getWorkoutWithExercisesByFirestoreId(firestoreId: String): WorkoutWithExercises?

    @Query("SELECT DISTINCT name FROM workout_exercises ORDER BY name")
    suspend fun getDistinctExerciseNames(): List<String>

    @Query(
        """
    SELECT s.* FROM workout_sets s 
    INNER JOIN workout_exercises e ON s.exerciseId = e.id 
        INNER JOIN workouts w ON e.workoutId = w.id 
        WHERE e.name = :exerciseName 
        AND w.id != :currentWorkoutId
        ORDER BY w.date DESC, (s.weight * s.reps) DESC 
        LIMIT 1
    """
    )
    suspend fun getBestSetFromLastWorkout(exerciseName: String, currentWorkoutId: Long): SetEntity?

    // New helpers for sync-down rebuild from nested FS doc
    @Query("DELETE FROM workout_sets WHERE exerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun getMaxPositionForWorkout(workoutId: Long): Int

    @Query(
        """
        SELECT exerciseName, setId, exerciseId, reps, weight, firestoreId FROM (
         SELECT e.name AS exerciseName,
             s.id AS setId,
             s.exerciseId AS exerciseId,
             s.reps AS reps,
             s.weight AS weight,
             s.firestoreId AS firestoreId,
             ROW_NUMBER() OVER (PARTITION BY e.name ORDER BY w.date DESC, (s.weight * s.reps) DESC) AS rn
         FROM workout_sets s
         INNER JOIN workout_exercises e ON s.exerciseId = e.id
            INNER JOIN workouts w ON e.workoutId = w.id
            WHERE e.name IN (:exerciseNames) AND w.id != :currentWorkoutId
        ) ranked
        WHERE rn = 1
    """
    )
    suspend fun getBestSetsForExercises(
        exerciseNames: List<String>,
        currentWorkoutId: Long
    ): List<ExerciseBestSet>

    // New helper to count local workouts
    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getWorkoutCount(): Int

}

data class ExerciseBestSet(
    val exerciseName: String,
    val setId: Int,
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    val firestoreId: String?
)
