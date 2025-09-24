package com.flandolf.workout.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workouts", indices = [Index(value = ["firestoreId"], unique = true)])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val startTime: Long,
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
    )], indices = [Index("workoutId"), Index(value = ["firestoreId"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    // Ordering within a workout (lower comes first)
    val position: Int = 0,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "sets", foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("exerciseId"), Index(value = ["firestoreId"], unique = true)]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
