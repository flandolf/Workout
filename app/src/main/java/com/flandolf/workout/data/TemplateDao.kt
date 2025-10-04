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
    suspend fun insertTemplate(template: Template)

    @Update
    suspend fun updateTemplate(template: Template)

    @Query("UPDATE templates SET updatedAt = :updatedAt WHERE id = :templateId")
    suspend fun updateTemplateUpdatedAt(templateId: String, updatedAt: Long)

    @Delete
    suspend fun deleteTemplate(template: Template)

    @Insert
    suspend fun insertExercise(exercise: TemplateExerciseEntity)

    @Update
    suspend fun updateExercise(exercise: TemplateExerciseEntity)

    @Query("SELECT * FROM template_exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: String): TemplateExerciseEntity?

    @Delete
    suspend fun deleteExercise(exercise: TemplateExerciseEntity)

    @Insert
    suspend fun insertSet(set: TemplateSetEntity)

    @Update
    suspend fun updateSet(set: TemplateSetEntity)

    @Delete
    suspend fun deleteSet(set: TemplateSetEntity)

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name ASC")
    fun getAllTemplatesWithExercises(): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name ASC")
    suspend fun getAllTemplatesWithExercisesSuspend(): List<TemplateWithExercises>

    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateById(templateId: String): Template?

    @Query("SELECT COALESCE(MAX(position), -1) FROM template_exercises WHERE templateId = :templateId")
    suspend fun getMaxPositionForTemplate(templateId: String): Int

    // --- Convenience operations ---
    @Transaction
    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateWithExercises(templateId: String): TemplateWithExercises?

    @Query("DELETE FROM template_exercises WHERE id = :exerciseId")
    suspend fun deleteExerciseById(exerciseId: String)

    @Query("DELETE FROM template_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: String)

    @Query("SELECT * FROM template_sets WHERE id = :id LIMIT 1")
    suspend fun getSetById(id: String): TemplateSetEntity?

    @Query("SELECT * FROM templates WHERE name = :name LIMIT 1")
    suspend fun getTemplateByName(name: String): Template?

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY position ASC")
    suspend fun getExercisesForTemplate(templateId: String): List<TemplateExerciseEntity>

    @Query("DELETE FROM template_sets WHERE exerciseId = :exerciseId")
    suspend fun deleteSetsForExercise(exerciseId: String)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteExercisesForTemplate(templateId: String)

    // Count templates (used in repository)
    @Query("SELECT COUNT(*) FROM templates")
    suspend fun getTemplateCount(): Int
}
