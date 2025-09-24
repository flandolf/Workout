package com.flandolf.workout.data

import android.content.Context
import com.flandolf.workout.data.sync.SyncRepository
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TemplateRepository(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.templateDao() }
    val syncRepository by lazy { SyncRepository(context) }

    suspend fun getAllTemplatesWithExercises() = dao.getAllTemplatesWithExercises()
    suspend fun insertTemplate(template: Template): Long {
        val id = dao.insertTemplate(template)
        syncRepository.syncTemplate(template)
        return id
    }

    // Insert a template and its nested exercises/sets in a single transaction.
    // Returns the newly inserted template ID.
    suspend fun insertTemplateWithExercises(templateWithExercises: TemplateWithExercises): Long =
        withContext(Dispatchers.IO) {
            var insertedTemplateId = 0L
            // Use Room's suspend withTransaction so we can call suspend DAO methods inside.
            db.withTransaction {
                // Insert the template row first
                insertedTemplateId = dao.insertTemplate(templateWithExercises.template)

                // Use the workoutDao to insert exercises and sets (reusing workout tables for template content)
                val workoutDao = db.workoutDao()

                for (exWithSets in templateWithExercises.exercises) {
                    // Copy the exercise but point its workoutId to the new template id
                    val exerciseToInsert = exWithSets.exercise.copy(workoutId = insertedTemplateId)
                    val exId = workoutDao.insertExercise(exerciseToInsert)

                    for (s in exWithSets.sets) {
                        workoutDao.insertSet(
                            SetEntity(
                                exerciseId = exId,
                                reps = s.reps,
                                weight = s.weight
                            )
                        )
                    }
                }
            }

            // Attempt to sync the template (currently a no-op in sync repo). Don't fail the insert on sync error.
            try {
                syncRepository.syncTemplate(templateWithExercises.template.copy(id = insertedTemplateId))
            } catch (_: Exception) {
            }

            insertedTemplateId
        }

}
