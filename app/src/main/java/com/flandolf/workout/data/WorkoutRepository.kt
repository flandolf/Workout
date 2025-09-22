package com.flandolf.workout.data

import android.content.Context
import com.flandolf.workout.data.sync.SyncRepository
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkoutRepository(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.workoutDao() }
    val syncRepository by lazy { SyncRepository(context) }

    suspend fun startWorkout(): Long {
        val currentTime = System.currentTimeMillis()
        val id = dao.insertWorkout(Workout(startTime = currentTime))
        android.util.Log.d("WorkoutRepository", "Started workout local id=$id")

        // Removed automatic sync at workout start to avoid constant syncing.
        return id
    }

    suspend fun endWorkout(id: Long, durationSeconds: Long) {
        val existingWith = dao.getWorkoutWithExercises(id)
        val existing = existingWith?.workout
        android.util.Log.d(
            "WorkoutRepository", "Ending workout local id=$id, exists=${existing != null}"
        )
        if (existing != null) {
            val hasSets = existingWith.exercises.any { ex -> ex.sets.isNotEmpty() }
            android.util.Log.d("WorkoutRepository", "Workout $id hasSets=$hasSets")
            if (!hasSets) {
                // No sets logged, delete the workout entirely
                dao.deleteWorkout(existing)
                syncRepository.deleteWorkoutFromFirestore(id)
            } else {
                val updated = existing.copy(durationSeconds = durationSeconds)
                dao.updateWorkout(updated)
                syncRepository.syncWorkout(updated)
            }
        }
    }

    suspend fun addExercise(workoutId: Long, name: String): Long {
        val maxPos = dao.getMaxPositionForWorkout(workoutId)
        val nextPos = maxPos + 1
        return dao.insertExercise(ExerciseEntity(workoutId = workoutId, name = name, position = nextPos))
    }

    suspend fun updateExercise(exercise: ExerciseEntity) {
        dao.updateExercise(exercise)
    }

    suspend fun addSet(exerciseId: Long, reps: Int, weight: Float): Long {
        return dao.insertSet(SetEntity(exerciseId = exerciseId, reps = reps, weight = weight))
    }

    suspend fun deleteSet(set: SetEntity) {
        dao.deleteSet(set)
    }

    suspend fun getAllWorkouts(): List<WorkoutWithExercises> = dao.getAllWorkoutsWithExercises()

    suspend fun getWorkout(id: Long): WorkoutWithExercises? = dao.getWorkoutWithExercises(id)

    suspend fun getDistinctExerciseNames(): List<String> = dao.getDistinctExerciseNames()

    suspend fun getBestSetFromLastWorkout(
        exerciseName: String, currentWorkoutId: Long
    ): SetEntity? = dao.getBestSetFromLastWorkout(exerciseName, currentWorkoutId)

    /**
     * Exports all workout data to a CSV file with comprehensive information including:
     * - Date and time of workout
     * - Workout duration (both in seconds and formatted)
     * - Exercise details with set-by-set breakdown
     * - Weight, reps, and calculated volume for each set
     * - Proper CSV escaping for special characters
     */
    suspend fun exportCsv(file: File): File = withContext(Dispatchers.IO) {
        val workouts = getAllWorkouts()
        FileWriter(file).use { writer ->
            // Enhanced CSV header with more useful information
            writer.append("Date,Time,Workout Duration (seconds),Workout Duration (formatted),Exercise Name,Set Number,Reps,Weight (kg),Volume (kg),Notes\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (w in workouts) {
                val date = dateFormat.format(Date(w.workout.date))
                val time = timeFormat.format(Date(w.workout.date))
                val durationSeconds = w.workout.durationSeconds
                val durationText = formatDuration(durationSeconds)

                for (ex in w.exercises) {
                    var setIndex = 1
                    for (s in ex.sets) {
                        val volume = s.reps * s.weight
                        val notes = "" // Could be extended to include notes in the future

                        writer.append(escapeCsv(date)).append(',').append(escapeCsv(time))
                            .append(',').append(durationSeconds.toString()).append(',')
                            .append(escapeCsv(durationText)).append(',')
                            .append(escapeCsv(ex.exercise.name)).append(',')
                            .append(setIndex.toString()).append(',').append(s.reps.toString())
                            .append(',').append(String.format(Locale.US, "%.2f", s.weight)).append(',')
                            .append(String.format(Locale.US, "%.2f", volume)).append(',')
                            .append(escapeCsv(notes)).append('\n')
                        setIndex++
                    }
                }
            }
        }
        file
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else value
    }

    suspend fun deleteExercise(exercise: ExerciseEntity) {
        dao.deleteExercise(exercise)
        // Note: Exercise deletion doesn't need immediate sync since it's part of workout
    }

    suspend fun deleteWorkout(workout: Workout) {
        dao.deleteWorkout(workout)
        // Sync the deletion to Firestore
        try {
            syncRepository.deleteWorkoutFromFirestore(workout.id)
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to sync workout deletion", e)
        }
    }

    suspend fun updateSet(set: SetEntity) {
        dao.updateSet(set)
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        return "$mins min"
    }

    suspend fun resetAllData() {
        // Clear local database
        try {
            dao.getAllWorkoutsWithExercises().forEach { workout ->
                dao.deleteWorkout(workout.workout)
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutRepository", "Failed to reset local data", e)
        }
    }


    /**
     * Initialize sync functionality
     */
    suspend fun initializeSync() {
        try {
            syncRepository.initialize()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to initialize sync", e)
        }
    }

    /**
     * Perform manual sync
     */
    suspend fun performSync() {
        try {
            syncRepository.performFullSync()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to perform sync", e)
        }
    }

    /**
     * Sync down from remote database only (download remote changes to local)
     */
    suspend fun syncDown() {
        try {
            syncRepository.syncDown()
        } catch (e: Exception) {
            android.util.Log.w("WorkoutRepository", "Failed to sync down", e)
        }
    }

    /**
     * Import workout data from CSV file with validation rules
     */
    suspend fun importStrongCsv(inputStream: java.io.InputStream): Int =
        withContext(Dispatchers.IO) {
            // Column positions (0-based index)
            val timeColumn = 1
            val durationColumn = 3
            val exerciseNameColumn = 4
            val setTypeColumn = 5
            val weightColumn = 6
            val repsColumn = 7

            // Skip types
            val skipTypes = listOf("Rest Timer", "Note")

            fun isRowValid(row: List<String>): Boolean {
                return row.size > repsColumn // At least 8 fields
            }

            fun shouldSkipRow(row: List<String>): Boolean {
                if (!isRowValid(row)) return true
                val setType = row.getOrNull(setTypeColumn)?.trim() ?: ""
                return setType in skipTypes
            }

            var importedWorkouts = 0
            val workoutMap = mutableMapOf<String, MutableMap<String, Any>>()

            inputStream.bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@withContext 0

                // Detect header presence (some exports include a header row). If the first line
                // contains common header tokens, skip it; otherwise start at line 0.
                var startIndex = 0
                val firstLine = lines.firstOrNull() ?: ""
                if (firstLine.contains("Date", ignoreCase = true) || firstLine.contains("Time", ignoreCase = true) || firstLine.contains("Exercise", ignoreCase = true)) {
                    startIndex = 1
                }

                for (i in startIndex until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    // Robust delimiter detection: try semicolon and comma and prefer the parse
                    // that yields the expected number of columns (or the larger column count).
                    val rowSemicolon = parseCsvLine(line, ";")
                    val rowComma = parseCsvLine(line, ",")
                    val row = when {
                        rowSemicolon.size > repsColumn -> rowSemicolon
                        rowComma.size > repsColumn -> rowComma
                        rowSemicolon.size >= rowComma.size -> rowSemicolon
                        else -> rowComma
                    }
                    android.util.Log.d("WorkoutRepository", "Detected delimiter for line ${i}: ${if (row === rowSemicolon) ";" else ","} (cols=${row.size})")
                     if (shouldSkipRow(row)) continue

                     try {
                         val workoutNumber = row[0].trim().replace("\"", "")
                         val dateStr = row[timeColumn].trim().replace("\"", "")
                         android.util.Log.d("WorkoutRepository", "Parsed CSV date string: '$dateStr'")
                         val durationStr =
                             row.getOrNull(durationColumn)?.trim()?.replace("\"", "") ?: "0"
                         val exerciseName = row[exerciseNameColumn].trim().replace("\"", "")
                         val weightStr = row[weightColumn].trim().replace("\"", "")
                         val repsStr = row[repsColumn].trim().replace("\"", "")

                         if (exerciseName.isBlank()) continue

                         val weight = weightStr.toFloatOrNull() ?: 0f
                         val reps = repsStr.toIntOrNull() ?: 0
                         val duration = durationStr.toLongOrNull() ?: 0L

                         if (reps <= 0) continue

                         // Parse date - handle various formats
                         val workoutDate = parseCsvDate(dateStr)
                         android.util.Log.d("WorkoutRepository", "Parsed CSV date -> epoch: ${'$'}workoutDate")
                         val workoutKey = "${workoutNumber}_${workoutDate}"

                         // Get or create workout
                         val workoutData = workoutMap.getOrPut(workoutKey) {
                             mutableMapOf(
                                 "date" to workoutDate,
                                 "duration" to duration,
                                 "exercises" to mutableMapOf<String, MutableList<Pair<Int, Float>>>()
                             )
                         }

                         // Update duration if this row has a longer duration (in case of inconsistencies)
                         val currentDuration = workoutData["duration"] as Long
                         if (duration > currentDuration) {
                             workoutData["duration"] = duration
                         }

                         @Suppress("UNCHECKED_CAST") val exercises =
                             workoutData["exercises"] as MutableMap<String, MutableList<Pair<Int, Float>>>
                        val sets = exercises.getOrPut(exerciseName) { mutableListOf() }
                        sets.add(Pair(reps, weight))

                    } catch (e: Exception) {
                        android.util.Log.w("WorkoutRepository", "Failed to parse CSV row: $line", e)
                        // Continue with next row
                    }
                }
            }

            // Import workouts into database
            for ((_, workoutData) in workoutMap) {
                try {
                    val date = workoutData["date"] as Long
                    val duration = workoutData["duration"] as Long

                    @Suppress("UNCHECKED_CAST") val exercises =
                        workoutData["exercises"] as Map<String, List<Pair<Int, Float>>>

                    if (exercises.isEmpty()) continue

                    // Create workout with duration
                    val workout = Workout(date = date, durationSeconds = duration, startTime = date)
                    val workoutId = dao.insertWorkout(workout)
                    // Log inserted workout details for verification (human-readable + epoch)
                    android.util.Log.d("WorkoutRepository", "Inserted workout id=$workoutId date=${Date(date)} (epoch=$date)")

                    // Add exercises and sets
                    for ((exerciseName, sets) in exercises) {
                        if (sets.isEmpty()) continue

                        val exercise = ExerciseEntity(workoutId = workoutId, name = exerciseName)
                        val exerciseId = dao.insertExercise(exercise)

                        for ((reps, weight) in sets) {
                            val set =
                                SetEntity(exerciseId = exerciseId, reps = reps, weight = weight)
                            dao.insertSet(set)
                        }
                    }

                    importedWorkouts++

                } catch (e: Exception) {
                    android.util.Log.w("WorkoutRepository", "Failed to import workout", e)
                }
            }

            // After importing, trigger a one-off full sync (upload/download) to ensure cloud matches imports.
            try {
                syncRepository.performFullSync()
            } catch (e: Exception) {
                android.util.Log.w("WorkoutRepository", "One-off sync after import failed", e)
            }

            importedWorkouts
        }

    /**
     * Import workout data from a simpler CSV format
     * Date,Time,Workout Duration (seconds),Workout Duration (formatted),
     * Exercise Name,Set Number,Reps,Weight (kg),Volume (kg),Notes
     */
    suspend fun importWorkoutCsv(inputStream: java.io.InputStream): Int =
        withContext(Dispatchers.IO) {
            val dateColumn = 0
            val timeColumn = 1
            val durationColumn = 2
            val exerciseNameColumn = 4
            val repsColumn = 6
            val weightColumn = 7

            var importedWorkouts = 0
            val workoutMap = mutableMapOf<String, MutableMap<String, Any>>()

            inputStream.bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@withContext 0

                // Skip header if present (detect if first line contains "Date")
                var startIndex = 0
                if (lines[0].startsWith("Date", ignoreCase = true)) {
                    startIndex = 1
                }

                for (i in startIndex until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    val row = parseCsvLine(line)
                    if (row.size <= weightColumn) continue

                    try {
                        val dateStr = row[dateColumn].trim().replace("\"", "")
                        val timeStr = row[timeColumn].trim().replace("\"", "")
                        val durationStr = row[durationColumn].trim().replace("\"", "0")
                        val exerciseName = row[exerciseNameColumn].trim().replace("\"", "")
                        val repsStr = row[repsColumn].trim().replace("\"", "")
                        val weightStr = row[weightColumn].trim().replace("\"", "")

                        if (exerciseName.isBlank()) continue

                        val weight = weightStr.toFloatOrNull() ?: 0f
                        val reps = repsStr.toIntOrNull() ?: 0
                        val duration = durationStr.toLongOrNull() ?: 0L

                        if (reps <= 0) continue

                        // Parse combined date + time into epoch millis
                        val workoutDate = parseCsvDate("$dateStr $timeStr")
                        val workoutKey = "${dateStr}_${timeStr}"

                        val workoutData = workoutMap.getOrPut(workoutKey) {
                            mutableMapOf(
                                "date" to workoutDate,
                                "duration" to duration,
                                "exercises" to mutableMapOf<String, MutableList<Pair<Int, Float>>>()
                            )
                        }

                        val currentDuration = workoutData["duration"] as Long
                        if (duration > currentDuration) {
                            workoutData["duration"] = duration
                        }

                        @Suppress("UNCHECKED_CAST") val exercises =
                            workoutData["exercises"] as MutableMap<String, MutableList<Pair<Int, Float>>>
                        val sets = exercises.getOrPut(exerciseName) { mutableListOf() }
                        sets.add(Pair(reps, weight))

                    } catch (e: Exception) {
                        android.util.Log.w("WorkoutRepository", "Failed to parse CSV row: $line", e)
                    }
                }
            }

            // Insert into DB
            for ((_, workoutData) in workoutMap) {
                try {
                    val date = workoutData["date"] as Long
                    val duration = workoutData["duration"] as Long

                    @Suppress("UNCHECKED_CAST") val exercises =
                        workoutData["exercises"] as Map<String, List<Pair<Int, Float>>>

                    if (exercises.isEmpty()) continue

                    val workout = Workout(date = date, durationSeconds = duration, startTime = date)
                    val workoutId = dao.insertWorkout(workout)

                    for ((exerciseName, sets) in exercises) {
                        if (sets.isEmpty()) continue
                        val exercise = ExerciseEntity(workoutId = workoutId, name = exerciseName)
                        val exerciseId = dao.insertExercise(exercise)

                        for ((reps, weight) in sets) {
                            val set =
                                SetEntity(exerciseId = exerciseId, reps = reps, weight = weight)
                            dao.insertSet(set)
                        }
                    }

                    importedWorkouts++
                } catch (e: Exception) {
                    android.util.Log.w("WorkoutRepository", "Failed to import workout", e)
                }
            }

            // After importing, trigger a one-off full sync (upload/download) to ensure cloud matches imports.
            try {
                syncRepository.performFullSync()
            } catch (e: Exception) {
                android.util.Log.w("WorkoutRepository", "One-off sync after import failed", e)
            }

            importedWorkouts
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

    private fun parseCsvDate(dateStr: String): Long {
        // First, try java.time parsing for ISO and timezone-aware values (best-effort).
        try {
            // Try OffsetDateTime (handles offsets like +01:00 and Z)
            val odt = java.time.OffsetDateTime.parse(dateStr)
            return odt.toInstant().toEpochMilli()
        } catch (_: Exception) {
            // ignore and continue
        }

        try {
            // Try common ISO local date-time (handles both 'T' and space separators)
            val isoCandidates = listOf(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            )
            for (fmt in isoCandidates) {
                try {
                    val ldt = java.time.LocalDateTime.parse(dateStr, fmt)
                    val zdt = ldt.atZone(java.time.ZoneId.systemDefault())
                    return zdt.toInstant().toEpochMilli()
                } catch (_: Exception) {
                    // try next
                }
            }
        } catch (_: Exception) {
            // ignore and continue to other patterns
        }

        // Try other common patterns with java.time (day-first, month-first with time)
        val javaTimePatterns = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        )
        for (p in javaTimePatterns) {
            try {
                val fmt = java.time.format.DateTimeFormatter.ofPattern(p)
                val ldt = java.time.LocalDateTime.parse(dateStr, fmt)
                val zdt = ldt.atZone(java.time.ZoneId.systemDefault())
                return zdt.toInstant().toEpochMilli()
            } catch (_: Exception) {
                // next
            }
        }

        // Fallback to legacy SimpleDateFormat list (as before) for broader compatibility
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        )

        for (format in formats) {
            try {
                return format.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                // Try next format
            }
        }

        // If all formats fail, use current time
        return System.currentTimeMillis()
    }
}
