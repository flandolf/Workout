package com.flandolf.workout.data

import androidx.room.*

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "exercises", foreignKeys = [ForeignKey(
        entity = Workout::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "sets", foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("exerciseId")]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<SetEntity>
)

data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(
        entity = ExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    ) val exercises: List<ExerciseWithSets>
)
