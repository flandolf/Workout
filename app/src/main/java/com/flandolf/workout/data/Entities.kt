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
    tableName = "exercises", foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ], indices = [Index("workoutId"), Index("templateId"), Index(value = ["firestoreId"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long? = null, // Nullable to allow exercises to belong to either a workout or a template
    val templateId: Long? = null, // New: links exercise to a template
    val name: String,
    // Ordering within a workout or template (lower comes first)
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

@Entity(tableName = "templates", indices = [Index(value = ["id"], unique = true)])
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

data class TemplateWithExercises(
    @Embedded val template: Template,
    @Relation(
        entity = ExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "templateId"
    ) val exercises: List<ExerciseWithSets>
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
