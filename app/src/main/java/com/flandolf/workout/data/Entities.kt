package com.flandolf.workout.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity(
    tableName = "workouts",
    indices = [Index(value = ["id"], unique = true)]
)
data class Workout(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val startTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
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
    indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val name: String,
    val position: Int = 0,
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
    indices = [Index("exerciseId")]
)
data class SetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val reps: Int,
    val weight: Float
)

@Entity(
    tableName = "templates",
    indices = [Index(value = ["id"], unique = true)]
)
data class Template(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
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
    indices = [Index("templateId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val name: String,
    val position: Int = 0
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
    indices = [Index("exerciseId", unique = true)]
)
data class TemplateSetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val reps: Int,
    val weight: Float
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
