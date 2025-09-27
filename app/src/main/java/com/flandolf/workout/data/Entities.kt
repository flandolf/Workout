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
    val startTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId"), Index(value = ["firestoreId"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    // Ordering within a workout or template (lower comes first)
    val position: Int = 0,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId"), Index(value = ["firestoreId"], unique = true)]
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    // Firestore document ID mapping for sync
    val firestoreId: String? = null
)

@Entity(
    tableName = "templates",
    indices = [Index(value = ["id"], unique = true), Index(value = ["firestoreId"], unique = true)]
)
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val firestoreId: String? = null
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index(value = ["firestoreId"], unique = true)]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val position: Int = 0,
    val firestoreId: String? = null
)

@Entity(
    tableName = "template_sets",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId"), Index(value = ["firestoreId"], unique = true)]
)
data class TemplateSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exerciseId: Long,
    val reps: Int,
    val weight: Float,
    val firestoreId: String? = null
)

data class TemplateWithExercises(
    @Embedded val template: Template,
    @Relation(
        entity = TemplateExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "templateId"
    ) val exercises: List<TemplateExerciseWithSets>
)

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<SetEntity>
)

data class TemplateExerciseWithSets(
    @Embedded val exercise: TemplateExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<TemplateSetEntity>
)

data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(
        entity = ExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    ) val exercises: List<ExerciseWithSets>
)
