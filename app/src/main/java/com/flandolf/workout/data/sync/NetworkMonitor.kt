package com.flandolf.workout.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight network monitor using ConnectivityManager.
 * Exposes a StateFlow<Boolean> indicating whether the device currently
 * has an active network with internet capability.
 */
@Suppress("unused")
class NetworkMonitor(context: Context) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            _isOnline.value = isCurrentlyOnline()
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            _isOnline.value = isCurrentlyOnline()
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Log the failure so it isn't optimized away as unused
            Log.w(TAG, "Failed to register network callback", e)
            _isOnline.value = isCurrentlyOnline()
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun isCurrentlyOnline(): Boolean {
        try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check network capabilities", e)
            return false
        }
    }
}
