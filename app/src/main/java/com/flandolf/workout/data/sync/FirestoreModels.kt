package com.flandolf.workout.data.sync

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore models for single-document-per-workout with nested exercises/sets
 */

data class FSSet(
    val weight: Double = 0.0,
    val reps: Int = 0
)

data class FSExercise(
    val name: String = "",
    val sets: List<FSSet> = emptyList()
)

/**
 * Root document stored under workouts/{workoutId}
 */
data class FirestoreWorkout(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val userId: String = "",
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val exercises: List<FSExercise> = emptyList(),
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