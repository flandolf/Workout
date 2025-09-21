package com.flandolf.workout.data.sync

import android.content.Context
import android.util.Log
import com.flandolf.workout.data.*
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

    companion object {
        private const val TAG = "SyncRepository"
        private const val COLLECTION_WORKOUTS = "workouts"
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

                // No auto-initialize here to avoid unexpected syncs
            }
        }
    }

    // Deterministic ID helpers
    private fun workoutDocId(userId: String, localId: Long) = "${userId}_w_${localId}"

    /**
     * Initialize sync repository; does not auto-run full sync to honor on-demand syncing.
     */
    suspend fun initialize() {
        if (isInitialized) return

        try {
            val currentlyOnline = try { networkMonitor.isOnline.value } catch (_: Exception) { false }
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
     * Perform full bidirectional sync
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

            // IMPORTANT: Upload first (to populate nested docs remotely), then download
            uploadToFirestore()

            // Then pull remote changes
            downloadFromFirestore()

            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                pendingUploads = 0,
                pendingDownloads = 0,
                errorMessage = null
            )

            Log.d(TAG, "Full sync completed successfully")
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

            // Download remote changes only
            downloadFromFirestore()

            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                pendingDownloads = 0,
                errorMessage = null
            )

            Log.d(TAG, "Sync down completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync down failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                pendingDownloads = 0,
                errorMessage = e.message
            )
        }
    }

    /**
     * Upload local changes to Firestore (single-document-per-workout with nested exercises/sets)
     */
    private suspend fun uploadToFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return

        try {
            val localWorkouts = dao.getAllWorkoutsWithExercises()
            Log.d(TAG, "Local workouts count: ${'$'}{localWorkouts.size}")

            var batch = firestore.batch()
            var opsInBatch = 0
            val maxBatchOps = 400 // keep below 500 limit and leave margin

            val workoutsToPersist = mutableListOf<Pair<Long, String>>()

            suspend fun commitBatchIfNeeded() {
                if (opsInBatch >= maxBatchOps) {
                    try {
                        batch.commit().await()
                        Log.d(TAG, "Committed a batch of ${'$'}opsInBatch operations")
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch commit failed", e)
                    }

                    // Persist firestore IDs locally after commit
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

                // Build nested FS model for this workout
                val fsExercises = workoutWithExercises.exercises.map { exWithSets ->
                    FSExercise(
                        name = exWithSets.exercise.name,
                        sets = exWithSets.sets.map { s -> FSSet(weight = s.weight.toDouble(), reps = s.reps) }
                    )
                }
                val totalSets = workoutWithExercises.exercises.sumOf { it.sets.size }
                Log.d(TAG, "Uploading workout ${workout.id}: exercises=${fsExercises.size}, sets=${totalSets}")

                val fsWorkout = FirestoreWorkout(
                    localId = workout.id,
                    userId = userId,
                    date = workout.date,
                    durationSeconds = workout.durationSeconds,
                    exercises = fsExercises,
                    version = 2L
                )

                // Determine docRef for workout. Prefer local firestoreId, else deterministic id.
                val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() }
                    ?: workoutDocId(userId, workout.id)

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
                    Log.d(TAG, "Final commit of ${'$'}opsInBatch operations")
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

            Log.d(TAG, "Uploaded ${localWorkouts.size} workouts to Firestore (nested docs)")
        } catch (e: Exception) {
            Log.e(TAG, "uploadToFirestore (nested) failed", e)
            throw e
        }
    }

    /**
     * Sync a single workout (single nested document)
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
            Log.d(TAG, "Uploading single workout ${workout.id}: exercises=${fsExercises.size}, sets=${totalSetsForSingle}")

            val fsWorkout = FirestoreWorkout(
                localId = workout.id,
                userId = userId,
                date = workout.date,
                durationSeconds = workout.durationSeconds,
                exercises = fsExercises,
                version = 2L
            )

            val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() } ?: workoutDocId(userId, workout.id)
            val workoutDocRef = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId)

            firestore.runBatch { b ->
                b.set(workoutDocRef, fsWorkout)
            }.await()

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
     * Delete all Firestore data for the current user (DESTRUCTIVE OPERATION)
     */
    suspend fun nukeFirestoreData() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot nuke Firestore data: no user ID")
            throw IllegalStateException("User not authenticated")
        }

        try {
            Log.d(TAG, "Starting to nuke all Firestore workouts for user: $userId")

            val workoutsSnapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Log.d(TAG, "Deleting ${workoutsSnapshot.documents.size} workouts")
            for (document in workoutsSnapshot.documents) {
                document.reference.delete().await()
            }

            Log.d(TAG, "Successfully nuked workouts for user: $userId")

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
     * Setup real-time listeners for live sync
     */
    private fun setupRealtimeListeners() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return

        firestore.collection(COLLECTION_WORKOUTS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { _, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for workouts", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "Received real-time workout updates")
            }

        Log.d(TAG, "Real-time listeners setup complete")
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

            Log.d(TAG, "Marked workout ${'$'}workoutId as deleted in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workout ${'$'}workoutId from Firestore", e)
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
            Log.d(TAG, "Starting download from Firestore for user: $userId")

            val workoutsSnapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()

            Log.d(TAG, "Found ${'$'}{workoutsSnapshot.documents.size} remote workouts")

            for (document in workoutsSnapshot.documents) {
                val fsWorkout = document.toObject<FirestoreWorkout>() ?: continue
                Log.d(TAG, "Downloaded workout doc ${document.id}: exercises=${fsWorkout.exercises.size}")

                val existingWorkout = if (fsWorkout.localId > 0) {
                    try { dao.getWorkoutWithExercises(fsWorkout.localId)?.workout } catch (_: Exception) { null }
                } else {
                    try { dao.getWorkoutByDate(fsWorkout.date) } catch (_: Exception) { null }
                }

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
                        firestoreId = document.id
                    )
                    val insertedId = dao.insertWorkout(newWorkout)
                    val persisted = newWorkout.copy(id = insertedId, firestoreId = document.id)
                    dao.updateWorkout(persisted)
                    persisted
                }

                // Only rebuild exercises/sets if the remote has content (avoid wiping on legacy docs)
                if (!fsWorkout.exercises.isNullOrEmpty()) {
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
            }

            Log.d(TAG, "Download from Firestore completed successfully (nested docs)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from Firestore", e)
            throw e
        }
    }
}
