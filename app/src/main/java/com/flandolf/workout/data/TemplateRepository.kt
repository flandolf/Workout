package com.flandolf.workout.data

import android.content.Context
import androidx.room.withTransaction
import com.flandolf.workout.data.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class TemplateRepository(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.templateDao() }
    val syncRepository by lazy { SyncRepository(context) }

    private suspend fun touchTemplate(templateId: String?) {
        if (templateId == null) return
        try {
            dao.updateTemplateUpdatedAt(templateId, System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.w(
                "TemplateRepository",
                "Failed to update updatedAt for template $templateId",
                e
            )
        }
    }

    fun getAllTemplatesWithExercises(): Flow<List<TemplateWithExercises>> =
        dao.getAllTemplatesWithExercises()

    suspend fun insertTemplate(template: Template): String {
        val now = System.currentTimeMillis()
        dao.insertTemplate(template.copy(updatedAt = now))
        return template.id
    }

    // --- new update helper ---
    suspend fun updateTemplate(template: Template) = withContext(Dispatchers.IO) {
        dao.updateTemplate(template.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun addExercise(templateId: String, name: String): String = withContext(Dispatchers.IO) {
        val maxPos = dao.getMaxPositionForTemplate(templateId)
        val nextPos = maxPos + 1
        val exercise = TemplateExerciseEntity(
            templateId = templateId,
            name = name,
            position = nextPos
        )
        dao.insertExercise(exercise)
        touchTemplate(templateId)
        exercise.id
    }

    suspend fun addExerciseToTemplate(templateId: String, name: String, position: Int? = null): String =
        withContext(Dispatchers.IO) {
            val pos = position ?: (dao.getMaxPositionForTemplate(templateId) + 1)
            val exercise = TemplateExerciseEntity(
                templateId = templateId,
                name = name,
                position = pos
            )
            dao.insertExercise(exercise)
            touchTemplate(templateId)
            exercise.id
        }

    suspend fun deleteExerciseById(exerciseId: String) = withContext(Dispatchers.IO) {
        val exercise = dao.getExerciseById(exerciseId)
        dao.deleteExerciseById(exerciseId)
        touchTemplate(exercise?.templateId)
    }

    suspend fun addSet(exerciseId: String, reps: Int, weight: Float): String =
        withContext(Dispatchers.IO) {
            val set = TemplateSetEntity(
                exerciseId = exerciseId,
                reps = reps,
                weight = weight
            )
            dao.insertSet(set)
            val exercise = dao.getExerciseById(exerciseId)
            touchTemplate(exercise?.templateId)
            set.id
        }

    suspend fun updateSet(setId: String, reps: Int, weight: Float) = withContext(Dispatchers.IO) {
        // Need to preserve exerciseId - fetch existing set using TemplateDao
        val existing = dao.getSetById(setId)
        if (existing != null) {
            dao.updateSet(existing.copy(reps = reps, weight = weight))
            val exercise = dao.getExerciseById(existing.exerciseId)
            touchTemplate(exercise?.templateId)
        }
    }

    suspend fun deleteSetById(setId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getSetById(setId)
        dao.deleteSetById(setId)
        if (existing != null) {
            val exercise = dao.getExerciseById(existing.exerciseId)
            touchTemplate(exercise?.templateId)
        }
    }

    suspend fun getTemplate(id: String): TemplateWithExercises? = withContext(Dispatchers.IO) {
        dao.getTemplateWithExercises(id)
    }

    // Delete a template row by id (and remote if synced)
    suspend fun deleteTemplateById(id: String) = withContext(Dispatchers.IO) {
        val t = dao.getTemplateById(id)
        if (t != null) {
            dao.deleteTemplate(t)
        }
    }

    // Swap positions of two exercises within a template
    suspend fun swapExercisePositions(ex1: TemplateExerciseEntity, ex2: TemplateExerciseEntity): Unit =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val a = ex1.copy(position = ex2.position)
                val b = ex2.copy(position = ex1.position)
                dao.updateExercise(a)
                dao.updateExercise(b)
                touchTemplate(ex1.templateId)
            }
        }

    suspend fun exportTemplatesCsv(outputStream: OutputStream): Unit = withContext(Dispatchers.IO) {
        val templates = dao.getAllTemplatesWithExercisesSuspend()

        outputStream.bufferedWriter().use { writer ->
            // Header row
            writer.appendLine("Template Name,Exercise Name,Set Number,Reps,Weight (kg)")

            for (template in templates) {
                if (template.exercises.isEmpty()) {
                    // Ensure template shows up even if it has no exercises yet
                    writer.append(escapeCsv(template.template.name)).append(',')
                        .append("") // exercise name
                        .append(',')
                        .append("") // set number
                        .append(',')
                        .append("") // reps
                        .append(',')
                        .append("") // weight
                        .appendLine()
                    continue
                }
                for (exercise in template.exercises.sortedBy { it.exercise.position }) {
                    if (exercise.sets.isEmpty()) {
                        writer.append(escapeCsv(template.template.name)).append(',')
                            .append(escapeCsv(exercise.exercise.name)).append(',')
                            .append("") // Set Number
                            .append(',')
                            .append("") // Reps
                            .append(',')
                            .append("") // Weight
                            .appendLine()
                        continue
                    }

                    var setIndex = 1
                    for (set in exercise.sets) {
                        writer.append(escapeCsv(template.template.name)).append(',')
                            .append(escapeCsv(exercise.exercise.name)).append(',')
                            .append(setIndex.toString()).append(',')
                            .append(set.reps.toString()).append(',')
                            .append(String.format(Locale.US, "%.2f", set.weight))
                            .appendLine()
                        setIndex++
                    }
                }
            }
        }
    }

    suspend fun importTemplatesCsv(inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        val templateNameColumn = 0
        val exerciseNameColumn = 1
        val repsColumn = 3
        val weightColumn = 4

        var importedTemplates = 0
        // Map<TemplateName, Map<ExerciseName, MutableList<Pair<Reps, Weight>>>>
        val templateMap = mutableMapOf<String, MutableMap<String, MutableList<Pair<Int, Float>>>>()
        // Track templates that appear with a blank exercise row (template with no exercises)
        val templatesNoExercises = mutableSetOf<String>()

        inputStream.bufferedReader().useLines { sequence ->
            var isFirstLine = true
            sequence.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    return@forEach
                }

                if (isFirstLine) {
                    isFirstLine = false
                    if (line.startsWith("Template Name", ignoreCase = true)) {
                        return@forEach
                    }
                }

                val row = parseCsvLine(rawLine) // keep original to preserve empty fields
                if (row.size <= weightColumn) {
                    // pad if shorter
                    return@forEach
                }

                try {
                    val templateName = row[templateNameColumn].trim().replace("\"", "")
                    val exerciseName = row[exerciseNameColumn].trim().replace("\"", "")
                    val repsStr = row[repsColumn].trim().replace("\"", "")
                    val weightStr = row[weightColumn].trim().replace("\"", "")

                    if (templateName.isBlank()) return@forEach // need a template name at minimum

                    if (exerciseName.isBlank()) {
                        // Template line indicating template exists (possibly no exercises)
                        templatesNoExercises.add(templateName)
                        return@forEach
                    }

                    // Ensure exercise entry exists even if there are no valid sets (empty reps)
                    val exercises = templateMap.getOrPut(templateName) { mutableMapOf() }
                    val sets = exercises.getOrPut(exerciseName) { mutableListOf() }

                    val reps = repsStr.toIntOrNull()
                    val weight = weightStr.toFloatOrNull()
                    if (reps != null && reps > 0) {
                        sets.add(Pair(reps, weight ?: 0f))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TemplateRepository", "Failed to parse CSV row: $rawLine", e)
                }
            }
        }

        // Union of templates from both collections
        val allTemplateNames: Set<String> = (templateMap.keys + templatesNoExercises).toSet()

        for (templateName in allTemplateNames) {
            try {
                val exercises = templateMap[templateName] ?: emptyMap()

                // Check if template already exists
                val existingTemplate = dao.getTemplateByName(templateName)
                val templateId = if (existingTemplate != null) {
                    // Wipe existing exercises & sets so import is authoritative
                    val existingExercises = dao.getExercisesForTemplate(existingTemplate.id)
                    for (ex in existingExercises) {
                        dao.deleteSetsForExercise(ex.id)
                    }
                    dao.deleteExercisesForTemplate(existingTemplate.id)
                    existingTemplate.id
                } else {
                    val template = Template(name = templateName)
                    dao.insertTemplate(template)
                    template.id
                }

                // Insert exercises (even when they have zero sets)
                var position = 0
                for ((exerciseName, sets) in exercises) {
                    val exercise = TemplateExerciseEntity(
                        templateId = templateId,
                        name = exerciseName,
                        position = position
                    )
                    dao.insertExercise(exercise)
                    for ((reps, weight) in sets) {
                        dao.insertSet(
                            TemplateSetEntity(
                                exerciseId = exercise.id,
                                reps = reps,
                                weight = weight
                            )
                        )
                    }
                    position++
                }

                importedTemplates++
                touchTemplate(templateId)
            } catch (e: Exception) {
                android.util.Log.w(
                    "TemplateRepository",
                    "Failed to import template: $templateName",
                    e
                )
            }
        }

        importedTemplates
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else value
    }

    private fun parseCsvLine(line: String, delimiter: String = ","): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes -> inQuotes = true
                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = false
                    }
                }

                char == delimiter.toCharArray()[0] && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    suspend fun getLocalTemplateCount(): Int {
        return dao.getTemplateCount()
    }
}
