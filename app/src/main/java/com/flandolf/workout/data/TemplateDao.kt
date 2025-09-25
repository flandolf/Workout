package com.flandolf.workout.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Insert
    suspend fun insertTemplate(template: Template): Long

    @Update
    suspend fun updateTemplate(template: Template)

    @Delete
    suspend fun deleteTemplate(template: Template)

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateSet(set: SetEntity)

    @Delete
    suspend fun deleteSet(set: SetEntity)

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name ASC")
    fun getAllTemplatesWithExercises(): Flow<List<TemplateWithExercises>>


    @Query("SELECT * FROM templates WHERE id = :workoutId LIMIT 1")
    suspend fun getTemplateById(workoutId: Long): Template?

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercises WHERE templateId = :templateId")
    suspend fun getMaxPositionForTemplate(templateId: Long): Int

    // --- Added convenience operations ---
    @Transaction
    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateWithExercises(templateId: Long): TemplateWithExercises?

    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteExerciseById(exerciseId: Long)

    @Query("DELETE FROM sets WHERE id = :setId")
    suspend fun deleteSetById(setId: Int)

    @Query("SELECT * FROM sets WHERE id = :id LIMIT 1")
    suspend fun getSetById(id: Int): SetEntity?
}
