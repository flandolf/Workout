package com.flandolf.workout.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "AuthRepository"
    }
    
    private val _currentUser = MutableStateFlow(auth.currentUser)

    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState: StateFlow<AuthState> = _authState
    
    init {
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _authState.value = if (user != null) {
                AuthState.AUTHENTICATED
            } else {
                AuthState.UNAUTHENTICATED
            }
            Log.d(TAG, "Auth state changed: ${_authState.value}")
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Email sign in successful: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to sign in with email"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email sign in failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create account with email and password
     */
    suspend fun createAccountWithEmailPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.d(TAG, "Account creation successful: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to create account"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign out current user
     */
     fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Log.d(TAG, "User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
            Result.failure(e)
        }
    }

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

}

enum class AuthState {
    LOADING,
    AUTHENTICATED,
    UNAUTHENTICATED
}