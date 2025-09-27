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

    @Query("UPDATE templates SET updatedAt = :updatedAt WHERE id = :templateId")
    suspend fun updateTemplateUpdatedAt(templateId: Long, updatedAt: Long)

    @Delete
    suspend fun deleteTemplate(template: Template)

    @Insert
    suspend fun insertExercise(exercise: TemplateExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: TemplateExerciseEntity)

    @Query("SELECT * FROM template_exercises WHERE id = :exerciseId LIMIT 1")
    suspend fun getExerciseById(exerciseId: Long): TemplateExerciseEntity?

    @Delete
    suspend fun deleteExercise(exercise: TemplateExerciseEntity)

    @Insert
    suspend fun insertSet(set: TemplateSetEntity): Long

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
    suspend fun getTemplateById(templateId: Long): Template?

    @Query("SELECT COALESCE(MAX(position), -1) FROM template_exercises WHERE templateId = :templateId")
    suspend fun getMaxPositionForTemplate(templateId: Long): Int

    // --- Added convenience operations ---
    @Transaction
    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateWithExercises(templateId: Long): TemplateWithExercises?

    @Transaction
    @Query("SELECT * FROM templates WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getTemplateWithExercisesByFirestoreId(firestoreId: String): TemplateWithExercises?

    @Query("DELETE FROM template_exercises WHERE id = :exerciseId")
    suspend fun deleteExerciseById(exerciseId: Long)

    @Query("DELETE FROM template_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: Int)

    @Query("SELECT * FROM template_sets WHERE id = :id LIMIT 1")
    suspend fun getSetById(id: Int): TemplateSetEntity?

    @Query("SELECT * FROM templates WHERE name = :name LIMIT 1")
    suspend fun getTemplateByName(name: String): Template?

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY position ASC")
    suspend fun getExercisesForTemplate(templateId: Long): List<TemplateExerciseEntity>

    @Query("DELETE FROM template_sets WHERE exerciseId = :exerciseId")
    suspend fun deleteSetsForExercise(exerciseId: Long)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteExercisesForTemplate(templateId: Long)

    // Template lookup by firestoreId for sync
    @Query("SELECT * FROM templates WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getTemplateByFirestoreId(firestoreId: String): Template?

    // Count templates (used in repository)
    @Query("SELECT COUNT(*) FROM templates")
    suspend fun getTemplateCount(): Int
}
