package com.flandolf.workout.data.sync

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

/**
 * Firestore models that mirror Room entities but with additional sync metadata
 */

data class FirestoreWorkout(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val userId: String = "",
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,
    val isDeleted: Boolean = false,
    val version: Long = 1L
)
data class FirestoreExercise(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val workoutId: String = "",
    val localWorkoutId: Long = 0L,
    val userId: String = "",
    val name: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,
    val isDeleted: Boolean = false,
    val version: Long = 1L
)

data class FirestoreSet(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val exerciseId: String = "",
    val localExerciseId: Long = 0L,
    val userId: String = "",
    val reps: Int = 0,
    val weight: Float = 0f,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null,
    val isDeleted: Boolean = false,
    val version: Long = 1L
)

/**
 * Sync status tracking
 */
data class SyncStatus(
    val isOnline: Boolean = false,
    val lastSyncTime: Long = 0L,
    val pendingUploads: Int = 0,
    val pendingDownloads: Int = 0,
    val hasConflicts: Boolean = false,
    val errorMessage: String? = null
)