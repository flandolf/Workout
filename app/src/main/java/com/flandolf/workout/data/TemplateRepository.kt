package com.flandolf.workout.data

import android.content.Context
import androidx.room.withTransaction
import com.flandolf.workout.data.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TemplateRepository(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.templateDao() }
    val syncRepository by lazy { SyncRepository(context) }

    fun getAllTemplatesWithExercises(): Flow<List<TemplateWithExercises>> =
        dao.getAllTemplatesWithExercises()

    suspend fun insertTemplate(template: Template): Long {
        val id = dao.insertTemplate(template)
        return id
    }

    // --- new update helper ---
    suspend fun updateTemplate(template: Template) = withContext(Dispatchers.IO) {
        dao.updateTemplate(template)
    }

    suspend fun addExercise(templateId: Long, name: String): Long = withContext(Dispatchers.IO) {
        val maxPos = dao.getMaxPositionForTemplate(templateId)
        val nextPos = maxPos + 1
        dao.insertExercise(
            ExerciseEntity(
                templateId = templateId,
                name = name,
                position = nextPos
            )
        )
    }

    suspend fun addExerciseToTemplate(templateId: Long, name: String, position: Int? = null): Long =
        withContext(Dispatchers.IO) {
            val pos = position ?: (dao.getMaxPositionForTemplate(templateId) + 1)
            dao.insertExercise(
                ExerciseEntity(
                    templateId = templateId,
                    name = name,
                    position = pos
                )
            )
        }

    suspend fun deleteExerciseById(exerciseId: Long) = withContext(Dispatchers.IO) {
        dao.deleteExerciseById(exerciseId)
    }

    suspend fun addSet(exerciseId: Long, reps: Int, weight: Float): Long =
        withContext(Dispatchers.IO) {
            dao.insertSet(SetEntity(exerciseId = exerciseId, reps = reps, weight = weight))
        }

    suspend fun updateSet(setId: Int, reps: Int, weight: Float) = withContext(Dispatchers.IO) {
        // Need to preserve exerciseId - fetch existing set using TemplateDao
        val existing = dao.getSetById(setId)
        if (existing != null) {
            dao.updateSet(existing.copy(reps = reps, weight = weight))
        }
    }

    suspend fun deleteSetById(setId: Int) = withContext(Dispatchers.IO) {
        dao.deleteSetById(setId)
    }

    suspend fun getTemplate(id: Long): TemplateWithExercises? = withContext(Dispatchers.IO) {
        dao.getTemplateWithExercises(id)
    }

    // Delete a template row by id
    suspend fun deleteTemplateById(id: Long) = withContext(Dispatchers.IO) {
        val t = dao.getTemplateById(id)
        if (t != null) dao.deleteTemplate(t)
    }

    // Swap positions of two exercises within a template
    suspend fun swapExercisePositions(ex1: ExerciseEntity, ex2: ExerciseEntity) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val a = ex1.copy(position = ex2.position)
                val b = ex2.copy(position = ex1.position)
                dao.updateExercise(a)
                dao.updateExercise(b)
            }
        }
}
