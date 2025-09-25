package com.flandolf.workout.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

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

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Delete
    suspend fun deleteSet(set: SetEntity)

    // Lookups to support upsert during download
    @Query("SELECT * FROM exercises WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getExerciseByFirestoreId(firestoreId: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :localId AND workoutId = :localWorkoutId LIMIT 1")
    suspend fun getExerciseByLocalId(localId: Long, localWorkoutId: Long): ExerciseEntity?

    @Query("SELECT * FROM sets WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getSetByFirestoreId(firestoreId: String): SetEntity?

    @Query("SELECT * FROM sets WHERE id = :localId AND exerciseId = :localExerciseId LIMIT 1")
    suspend fun getSetByLocalId(localId: Long, localExerciseId: Long): SetEntity?

    @Update
    suspend fun updateSet(set: SetEntity)

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    suspend fun getAllWorkoutsWithExercises(): List<WorkoutWithExercises>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithExercises(id: Long): WorkoutWithExercises?

    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    suspend fun getWorkoutByDate(date: Long): Workout?

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC LIMIT 1")
    suspend fun getLatestWorkoutWithExercises(): WorkoutWithExercises?

    @Query("SELECT DISTINCT name FROM exercises ORDER BY name")
    suspend fun getDistinctExerciseNames(): List<String>

    @Query(
        """
        SELECT s.* FROM sets s 
        INNER JOIN exercises e ON s.exerciseId = e.id 
        INNER JOIN workouts w ON e.workoutId = w.id 
        WHERE e.name = :exerciseName 
        AND w.id != :currentWorkoutId
        ORDER BY w.date DESC, (s.weight * s.reps) DESC 
        LIMIT 1
    """
    )
    suspend fun getBestSetFromLastWorkout(exerciseName: String, currentWorkoutId: Long): SetEntity?

    // New helpers for sync-down rebuild from nested FS doc
    @Query("DELETE FROM sets WHERE exerciseId IN (SELECT id FROM exercises WHERE workoutId = :workoutId)")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercises WHERE workoutId = :workoutId")
    suspend fun getMaxPositionForWorkout(workoutId: Long): Int

    // New helper to count local workouts
    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getWorkoutCount(): Int

}
