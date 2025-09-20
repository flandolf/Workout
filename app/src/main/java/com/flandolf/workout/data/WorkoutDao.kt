package com.flandolf.workout.data

import androidx.room.*

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

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    suspend fun getAllWorkoutsWithExercises(): List<WorkoutWithExercises>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithExercises(id: Long): WorkoutWithExercises?

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC LIMIT 1")
    suspend fun getLatestWorkoutWithExercises(): WorkoutWithExercises?

    @Query("SELECT DISTINCT name FROM exercises ORDER BY name")
    suspend fun getDistinctExerciseNames(): List<String>

    @Query("""
        SELECT s.* FROM sets s 
        INNER JOIN exercises e ON s.exerciseId = e.id 
        INNER JOIN workouts w ON e.workoutId = w.id 
        WHERE e.name = :exerciseName 
        AND w.id != :currentWorkoutId
        ORDER BY w.date DESC, (s.weight * s.reps) DESC 
        LIMIT 1
    """)
    suspend fun getBestSetFromLastWorkout(exerciseName: String, currentWorkoutId: Long): SetEntity?
}
