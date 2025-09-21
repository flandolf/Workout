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
        
        // Upload workouts
        val localWorkouts = dao.getAllWorkoutsWithExercises()
        Log.d(TAG, "Local workouts count: ${localWorkouts.size}")
        for (workoutWithExercises in localWorkouts) {
            val workout = workoutWithExercises.workout
            Log.d(TAG, "Preparing to upload workout id=${workout.id} firestoreId=${workout.firestoreId} date=${workout.date} duration=${workout.durationSeconds}")
            
            val firestoreWorkout = FirestoreWorkout(
                localId = workout.id,
                userId = userId,
                date = workout.date,
                durationSeconds = workout.durationSeconds,
                version = 1L
            )
            
            // If we already have a local firestoreId, write directly to that doc; otherwise try to find by userId+localId
            val workoutFirestoreId = workout.firestoreId?.takeIf { it.isNotBlank() }?.let { firestoreId ->
                val docRef = firestore.collection(COLLECTION_WORKOUTS).document(firestoreId)
                docRef.set(firestoreWorkout).await()
                firestoreId
            } ?: run {
                val existingDocs = firestore.collection(COLLECTION_WORKOUTS)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("localId", workout.id)
                    .get()
                    .await()

                val docRef = if (existingDocs.documents.isNotEmpty()) {
                    existingDocs.documents[0].reference
                } else {
                    firestore.collection(COLLECTION_WORKOUTS).document()
                }

                docRef.set(firestoreWorkout).await()
                val id = docRef.id
                    // Diagnostic: read back the document to verify it exists and what userId it contains
                    try {
                        val snap = docRef.get().await()
                        Log.d(TAG, "Wrote workout doc id=$id exists=${snap.exists()} data=${snap.data}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read back workout doc $id", e)
                    }
                // Persist firestoreId back to local workout
                dao.updateWorkout(workout.copy(firestoreId = id))
                id
            }
            
            // Upload exercises for this workout
            for (exerciseWithSets in workoutWithExercises.exercises) {
                val exercise = exerciseWithSets.exercise
                
                val firestoreExercise = FirestoreExercise(
                    localId = exercise.id,
                    workoutId = workoutFirestoreId,
                    localWorkoutId = workout.id,
                    userId = userId,
                    name = exercise.name,
                    version = 1L
                )
                
                    // For exercises we also prefer to upsert based on localId + localWorkoutId
                    val exerciseFirestoreId = exercise.firestoreId?.takeIf { it.isNotBlank() }?.let { exerciseFsId ->
                        val docRef = firestore.collection(COLLECTION_EXERCISES).document(exerciseFsId)
                        docRef.set(firestoreExercise).await()
                        exerciseFsId
                    } ?: run {
                        val existingExerciseDocs = firestore.collection(COLLECTION_EXERCISES)
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("localId", exercise.id)
                            .whereEqualTo("localWorkoutId", workout.id)
                            .get()
                            .await()

                        val exerciseDocRef = if (existingExerciseDocs.documents.isNotEmpty()) {
                            existingExerciseDocs.documents[0].reference
                        } else {
                            firestore.collection(COLLECTION_EXERCISES).document()
                        }

                        exerciseDocRef.set(firestoreExercise).await()
                        val id = exerciseDocRef.id
                            try {
                                val snap = exerciseDocRef.get().await()
                                Log.d(TAG, "Wrote exercise doc id=${id} exists=${snap.exists()} data=${snap.data}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read back exercise doc $id", e)
                            }
                        // Persist firestoreId back to local exercise
                        dao.updateExercise(exercise.copy(firestoreId = id))
                        id
                    }
                
                // Upload sets for this exercise
                for (set in exerciseWithSets.sets) {
                    val firestoreSet = FirestoreSet(
                        localId = set.id,
                        exerciseId = exerciseFirestoreId,
                        localExerciseId = exercise.id,
                        userId = userId,
                        reps = set.reps,
                        weight = set.weight,
                        version = 1L
                    )
                    
                    // Upsert sets by matching localId and localExerciseId when possible
                    set.firestoreId?.takeIf { it.isNotBlank() }?.let { setFsId ->
                        val docRef = firestore.collection(COLLECTION_SETS).document(setFsId)
                        docRef.set(firestoreSet).await()
                    } ?: run {
                        val existingSetDocs = firestore.collection(COLLECTION_SETS)
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("localId", set.id)
                            .whereEqualTo("localExerciseId", exercise.id)
                            .get()
                            .await()

                        val setDocRef = if (existingSetDocs.documents.isNotEmpty()) {
                            existingSetDocs.documents[0].reference
                        } else {
                            firestore.collection(COLLECTION_SETS).document()
                        }

                        setDocRef.set(firestoreSet).await()
                        val id = setDocRef.id
                            try {
                                val snap = setDocRef.get().await()
                                Log.d(TAG, "Wrote set doc id=${id} exists=${snap.exists()} data=${snap.data}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read back set doc $id", e)
                            }
                        // Persist firestoreId back to local set
                        dao.updateSet(set.copy(firestoreId = id))
                    }
                }
            }
        }
        
        Log.d(TAG, "Uploaded ${localWorkouts.size} workouts to Firestore")
    }
    
    /**
     * Download changes from Firestore
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
                .whereEqualTo("deleted", false)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            Log.d(TAG, "Found ${workoutsSnapshot.documents.size} remote workouts")
            
            val workoutMap = mutableMapOf<String, Long>() // Firestore ID -> Local ID
            
            for (document in workoutsSnapshot.documents) {
                val firestoreWorkout = document.toObject<FirestoreWorkout>()
                if (firestoreWorkout != null) {
                    Log.d(TAG, "Processing remote workout: ${firestoreWorkout.date}")
                    
                    // Check if workout already exists locally by localId or by date
                    val existingWorkout = if (firestoreWorkout.localId > 0) {
                        try {
                            dao.getWorkoutWithExercises(firestoreWorkout.localId)?.workout
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        // If no localId, try to find by date (in case of app reset)
                        try {
                            dao.getWorkoutByDate(firestoreWorkout.date)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    
                    val localWorkout = if (existingWorkout != null) {
                        Log.d(TAG, "Updating existing workout with ID: ${existingWorkout.id}")
                        // Update existing workout
                        val updatedWorkout = existingWorkout.copy(
                            date = firestoreWorkout.date,
                            durationSeconds = firestoreWorkout.durationSeconds
                        )
                        dao.updateWorkout(updatedWorkout)
                        updatedWorkout
                    } else {
                        Log.d(TAG, "Creating new workout for date: ${firestoreWorkout.date}")
                        // Create new workout
                        val newWorkout = Workout(
                            date = firestoreWorkout.date,
                            durationSeconds = firestoreWorkout.durationSeconds,
                            firestoreId = document.id
                        )
                        val insertedId = dao.insertWorkout(newWorkout)
                        // Persist firestoreId to the inserted row (id is known after insert)
                        dao.updateWorkout(newWorkout.copy(id = insertedId, firestoreId = document.id))
                        newWorkout.copy(id = insertedId)
                    }
                    
                    workoutMap[document.id] = localWorkout.id
                }
            }

            // Download exercises
            val exercisesSnapshot = firestore.collection(COLLECTION_EXERCISES)
                .whereEqualTo("userId", userId)
                .whereEqualTo("deleted", false)
                .get()
                .await()
            
            Log.d(TAG, "Found ${exercisesSnapshot.documents.size} remote exercises")
            
            val exerciseMap = mutableMapOf<String, Long>() // Firestore ID -> Local ID
            
            for (document in exercisesSnapshot.documents) {
                val firestoreExercise = document.toObject<FirestoreExercise>()
                if (firestoreExercise != null) {
                    val localWorkoutId = workoutMap[firestoreExercise.workoutId]
                    if (localWorkoutId != null) {
                        Log.d(TAG, "Creating exercise: ${firestoreExercise.name} for workout $localWorkoutId")
                        val newExercise = ExerciseEntity(
                            workoutId = localWorkoutId,
                            name = firestoreExercise.name,
                            firestoreId = document.id
                        )
                        val insertedId = dao.insertExercise(newExercise)
                        // Persist firestoreId to inserted row
                        dao.updateExercise(newExercise.copy(id = insertedId, firestoreId = document.id))
                        exerciseMap[document.id] = insertedId
                    } else {
                        Log.w(TAG, "Skipping exercise ${firestoreExercise.name} - no local workout found for ${firestoreExercise.workoutId}")
                    }
                }
            }
            
            // Download sets
            val setsSnapshot = firestore.collection(COLLECTION_SETS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("deleted", false)
                .get()
                .await()
            
            Log.d(TAG, "Found ${setsSnapshot.documents.size} remote sets")
            
            for (document in setsSnapshot.documents) {
                val firestoreSet = document.toObject<FirestoreSet>()
                if (firestoreSet != null) {
                    val localExerciseId = exerciseMap[firestoreSet.exerciseId]
                    if (localExerciseId != null) {
                        Log.d(TAG, "Creating set: ${firestoreSet.reps} reps x ${firestoreSet.weight} for exercise $localExerciseId")
                        val newSet = SetEntity(
                            exerciseId = localExerciseId,
                            reps = firestoreSet.reps,
                            weight = firestoreSet.weight,
                            firestoreId = document.id
                        )
                        val insertedSetId = dao.insertSet(newSet)
                        dao.updateSet(newSet.copy(id = insertedSetId, firestoreId = document.id))
                    } else {
                        Log.w(TAG, "Skipping set - no local exercise found for ${firestoreSet.exerciseId}")
                    }
                }
            }
            
            Log.d(TAG, "Download from Firestore completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from Firestore", e)
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
     * Sync a single workout
     */
    suspend fun syncWorkout(workout: Workout) {
        if (!isUserAuthenticated()) return
        
        try {
            val userId = getCurrentUserId()
            val firestoreWorkout = FirestoreWorkout(
                localId = workout.id,
                userId = userId,
                date = workout.date,
                durationSeconds = workout.durationSeconds,
                version = 1L
            )
            
            // Try to find an existing workout document with same userId + localId to update instead of creating duplicates
            val existing = firestore.collection(COLLECTION_WORKOUTS)
                .whereEqualTo("userId", firestoreWorkout.userId)
                .whereEqualTo("localId", firestoreWorkout.localId)
                .get()
                .await()

            val docRef = if (existing.documents.isNotEmpty()) {
                existing.documents[0].reference
            } else {
                firestore.collection(COLLECTION_WORKOUTS).document()
            }

            docRef.set(firestoreWorkout).await()
            val workoutFirestoreId = docRef.id
                try {
                    val snap = docRef.get().await()
                    Log.d(TAG, "(syncWorkout) Wrote workout doc id=${workoutFirestoreId} exists=${snap.exists()} data=${snap.data}")
                } catch (e: Exception) {
                    Log.w(TAG, "(syncWorkout) Failed to read back workout doc $workoutFirestoreId", e)
                }

            // Also upload exercises and sets for this workout
            val workoutWith = dao.getWorkoutWithExercises(workout.id)
            if (workoutWith != null) {
                for (exerciseWithSets in workoutWith.exercises) {
                    val exercise = exerciseWithSets.exercise
                    val firestoreExercise = FirestoreExercise(
                        localId = exercise.id,
                        workoutId = workoutFirestoreId,
                        localWorkoutId = workout.id,
                        userId = userId,
                        name = exercise.name,
                        version = 1L
                    )

                    // Upsert exercise doc
                    val existingExerciseDocs = firestore.collection(COLLECTION_EXERCISES)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("localId", exercise.id)
                        .whereEqualTo("localWorkoutId", workout.id)
                        .get()
                        .await()

                    val exerciseDocRef = if (existingExerciseDocs.documents.isNotEmpty()) {
                        existingExerciseDocs.documents[0].reference
                    } else {
                        firestore.collection(COLLECTION_EXERCISES).document()
                    }

                    exerciseDocRef.set(firestoreExercise).await()
                    val exerciseFirestoreId = exerciseDocRef.id

                    // Upsert sets for this exercise
                    for (set in exerciseWithSets.sets) {
                        val firestoreSet = FirestoreSet(
                            localId = set.id,
                            exerciseId = exerciseFirestoreId,
                            localExerciseId = exercise.id,
                            userId = userId,
                            reps = set.reps,
                            weight = set.weight,
                            version = 1L
                        )

                        val existingSetDocs = firestore.collection(COLLECTION_SETS)
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("localId", set.id)
                            .whereEqualTo("localExerciseId", exercise.id)
                            .get()
                            .await()

                        val setDocRef = if (existingSetDocs.documents.isNotEmpty()) {
                            existingSetDocs.documents[0].reference
                        } else {
                            firestore.collection(COLLECTION_SETS).document()
                        }

                        setDocRef.set(firestoreSet).await()
                    }
                }
            }

            Log.d(TAG, "Synced workout ${workout.id} with exercises and sets")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync workout ${workout.id}", e)
        }
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
            
            for (document in snapshot.documents) {
                document.reference.update("deleted", true).await()
            }
            
            Log.d(TAG, "Marked workout $workoutId as deleted in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete workout $workoutId from Firestore", e)
        }
    }

}