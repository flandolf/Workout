package com.flandolf.workout.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flandolf.workout.data.AppDatabase
import com.flandolf.workout.data.ExerciseEntity
import com.flandolf.workout.data.SetEntity
import com.flandolf.workout.data.Template
import com.flandolf.workout.data.Workout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class SyncRepository(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.workoutDao()
    private val templateDao = db.templateDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SyncRepository"
        private const val COLLECTION_WORKOUTS = "workouts"
        private const val COLLECTION_TEMPLATES = "templates"
        private const val PREF_LAST_REMOTE_DOWNLOAD = "last_remote_download"
        private const val PREF_LAST_TEMPLATE_REMOTE_DOWNLOAD = "last_template_remote_download"
    }

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private var isInitialized = false

    // Network monitoring
    private val networkMonitor = NetworkMonitor(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe network changes and update sync status accordingly
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                try {
                    _syncStatus.value = _syncStatus.value.copy(isOnline = online)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update syncStatus.isOnline", e)
                }
            }
        }
    }

    // Deterministic ID helpers
    private fun workoutDocId(userId: String, localId: Long) = "${userId}_w_${localId}"
    private fun templateDocId(userId: String, localId: Long) = "${userId}_t_${localId}"

    /**
     * Initialize sync repository; does not auto-run full sync to honor on-demand syncing.
     */
    fun initialize() {
        if (isInitialized) return

        try {
            val currentlyOnline = try {
                networkMonitor.isOnline.value
            } catch (_: Exception) {
                false
            }
            _syncStatus.value = _syncStatus.value.copy(isOnline = currentlyOnline)

            isInitialized = true
            Log.d(TAG, "Sync repository initialized (no auto-sync)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sync repository", e)
            _syncStatus.value = _syncStatus.value.copy(
                isOnline = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Get current user ID
     */
    private fun getCurrentUserId(): String = auth.currentUser?.uid ?: ""

    /**
     * Perform full bidirectional sync (templates + workouts)
     */
    suspend fun performFullSync() {
        if (!isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated, skipping sync")
            return
        }

        try {
            _syncStatus.value = _syncStatus.value.copy(
                pendingUploads = -1, // Indicates sync in progress
                pendingDownloads = -1
            )

            // Upload local changes first
            uploadTemplatesToFirestore()
            uploadWorkoutsToFirestore()

            // Then pull remote changes
            downloadTemplatesFromFirestore()
            downloadFromFirestore()

            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                pendingUploads = 0,
                pendingDownloads = 0,
                errorMessage = null
            )

            Log.d(TAG, "Full sync (templates + workouts) completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                pendingUploads = 0,
                pendingDownloads = 0,
                errorMessage = e.message
            )
        }
    }

    /**
     * Sync down from remote database only (download remote changes to local)
     */
    suspend fun syncDown() {
        if (!isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated, skipping sync down")
            return
        }

        try {
            _syncStatus.value = _syncStatus.value.copy(
                pendingDownloads = -1 // Indicates sync in progress
            )

            downloadTemplatesFromFirestore()
            downloadFromFirestore()

            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                pendingDownloads = 0,
                errorMessage = null
            )

            Log.d(TAG, "Sync down (templates + workouts) completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync down failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                pendingDownloads = 0,
                errorMessage = e.message
            )
        }
    }

    /**
     * Workout upload logic (renamed private method)
     */
    private suspend fun uploadWorkoutsToFirestore() = uploadToFirestore()

    /**
     * Upload local workouts to Firestore (single-document-per-workout with nested exercises/sets)
     */
    private fun computeWorkoutContentHash(fsExercises: List<FSExercise>, workout: Workout): String {
        val sb = StringBuilder()
        sb.append(workout.date).append(':').append(workout.durationSeconds).append('|')
        for (ex in fsExercises) {
            sb.append(ex.name).append('#')
            for (s in ex.sets) {
                sb.append(s.weight).append('x').append(s.reps).append(';')
            }
            sb.append('|')
        }
        return sb.toString().hashCode().toString()
    }

    /** Compute template content hash */
    private fun computeTemplateContentHash(fsExercises: List<FSExercise>, template: Template): String {
        val sb = StringBuilder()
        sb.append(template.name).append('|')
        for (ex in fsExercises) {
            sb.append(ex.name).append('#')
            for (s in ex.sets) {
                sb.append(s.weight).append('x').append(s.reps).append(';')
            }
            sb.append('|')
        }
        return sb.toString().hashCode().toString()
    }

    private fun getLastRemoteDownloadTime(): Long = prefs.getLong(PREF_LAST_REMOTE_DOWNLOAD, 0L)
    private fun setLastRemoteDownloadTime(value: Long) =
        prefs.edit { putLong(PREF_LAST_REMOTE_DOWNLOAD, value) }

    private fun getLastTemplateRemoteDownloadTime(): Long = prefs.getLong(PREF_LAST_TEMPLATE_REMOTE_DOWNLOAD, 0L)
    private fun setLastTemplateRemoteDownloadTime(value: Long) =
        prefs.edit { putLong(PREF_LAST_TEMPLATE_REMOTE_DOWNLOAD, value) }

    /**
     * Upload workouts (existing logic kept as-is)
     */
    private suspend fun uploadToFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return

        try {
            val localWorkouts = dao.getAllWorkoutsWithExercises()
            Log.d(TAG, "Local workouts count: ${localWorkouts.size}")

            var batch = firestore.batch()
            var opsInBatch = 0
            val maxBatchOps = 400

            val workoutsToPersist = mutableListOf<Pair<Long, String>>()
            var skippedUnchanged = 0

            suspend fun commitBatchIfNeeded() {
                if (opsInBatch >= maxBatchOps) {
                    try {
                        batch.commit().await()
                        Log.d(TAG, "Committed a batch of $opsInBatch operations")
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch commit failed", e)
                    }

                    try {
                        for ((localId, fsId) in workoutsToPersist) {
                            val w = dao.getWorkoutWithExercises(localId)?.workout
                            if (w != null) dao.updateWorkout(w.copy(firestoreId = fsId))
                        }
                        workoutsToPersist.clear()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist firestoreIds locally after batch", e)
                    }

                    batch = firestore.batch()
                    opsInBatch = 0
                }
            }

            for (workoutWithExercises in localWorkouts) {
                val workout = workoutWithExercises.workout
                val fsExercises = workoutWithExercises.exercises.map { exWithSets ->
                    FSExercise(
                        name = exWithSets.exercise.name,
                        sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                    )
                }
                val totalSets = workoutWithExercises.exercises.sumOf { it.sets.size }
                Log.d(TAG, "Uploading workout ${workout.id}: exercises=${fsExercises.size}, sets=$totalSets")

                val contentHash = computeWorkoutContentHash(fsExercises, workout)
                val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() }
                    ?: workoutDocId(userId, workout.id)
                var shouldUpload = true
                if (!workout.firestoreId.isNullOrBlank()) {
                    try {
                        val remoteSnap = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId).get().await()
                        val remoteHash = remoteSnap.getString("contentHash") ?: ""
                        if (remoteHash == contentHash) {
                            shouldUpload = false
                            skippedUnchanged++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to check remote hash for ${workout.id}", e)
                    }
                }
                if (!shouldUpload) continue

                val fsWorkout = FirestoreWorkout(
                    localId = workout.id,
                    userId = userId,
                    date = workout.date,
                    durationSeconds = workout.durationSeconds,
                    exercises = fsExercises,
                    contentHash = contentHash,
                    version = 2L
                )
                val workoutDocRef = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId)
                batch.set(workoutDocRef, fsWorkout)
                opsInBatch++
                if (workout.firestoreId.isNullOrBlank()) {
                    workoutsToPersist.add(workout.id to workoutDocRef.id)
                }
                commitBatchIfNeeded()
            }

            if (opsInBatch > 0) {
                try {
                    batch.commit().await()
                    Log.d(TAG, "Final commit of $opsInBatch operations")
                } catch (e: Exception) {
                    Log.e(TAG, "Final batch commit failed", e)
                }
                try {
                    for ((localId, fsId) in workoutsToPersist) {
                        val w = dao.getWorkoutWithExercises(localId)?.workout
                        if (w != null) dao.updateWorkout(w.copy(firestoreId = fsId))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist remaining firestoreIds locally", e)
                }
            }
            Log.d(TAG, "Uploaded ${localWorkouts.size - skippedUnchanged} workouts (skipped $skippedUnchanged unchanged) to Firestore (nested docs)")
        } catch (e: Exception) {
            Log.e(TAG, "uploadToFirestore (nested) failed", e)
            throw e
        }
    }

    /**
     * Upload templates with nested exercises/sets
     */
    private suspend fun uploadTemplatesToFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return

        try {
            val localTemplates = templateDao.getAllTemplatesWithExercisesSuspend()
            Log.d(TAG, "Local templates count: ${localTemplates.size}")

            var batch = firestore.batch()
            var opsInBatch = 0
            val maxBatchOps = 400

            val templatesToPersist = mutableListOf<Pair<Long, String>>()
            var skippedUnchanged = 0

            suspend fun commitBatchIfNeeded() {
                if (opsInBatch >= maxBatchOps) {
                    try {
                        batch.commit().await()
                        Log.d(TAG, "Committed a template batch of $opsInBatch operations")
                    } catch (e: Exception) {
                        Log.e(TAG, "Template batch commit failed", e)
                    }

                    try {
                        for ((localId, fsId) in templatesToPersist) {
                            val t = templateDao.getTemplateById(localId)
                            if (t != null) templateDao.updateTemplate(t.copy(firestoreId = fsId))
                        }
                        templatesToPersist.clear()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist template firestoreIds locally after batch", e)
                    }

                    batch = firestore.batch()
                    opsInBatch = 0
                }
            }

            for (templateWithExercises in localTemplates) {
                val template = templateWithExercises.template
                val fsExercises = templateWithExercises.exercises.map { exWithSets ->
                    FSExercise(
                        name = exWithSets.exercise.name,
                        sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                    )
                }
                val contentHash = computeTemplateContentHash(fsExercises, template)
                val templateFsId = template.firestoreId?.takeIf { it.isNotBlank() }
                    ?: templateDocId(userId, template.id)

                var shouldUpload = true
                if (!template.firestoreId.isNullOrBlank()) {
                    try {
                        val remoteSnap = firestore.collection(COLLECTION_TEMPLATES).document(templateFsId).get().await()
                        val remoteHash = remoteSnap.getString("contentHash") ?: ""
                        if (remoteHash == contentHash) {
                            shouldUpload = false
                            skippedUnchanged++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to check remote template hash for ${template.id}", e)
                    }
                }
                if (!shouldUpload) continue

                val fsTemplate = FirestoreTemplate(
                    localId = template.id,
                    userId = userId,
                    name = template.name,
                    exercises = fsExercises,
                    contentHash = contentHash,
                    version = 1L
                )
                val docRef = firestore.collection(COLLECTION_TEMPLATES).document(templateFsId)
                batch.set(docRef, fsTemplate)
                opsInBatch++
                if (template.firestoreId.isNullOrBlank()) templatesToPersist.add(template.id to docRef.id)
                commitBatchIfNeeded()
            }

            if (opsInBatch > 0) {
                try {
                    batch.commit().await()
                    Log.d(TAG, "Final template commit of $opsInBatch operations")
                } catch (e: Exception) {
                    Log.e(TAG, "Final template batch commit failed", e)
                }
                try {
                    for ((localId, fsId) in templatesToPersist) {
                        val t = templateDao.getTemplateById(localId)
                        if (t != null) templateDao.updateTemplate(t.copy(firestoreId = fsId))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist remaining template firestoreIds locally", e)
                }
            }
            Log.d(TAG, "Uploaded ${localTemplates.size - skippedUnchanged} templates (skipped $skippedUnchanged unchanged) to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "uploadTemplatesToFirestore failed", e)
            throw e
        }
    }

    /**
     * Sync a single workout (unchanged)
     */
    suspend fun syncWorkout(workout: Workout) {
        if (!isUserAuthenticated()) return
        try {
            val userId = getCurrentUserId()
            val workoutWith = dao.getWorkoutWithExercises(workout.id)
            val fsExercises = workoutWith?.exercises?.map { exWithSets ->
                FSExercise(
                    name = exWithSets.exercise.name,
                    sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                )
            } ?: emptyList()
            val totalSetsForSingle = workoutWith?.exercises?.sumOf { it.sets.size } ?: 0
            Log.d(TAG, "Uploading single workout ${workout.id}: exercises=${fsExercises.size}, sets=$totalSetsForSingle")
            val contentHash = computeWorkoutContentHash(fsExercises, workout)
            val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() } ?: workoutDocId(userId, workout.id)
            var shouldUpload = true
            if (!workout.firestoreId.isNullOrBlank()) {
                try {
                    val remoteSnap = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId).get().await()
                    val remoteHash = remoteSnap.getString("contentHash") ?: ""
                    if (remoteHash == contentHash) {
                        shouldUpload = false
                        Log.d(TAG, "Skipping upload for workout ${workout.id}: unchanged content hash")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check remote hash for single workout", e)
                }
            }
            if (!shouldUpload) return
            val fsWorkout = FirestoreWorkout(
                localId = workout.id,
                userId = userId,
                date = workout.date,
                durationSeconds = workout.durationSeconds,
                exercises = fsExercises,
                contentHash = contentHash,
                version = 2L
            )
            val workoutDocRef = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId)
            firestore.runBatch { b -> b.set(workoutDocRef, fsWorkout) }.await()
            try {
                if (workout.firestoreId.isNullOrBlank()) dao.updateWorkout(workout.copy(firestoreId = workoutDocRef.id))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist firestoreId locally after syncWorkout", e)
            }
            Log.d(TAG, "Synced workout ${workout.id} (nested doc)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync workout ${workout.id}", e)
        }
    }

    /**
     * Sync a single template (new method)
     */
    suspend fun syncTemplateById(templateId: Long) {
        if (!isUserAuthenticated()) return
        try {
            val userId = getCurrentUserId()
            val tmplWith = templateDao.getTemplateWithExercises(templateId) ?: return
            // Skip empty unnamed templates
            if (tmplWith.template.name.isBlank() && tmplWith.exercises.isEmpty()) return

            val fsExercises = tmplWith.exercises.sortedBy { it.exercise.position }.map { exWithSets ->
                FSExercise(
                    name = exWithSets.exercise.name,
                    sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                )
            }
            val contentHash = computeTemplateContentHash(fsExercises, tmplWith.template)
            val template = tmplWith.template
            val templateFsId = template.firestoreId?.takeIf { it.isNotBlank() } ?: templateDocId(userId, template.id)

            var shouldUpload = true
            if (!template.firestoreId.isNullOrBlank()) {
                try {
                    val remoteSnap = firestore.collection(COLLECTION_TEMPLATES).document(templateFsId).get().await()
                    val remoteHash = remoteSnap.getString("contentHash") ?: ""
                    if (remoteHash == contentHash) shouldUpload = false
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check remote hash for template ${'$'}{template.id}", e)
                }
            }
            if (!shouldUpload) return

            val fsTemplate = FirestoreTemplate(
                localId = template.id,
                userId = userId,
                name = template.name,
                exercises = fsExercises,
                contentHash = contentHash,
                version = 1L
            )
            val docRef = firestore.collection(COLLECTION_TEMPLATES).document(templateFsId)
            firestore.runBatch { b -> b.set(docRef, fsTemplate) }.await()
            if (template.firestoreId.isNullOrBlank()) {
                try { templateDao.updateTemplate(template.copy(firestoreId = docRef.id)) } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist template firestoreId after single sync", e)
                }
            }
            Log.d(TAG, "Synced template ${'$'}{template.id} (single doc)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync template ${'$'}templateId", e)
        }
    }

    /**
     * Delete all Firestore data for the current user (DESTRUCTIVE OPERATION)
     */
    suspend fun nukeFirestoreData() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot nuke Firestore data: no user ID")
            throw IllegalStateException("User not authenticated")
        }
        try {
            Log.d(TAG, "Starting to nuke all Firestore workouts & templates for user: $userId")
            val workoutsSnapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Log.d(TAG, "Deleting ${workoutsSnapshot.documents.size} workouts")
            for (document in workoutsSnapshot.documents) {
                document.reference.delete().await()
            }
            val templatesSnapshot = firestore.collection(COLLECTION_TEMPLATES)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Log.d(TAG, "Deleting ${templatesSnapshot.documents.size} templates")
            for (document in templatesSnapshot.documents) {
                document.reference.delete().await()
            }
            Log.d(TAG, "Successfully nuked workouts & templates for user: $userId")
            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                pendingUploads = 0,
                pendingDownloads = 0,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to nuke Firestore data", e)
            _syncStatus.value = _syncStatus.value.copy(
                errorMessage = "Failed to delete cloud data: ${e.message}"
            )
            throw e
        }
    }

    /**
     * Delete workout from Firestore
     */
    suspend fun deleteWorkoutFromFirestore(workoutId: Long) {
        if (!isUserAuthenticated()) return
        try {
            val userId = getCurrentUserId()
            val snapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("localId", workoutId)
                .get()
                .await()
            for (doc in snapshot.documents) {
                doc.reference.delete().addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete workout $workoutId from Firestore", e)
                }.addOnSuccessListener {
                    Log.d(TAG, "Deleted workout $workoutId from Firestore")
                }
            }
            Log.d(TAG, "Marked workout $workoutId as deleted in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workout $workoutId from Firestore", e)
        }
    }

    /**
     * Add template deletion from Firestore
     */
    suspend fun deleteTemplateFromFirestore(templateId: Long, firestoreId: String? = null) {
        if (!isUserAuthenticated()) return
        try {
            val userId = getCurrentUserId()
            if (!firestoreId.isNullOrBlank()) {
                // Try direct delete first
                try {
                    firestore.collection(COLLECTION_TEMPLATES).document(firestoreId).delete().await()
                    Log.d(TAG, "Deleted template ${'$'}templateId (doc ${'$'}firestoreId) from Firestore")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Direct delete by firestoreId failed, falling back to query", e)
                }
            }
            val snapshot = firestore.collection(COLLECTION_TEMPLATES)
                .whereEqualTo("userId", userId)
                .whereEqualTo("localId", templateId)
                .get()
                .await()
            for (doc in snapshot.documents) {
                doc.reference.delete().addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete template ${'$'}templateId from Firestore", e)
                }.addOnSuccessListener {
                    Log.d(TAG, "Deleted template ${'$'}templateId from Firestore (queried)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete template ${'$'}templateId from Firestore", e)
        }
    }

    /**
     * Download changes from Firestore (remote -> local) for nested workout docs
     */
    private suspend fun downloadFromFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot download: no user ID")
            return
        }
        try {
            Log.d(TAG, "Starting workout download from Firestore for user: $userId")
            val lastDownload = getLastRemoteDownloadTime()
            val query = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
            val workoutsSnapshot = query.get().await()
            Log.d(TAG, "Found ${workoutsSnapshot.documents.size} remote workouts (lastDownload=$lastDownload)")
            var processed = 0
            var skippedHash = 0
            var newestTimestamp = lastDownload
            for (document in workoutsSnapshot.documents) {
                val fsWorkout = document.toObject<FirestoreWorkout>() ?: continue
                Log.d(TAG, "Downloaded workout doc ${document.id}: exercises=${fsWorkout.exercises.size}")
                val localExisting = if (fsWorkout.localId > 0) dao.getWorkoutWithExercises(fsWorkout.localId)?.workout else null
                if (localExisting != null && fsWorkout.contentHash.isNotBlank()) {
                    try {
                        val localWith = dao.getWorkoutWithExercises(localExisting.id)
                        val localFsExercises = localWith?.exercises?.map { exWithSets ->
                            FSExercise(
                                name = exWithSets.exercise.name,
                                sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                            )
                        } ?: emptyList()
                        val localHash = computeWorkoutContentHash(localFsExercises, localExisting)
                        if (localHash == fsWorkout.contentHash) {
                            skippedHash++
                            continue
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to compute local hash for potential skip", e)
                    }
                }
                val existingWorkout = if (fsWorkout.localId > 0) {
                    try { dao.getWorkoutWithExercises(fsWorkout.localId)?.workout } catch (_: Exception) { null }
                } else { try { dao.getWorkoutByDate(fsWorkout.date) } catch (_: Exception) { null } }
                val localWorkout = if (existingWorkout != null) {
                    val updatedWorkout = existingWorkout.copy(
                        date = fsWorkout.date,
                        durationSeconds = fsWorkout.durationSeconds,
                        firestoreId = document.id
                    )
                    dao.updateWorkout(updatedWorkout)
                    updatedWorkout
                } else {
                    val newWorkout = Workout(
                        date = fsWorkout.date,
                        durationSeconds = fsWorkout.durationSeconds,
                        startTime = fsWorkout.date,
                        firestoreId = document.id
                    )
                    val insertedId = dao.insertWorkout(newWorkout)
                    val persisted = newWorkout.copy(id = insertedId, firestoreId = document.id)
                    dao.updateWorkout(persisted)
                    persisted
                }
                if (fsWorkout.exercises.isNotEmpty()) {
                    try {
                        dao.deleteSetsForWorkout(localWorkout.id)
                        dao.deleteExercisesForWorkout(localWorkout.id)
                        for (ex in fsWorkout.exercises) {
                            val exId = dao.insertExercise(ExerciseEntity(workoutId = localWorkout.id, name = ex.name))
                            for (s in ex.sets) {
                                dao.insertSet(SetEntity(exerciseId = exId, reps = s.reps, weight = s.weight.toFloat()))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to rebuild exercises for workout ${localWorkout.id}", e)
                    }
                } else {
                    Log.d(TAG, "Skipping exercise rebuild for workout ${localWorkout.id}: remote has empty/missing exercises")
                }
                processed++
                if (fsWorkout.date > newestTimestamp) newestTimestamp = fsWorkout.date
            }
            setLastRemoteDownloadTime(newestTimestamp)
            Log.d(TAG, "Workout download from Firestore completed: processed=$processed skipped=$skippedHash newestTs=$newestTimestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download workouts from Firestore", e)
            throw e
        }
    }

    /**
     * Download template changes from Firestore (remote -> local)
     */
    private suspend fun downloadTemplatesFromFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot download templates: no user ID")
            return
        }
        try {
            Log.d(TAG, "Starting template download from Firestore for user: $userId")
            val lastDownload = getLastTemplateRemoteDownloadTime()
            val query = firestore.collection(COLLECTION_TEMPLATES)
                .whereEqualTo("userId", userId)
                .orderBy("name", Query.Direction.ASCENDING)
            val templatesSnapshot = query.get().await()
            Log.d(TAG, "Found ${templatesSnapshot.documents.size} remote templates (lastDownload=$lastDownload)")
            var processed = 0
            var skippedHash = 0
            for (document in templatesSnapshot.documents) {
                val fsTemplate = document.toObject<FirestoreTemplate>() ?: continue
                if (fsTemplate.isDeleted) {
                    // If marked deleted remotely, attempt local delete
                    try {
                        templateDao.getTemplateByFirestoreId(document.id)?.let { templateDao.deleteTemplate(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed local delete for remote-deleted template ${document.id}", e)
                    }
                    continue
                }
                val localExisting = if (fsTemplate.localId > 0) templateDao.getTemplateById(fsTemplate.localId) else templateDao.getTemplateByFirestoreId(document.id)
                if (localExisting != null && fsTemplate.contentHash.isNotBlank()) {
                    try {
                        val localWith = templateDao.getTemplateWithExercises(localExisting.id)
                        val localFsExercises = localWith?.exercises?.map { exWithSets ->
                            FSExercise(
                                name = exWithSets.exercise.name,
                                sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                            )
                        } ?: emptyList()
                        val localHash = computeTemplateContentHash(localFsExercises, localExisting)
                        if (localHash == fsTemplate.contentHash) {
                            skippedHash++
                            continue
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to compute local template hash for potential skip", e)
                    }
                }
                val localTemplate = if (localExisting != null) {
                    val updated = localExisting.copy(name = fsTemplate.name, firestoreId = document.id)
                    templateDao.updateTemplate(updated)
                    updated
                } else {
                    val newTemplate = Template(name = fsTemplate.name, firestoreId = document.id)
                    val insertedId = templateDao.insertTemplate(newTemplate)
                    val persisted = newTemplate.copy(id = insertedId, firestoreId = document.id)
                    templateDao.updateTemplate(persisted)
                    persisted
                }
                // Rebuild exercises for template
                try {
                    val existingExercises = templateDao.getExercisesForTemplate(localTemplate.id)
                    for (ex in existingExercises) {
                        templateDao.deleteSetsForExercise(ex.id)
                    }
                    templateDao.deleteExercisesForTemplate(localTemplate.id)
                    fsTemplate.exercises.forEachIndexed { index, ex ->
                        val exId = templateDao.insertExercise(ExerciseEntity(templateId = localTemplate.id, name = ex.name, position = index))
                        for (s in ex.sets) {
                            templateDao.insertSet(SetEntity(exerciseId = exId, reps = s.reps, weight = s.weight.toFloat()))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to rebuild exercises for template ${localTemplate.id}", e)
                }
                processed++
            }
            setLastTemplateRemoteDownloadTime(System.currentTimeMillis())
            Log.d(TAG, "Template download completed: processed=$processed skipped=$skippedHash")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download templates from Firestore", e)
            throw e
        }
    }

    /**
     * Return the number of workouts for the current user on Firestore.
     */
    suspend fun getRemoteWorkoutCount(): Int {
        if (!isUserAuthenticated()) return 0
        return try {
            val userId = getCurrentUserId()
            val snapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snapshot.documents.size
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get remote workout count", e)
            0
        }
    }

    /**
     * Return the number of templates for the current user on Firestore.
     */
    suspend fun getRemoteTemplateCount(): Int {
        if (!isUserAuthenticated()) return 0
        return try {
            val userId = getCurrentUserId()
            val snapshot = firestore.collection(COLLECTION_TEMPLATES)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snapshot.documents.size
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get remote template count", e)
            0
        }
    }
}
