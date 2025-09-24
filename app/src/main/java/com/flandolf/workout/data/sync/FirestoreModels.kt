package com.flandolf.workout.data.sync

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import androidx.annotation.Keep

/**
 * Firestore models for single-document-per-workout with nested exercises/sets
 */

@Keep
data class FSSet(
    val weight: Double = 0.0,
    val reps: Int = 0
) {
    // Explicit no-arg constructor for Firestore deserialization
    constructor() : this(0.0, 0)
}

@Keep
data class FSExercise(
    val name: String = "",
    val sets: List<FSSet> = emptyList()
) {
    // Explicit no-arg constructor for Firestore deserialization
    constructor() : this("", emptyList())
}

/**
 * Root document stored under workouts/{workoutId}
 */
@Keep
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
) {
    // Explicit no-arg constructor for Firestore deserialization
    constructor() : this(
        id = "",
        localId = 0L,
        userId = "",
        date = 0L,
        durationSeconds = 0L,
        exercises = emptyList(),
        createdAt = null,
        updatedAt = null,
        isDeleted = false,
        version = 1L
    )
}

/**
 * Sync status tracking
 */
@Keep
data class SyncStatus(
    val isOnline: Boolean = false,
    val lastSyncTime: Long = 0L,
    val pendingUploads: Int = 0,
    val pendingDownloads: Int = 0,
    val hasConflicts: Boolean = false,
    val errorMessage: String? = null
)