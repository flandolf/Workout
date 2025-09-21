package com.flandolf.workout.data.sync

import android.content.Context
import android.util.Log
import com.flandolf.workout.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        private const val COLLECTION_EXERCISES = "exercises"
        private const val COLLECTION_SETS = "sets"
    }

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private var isInitialized = false

    // Deterministic ID helpers
    private fun workoutDocId(userId: String, localId: Long) = "${userId}_w_${localId}"
    private fun exerciseDocId(userId: String, localId: Long, localWorkoutId: Long) = "${userId}_e_${localWorkoutId}_${localId}"
    private fun setDocId(userId: String, localId: Long, localExerciseId: Long) = "${userId}_s_${localExerciseId}_${localId}"

    /**
     * Initialize sync repository - sets up listeners and performs initial sync
     */
    suspend fun initialize() {
        if (isInitialized) return

        try {
            _syncStatus.value = _syncStatus.value.copy(isOnline = true)

            // Perform initial sync if user is authenticated
            if (isUserAuthenticated()) {
                performFullSync()
                setupRealtimeListeners()
            }

            isInitialized = true
            Log.d(TAG, "Sync repository initialized successfully")
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

            // Download remote changes first
            downloadFromFirestore()

            // Upload local changes
            uploadToFirestore()

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
     * Upload local changes to Firestore
     */
    private suspend fun uploadToFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) return

        // We'll batch writes to reduce network round-trips.
        try {
            // Preload remote mappings to avoid per-item lookups.
            Log.d(TAG, "Preloading remote mappings for user=$userId")
            val remoteWorkoutMap = mutableMapOf<Long, String>() // localId -> fsId
            val remoteExerciseMap = mutableMapOf<Pair<Long, Long>, String>() // (localWorkoutId, localExerciseId) -> fsId
            val remoteSetMap = mutableMapOf<Pair<Long, Long>, String>() // (localExerciseId, localSetId) -> fsId

            try {
                val workoutsSnap = firestore.collection(COLLECTION_WORKOUTS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (doc in workoutsSnap.documents) {
                    val fw = doc.toObject<FirestoreWorkout>()
                    if (fw != null && fw.localId > 0) remoteWorkoutMap[fw.localId] = doc.id
                }

                val exercisesSnap = firestore.collection(COLLECTION_EXERCISES)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (doc in exercisesSnap.documents) {
                    val fe = doc.toObject<FirestoreExercise>()
                    if (fe != null && fe.localId > 0) remoteExerciseMap[Pair(fe.localWorkoutId, fe.localId)] = doc.id
                }

                val setsSnap = firestore.collection(COLLECTION_SETS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                for (doc in setsSnap.documents) {
                    val fs = doc.toObject<FirestoreSet>()
                    if (fs != null && fs.localId > 0) remoteSetMap[Pair(fs.localExerciseId, fs.localId)] = doc.id
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preloading remote mappings failed, proceeding without maps", e)
            }

            val localWorkouts = dao.getAllWorkoutsWithExercises()
            Log.d(TAG, "Local workouts count: ${'$'}{localWorkouts.size}")

            var batch = firestore.batch()
            var opsInBatch = 0
            val maxBatchOps = 400 // keep below 500 limit and leave margin

            // Keep track of local->firestore id mappings that need to be persisted
            val workoutsToPersist = mutableListOf<Pair<Long, String>>()
            // exercisesToPersist: Triple(localExerciseId, localWorkoutId, fsId)
            val exercisesToPersist = mutableListOf<Triple<Long, Long, String>>()
            // setsToPersist: Triple(localSetId, localExerciseId, fsId)
            val setsToPersist = mutableListOf<Triple<Long, Long, String>>()

            suspend fun commitBatchIfNeeded() {
                // suspend commit when batch is full
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

                        for ((localExId, localWorkoutId, fsId) in exercisesToPersist) {
                            val ex = dao.getExerciseByLocalId(localExId, localWorkoutId)
                            if (ex != null) dao.updateExercise(ex.copy(firestoreId = fsId))
                        }
                        exercisesToPersist.clear()

                        for ((localSetId, localExerciseId, fsId) in setsToPersist) {
                            val s = dao.getSetByLocalId(localSetId, localExerciseId)
                            if (s != null) dao.updateSet(s.copy(firestoreId = fsId))
                        }
                        setsToPersist.clear()

                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist firestoreIds locally after batch", e)
                    }

                    // start a new batch
                    batch = firestore.batch()
                    opsInBatch = 0
                }
            }

            for (workoutWithExercises in localWorkouts) {
                val workout = workoutWithExercises.workout

                val firestoreWorkout = FirestoreWorkout(
                    localId = workout.id,
                    userId = userId,
                    date = workout.date,
                    durationSeconds = workout.durationSeconds,
                    version = 1L
                )

                // Determine docRef for workout. Prefer local firestoreId, then remote map, then deterministic id.
                val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() }
                    ?: remoteWorkoutMap[workout.id]
                    ?: workoutDocId(userId, workout.id)

                val workoutDocRef = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId)

                batch.set(workoutDocRef, firestoreWorkout)
                opsInBatch++

                // If we generated a new id (no local firestoreId and wasn't in remote map), record it for persistence
                if (workout.firestoreId.isNullOrBlank()) {
                    workoutsToPersist.add(workout.id to workoutDocRef.id)
                }

                // Commit if needed before adding nested children to keep batch size sane
                commitBatchIfNeeded()

                for (exerciseWithSets in workoutWithExercises.exercises) {
                    val exercise = exerciseWithSets.exercise

                    val firestoreExercise = FirestoreExercise(
                        localId = exercise.id,
                        workoutId = workoutDocRef.id,
                        localWorkoutId = workout.id,
                        userId = userId,
                        name = exercise.name,
                        version = 1L
                    )

                    val exerciseFsId = exercise.firestoreId?.takeIf { it.isNotBlank() }
                        ?: remoteExerciseMap[Pair(workout.id, exercise.id)]
                        ?: exerciseDocId(userId, exercise.id, workout.id)

                    val exerciseDocRef = firestore.collection(COLLECTION_EXERCISES).document(exerciseFsId)

                    batch.set(exerciseDocRef, firestoreExercise)
                    opsInBatch++

                    if (exercise.firestoreId.isNullOrBlank()) {
                        exercisesToPersist.add(Triple(exercise.id, workout.id, exerciseDocRef.id))
                    }

                    commitBatchIfNeeded()

                    for (set in exerciseWithSets.sets) {
                        val firestoreSet = FirestoreSet(
                            localId = set.id,
                            exerciseId = exerciseDocRef.id,
                            localExerciseId = exercise.id,
                            userId = userId,
                            reps = set.reps,
                            weight = set.weight,
                            version = 1L
                        )

                        val setFsId = set.firestoreId?.takeIf { it.isNotBlank() }
                            ?: remoteSetMap[Pair(exercise.id, set.id)]
                            ?: setDocId(userId, set.id, exercise.id)

                        val setDocRef = firestore.collection(COLLECTION_SETS).document(setFsId)

                        batch.set(setDocRef, firestoreSet)
                        opsInBatch++

                        if (set.firestoreId.isNullOrBlank()) {
                            setsToPersist.add(Triple(set.id, exercise.id, setDocRef.id))
                        }

                        commitBatchIfNeeded()
                    }
                }
            }

            // Final commit of any remaining operations
            if (opsInBatch > 0) {
                try {
                    batch.commit().await()
                    Log.d(TAG, "Final commit of ${'$'}opsInBatch operations")
                } catch (e: Exception) {
                    Log.e(TAG, "Final batch commit failed", e)
                }

                // Persist any remaining ids
                try {
                    for ((localId, fsId) in workoutsToPersist) {
                        val w = dao.getWorkoutWithExercises(localId)?.workout
                        if (w != null) dao.updateWorkout(w.copy(firestoreId = fsId))
                    }
                    for ((localExId, localWorkoutId, fsId) in exercisesToPersist) {
                        val ex = dao.getExerciseByLocalId(localExId, localWorkoutId)
                        if (ex != null) dao.updateExercise(ex.copy(firestoreId = fsId))
                    }
                    for ((localSetId, localExerciseId, fsId) in setsToPersist) {
                        val s = dao.getSetByLocalId(localSetId, localExerciseId)
                        if (s != null) dao.updateSet(s.copy(firestoreId = fsId))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist remaining firestoreIds locally", e)
                }
            }

            Log.d(TAG, "Uploaded ${localWorkouts.size} workouts to Firestore (batched)")
        } catch (e: Exception) {
            Log.e(TAG, "uploadToFirestore (batched) failed", e)
            throw e
        }
    }

    /**
     * Sync a single workout (batched small commit) — uses deterministic IDs like bulk upload.
     */
    suspend fun syncWorkout(workout: Workout) {
        if (!isUserAuthenticated()) return

        try {
            val userId = getCurrentUserId()
            val batch = firestore.batch()

            val workoutFsId = workout.firestoreId?.takeIf { it.isNotBlank() } ?: workoutDocId(userId, workout.id)
            val workoutDocRef = firestore.collection(COLLECTION_WORKOUTS).document(workoutFsId)

            val firestoreWorkout = FirestoreWorkout(
                localId = workout.id,
                userId = userId,
                date = workout.date,
                durationSeconds = workout.durationSeconds,
                version = 1L
            )

            batch.set(workoutDocRef, firestoreWorkout)

            // Also upload exercises and sets for this workout
            val workoutWith = dao.getWorkoutWithExercises(workout.id)
            if (workoutWith != null) {
                for (exerciseWithSets in workoutWith.exercises) {
                    val exercise = exerciseWithSets.exercise
                    val exerciseFsId = exercise.firestoreId?.takeIf { it.isNotBlank() } ?: exerciseDocId(userId, exercise.id, workout.id)
                    val exerciseDocRef = firestore.collection(COLLECTION_EXERCISES).document(exerciseFsId)

                    val firestoreExercise = FirestoreExercise(
                        localId = exercise.id,
                        workoutId = workoutDocRef.id,
                        localWorkoutId = workout.id,
                        userId = userId,
                        name = exercise.name,
                        version = 1L
                    )
                    batch.set(exerciseDocRef, firestoreExercise)

                    for (set in exerciseWithSets.sets) {
                        val setFsId = set.firestoreId?.takeIf { it.isNotBlank() } ?: setDocId(userId, set.id, exercise.id)
                        val setDocRef = firestore.collection(COLLECTION_SETS).document(setFsId)

                        val firestoreSet = FirestoreSet(
                            localId = set.id,
                            exerciseId = exerciseDocRef.id,
                            localExerciseId = exercise.id,
                            userId = userId,
                            reps = set.reps,
                            weight = set.weight,
                            version = 1L
                        )
                        batch.set(setDocRef, firestoreSet)
                    }
                }
            }

            // Commit the small batch
            batch.commit().await()

            // Persist IDs locally if needed
            try {
                if (workout.firestoreId.isNullOrBlank()) dao.updateWorkout(workout.copy(firestoreId = workoutDocRef.id))
                if (workoutWith != null) {
                    for (exerciseWithSets in workoutWith.exercises) {
                        val ex = exerciseWithSets.exercise
                        if (ex.firestoreId.isNullOrBlank()) dao.updateExercise(ex.copy(firestoreId = exerciseDocId(userId, ex.id, workout.id)))
                        for (s in exerciseWithSets.sets) {
                            if (s.firestoreId.isNullOrBlank()) dao.updateSet(s.copy(firestoreId = setDocId(userId, s.id, ex.id)))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist firestoreIds locally after syncWorkout", e)
            }

            Log.d(TAG, "Synced workout ${workout.id} (batched)")
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
            Log.d(TAG, "Starting to nuke all Firestore data for user: $userId")

            // Delete all sets for this user
            val setsSnapshot = firestore.collection(COLLECTION_SETS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Log.d(TAG, "Deleting ${setsSnapshot.documents.size} sets")
            for (document in setsSnapshot.documents) {
                document.reference.delete().await()
            }

            // Delete all exercises for this user
            val exercisesSnapshot = firestore.collection(COLLECTION_EXERCISES)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Log.d(TAG, "Deleting ${exercisesSnapshot.documents.size} exercises")
            for (document in exercisesSnapshot.documents) {
                document.reference.delete().await()
            }

            // Delete all workouts for this user
            val workoutsSnapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Log.d(TAG, "Deleting ${workoutsSnapshot.documents.size} workouts")
            for (document in workoutsSnapshot.documents) {
                document.reference.delete().await()
            }

            Log.d(TAG, "Successfully nuked all Firestore data for user: $userId")

            // Update sync status to reflect the operation
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

        // Listen for workout changes
        firestore.collection(COLLECTION_WORKOUTS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { _, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for workouts", e)
                    return@addSnapshotListener
                }

                // Handle real-time updates here
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

            // Find and mark workout as deleted
            val snapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("localId", workoutId)
                .get()
                .await()

            for (doc in snapshot.documents) {
                doc.reference.delete().addOnFailureListener {
                    e -> Log.e(TAG, "Failed to delete workout $workoutId from Firestore", e)
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
     * Download changes from Firestore (remote -> local)
     */
    private suspend fun downloadFromFirestore() {
        val userId = getCurrentUserId()
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot download: no user ID")
            return
        }

        try {
            Log.d(TAG, "Starting download from Firestore for user: $userId")

            // Download workouts
            val workoutsSnapshot = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()

            Log.d(TAG, "Found ${'$'}{workoutsSnapshot.documents.size} remote workouts")

            val workoutMap = mutableMapOf<String, Long>() // Firestore ID -> Local ID

            for (document in workoutsSnapshot.documents) {
                val firestoreWorkout = document.toObject<FirestoreWorkout>()
                if (firestoreWorkout != null) {
                    Log.d(TAG, "Processing remote workout: ${'$'}{firestoreWorkout.date}")

                    // Check if workout already exists locally by localId or by date
                    val existingWorkout = if (firestoreWorkout.localId > 0) {
                        try {
                            dao.getWorkoutWithExercises(firestoreWorkout.localId)?.workout
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        try {
                            dao.getWorkoutByDate(firestoreWorkout.date)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val localWorkout = if (existingWorkout != null) {
                        Log.d(TAG, "Updating existing workout with ID: ${'$'}{existingWorkout.id}")
                        val updatedWorkout = existingWorkout.copy(
                            date = firestoreWorkout.date,
                            durationSeconds = firestoreWorkout.durationSeconds
                        )
                        val withFsId = updatedWorkout.copy(firestoreId = document.id)
                        dao.updateWorkout(withFsId)
                        withFsId
                    } else {
                        Log.d(TAG, "Creating new workout for date: ${'$'}{firestoreWorkout.date}")
                        val newWorkout = Workout(
                            date = firestoreWorkout.date,
                            durationSeconds = firestoreWorkout.durationSeconds,
                            firestoreId = document.id
                        )
                        val insertedId = dao.insertWorkout(newWorkout)
                        dao.updateWorkout(newWorkout.copy(id = insertedId, firestoreId = document.id))
                        newWorkout.copy(id = insertedId)
                    }

                    workoutMap[document.id] = localWorkout.id
                }
            }

            // Download exercises
            val exercisesSnapshot = firestore.collection(COLLECTION_EXERCISES)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val exerciseMap = mutableMapOf<String, Long>()

            for (document in exercisesSnapshot.documents) {
                val firestoreExercise = document.toObject<FirestoreExercise>()
                if (firestoreExercise != null) {
                    val localWorkoutId = workoutMap[firestoreExercise.workoutId]
                    if (localWorkoutId != null) {
                        Log.d(TAG, "Upserting exercise: ${'$'}{firestoreExercise.name} for workout ${'$'}localWorkoutId (fsId=${'$'}{document.id})")

                        var localExercise: ExerciseEntity? = dao.getExerciseByFirestoreId(document.id)
                        if (localExercise == null && firestoreExercise.localId > 0L) {
                            localExercise = dao.getExerciseByLocalId(firestoreExercise.localId, localWorkoutId)
                        }

                        if (localExercise != null) {
                            val updated = localExercise.copy(
                                name = firestoreExercise.name,
                                firestoreId = document.id,
                                workoutId = localWorkoutId
                            )
                            dao.updateExercise(updated)
                            exerciseMap[document.id] = updated.id
                        } else {
                            val newExercise = ExerciseEntity(
                                workoutId = localWorkoutId,
                                name = firestoreExercise.name,
                                firestoreId = document.id
                            )
                            val insertedId = dao.insertExercise(newExercise)
                            dao.updateExercise(newExercise.copy(id = insertedId, firestoreId = document.id))
                            exerciseMap[document.id] = insertedId
                        }
                    } else {
                        Log.w(TAG, "Skipping exercise ${'$'}{firestoreExercise.name} - no local workout found for ${'$'}{firestoreExercise.workoutId}")
                    }
                }
            }

            // Download sets
            val setsSnapshot = firestore.collection(COLLECTION_SETS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            for (document in setsSnapshot.documents) {
                val firestoreSet = document.toObject<FirestoreSet>()
                if (firestoreSet != null) {
                    val localExerciseId = exerciseMap[firestoreSet.exerciseId]
                    if (localExerciseId != null) {
                        Log.d(TAG, "Upserting set: ${'$'}{firestoreSet.reps} reps x ${'$'}{firestoreSet.weight} for exercise ${'$'}localExerciseId (fsId=${'$'}{document.id})")

                        var localSet: SetEntity? = dao.getSetByFirestoreId(document.id)
                        if (localSet == null && firestoreSet.localId > 0L) {
                            localSet = dao.getSetByLocalId(firestoreSet.localId, localExerciseId)
                        }

                        if (localSet != null) {
                            val updatedSet = localSet.copy(
                                reps = firestoreSet.reps,
                                weight = firestoreSet.weight,
                                firestoreId = document.id,
                                exerciseId = localExerciseId
                            )
                            dao.updateSet(updatedSet)
                        } else {
                            val newSet = SetEntity(
                                exerciseId = localExerciseId,
                                reps = firestoreSet.reps,
                                weight = firestoreSet.weight,
                                firestoreId = document.id
                            )
                            val insertedId = dao.insertSet(newSet)
                            dao.updateSet(newSet.copy(id = insertedId, firestoreId = document.id))
                        }
                    } else {
                        Log.w(TAG, "Skipping set ${'$'}{firestoreSet.reps}x${'$'}{firestoreSet.weight} - no local exercise found for ${'$'}{firestoreSet.exerciseId}")
                    }
                }
            }

            Log.d(TAG, "Download from Firestore completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from Firestore", e)
            throw e
        }
    }
}
