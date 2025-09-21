package com.flandolf.workout.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flandolf.workout.data.sync.AuthRepository
import com.flandolf.workout.data.sync.AuthState
import com.flandolf.workout.data.sync.SyncStatus
import com.flandolf.workout.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val workoutRepository = WorkoutRepository(application.applicationContext)
    private val syncRepository = workoutRepository.syncRepository

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog

    // Guard to avoid repeatedly requesting initialization
    private var requestedInitialize = false

    init {
        // Combine auth and sync states
        viewModelScope.launch {
            combine(
                authRepository.authState,
                syncRepository.syncStatus
            ) { authState, syncStatus ->
                authState to syncStatus
            }.collect { (authState, syncStatus) ->
                _uiState.value = _uiState.value.copy(
                    authState = authState,
                    syncStatus = syncStatus,
                    isInitialized = authState != AuthState.LOADING
                )

                // If authenticated and network is online, ensure sync repository is initialized
                if (authState == AuthState.AUTHENTICATED && syncStatus.isOnline && !requestedInitialize) {
                    requestedInitialize = true
                    viewModelScope.launch {
                        try {
                            workoutRepository.initializeSync()
                        } catch (_: Exception) {
                            // initialization failures are surfaced via SyncRepository.syncStatus
                        }
                    }
                }

                // Reset guard if user signs out
                if (authState == AuthState.UNAUTHENTICATED) {
                    requestedInitialize = false
                }
            }
        }
    }

    /**
     * Manually initialize sync repository (starts listeners / initial sync).
     * Call this only when you want sync to run (e.g., after import or when
     * finishing workflows that should push data to the cloud).
     */
    fun initializeSync() {
        viewModelScope.launch {
            workoutRepository.initializeSync()
        }
    }

    // Anonymous sign-in removed. Use email/password or external providers.

    /**
     * Sign in with email and password
     */
    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Email and password cannot be empty"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.signInWithEmailPassword(email, password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Signed in successfully"
                )
                _showAuthDialog.value = false
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Sign in failed"
                )
            }
        }
    }

    /**
     * Create account with email and password
     */
    fun createAccount(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Email and password cannot be empty"
            )
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Password must be at least 6 characters"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.createAccountWithEmailPassword(email, password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Account created successfully"
                )
                _showAuthDialog.value = false
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Account creation failed"
                )
            }
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.signOut()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    message = "Signed out successfully"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Sign out failed"
                )
            }
        }
    }

    /**
     * Perform manual sync
     */
    fun performSync() {
        viewModelScope.launch {
            workoutRepository.performSync()
        }
    }

    /**
     * Sync down from remote database only (download remote changes to local)
     */
    fun syncDown() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = null,
                errorMessage = null
            )

            try {
                workoutRepository.syncDown()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Downloaded latest data from cloud"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to download data: ${e.message}"
                )
            }
        }
    }

    /**
     * Show authentication dialog
     */
    fun showAuthDialog() {
        _showAuthDialog.value = true
    }

    /**
     * Hide authentication dialog
     */
    fun hideAuthDialog() {
        _showAuthDialog.value = false
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            message = null,
            errorMessage = null
        )
    }

    /**
     * Get current user email
     */
    fun getCurrentUserEmail(): String? = authRepository.getCurrentUserEmail()

    /**
     * Nuke all Firestore data for the current user (DESTRUCTIVE OPERATION)
     */
    fun nukeFirestoreData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = null,
                errorMessage = null
            )

            try {
                syncRepository.nukeFirestoreData()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "All cloud data deleted successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete cloud data: ${e.message}"
                )
            }
        }
    }

}

data class SyncUiState(
    val authState: AuthState = AuthState.LOADING,
    val syncStatus: SyncStatus = SyncStatus(),
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)