package io.github.christianjann.gittasks.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper class for network-related operations, primarily for WiFi-only sync feature.
 */
class NetworkHelper(private val context: Context) {

    companion object {
        private const val TAG = "NetworkHelper"
    }

    /**
     * Check if the device is currently connected to WiFi.
     * Does not require any special permissions beyond ACCESS_NETWORK_STATE.
     */
    fun isOnWifi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Check if the device has any network connection (WiFi or mobile data).
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Check if the app has location permission (required for getting WiFi SSID on Android 8.0+).
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the current WiFi SSID (network name).
     * 
     * IMPORTANT: On Android 8.0+, this requires:
     * 1. ACCESS_FINE_LOCATION permission granted
     * 2. Location services enabled on the device
     * 
     * Returns null if:
     * - Not connected to WiFi
     * - Permission not granted
     * - Location services disabled
     * - SSID is unknown/hidden
     */
    @Suppress("DEPRECATION")
    fun getCurrentWifiSsid(): String? {
        if (!isOnWifi()) {
            Log.d(TAG, "getCurrentWifiSsid: Not on WiFi")
            return null
        }

        // On Android 8.0+, location permission is required to get SSID
        if (!hasLocationPermission()) {
            Log.d(TAG, "getCurrentWifiSsid: Location permission not granted")
            return null
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        return try {
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo?.ssid

            // SSID comes wrapped in quotes, remove them
            // Also check for <unknown ssid> which is returned when location is off
            when {
                ssid == null -> null
                ssid == "<unknown ssid>" -> {
                    Log.d(TAG, "getCurrentWifiSsid: Unknown SSID (location services may be disabled)")
                    null
                }
                ssid.startsWith("\"") && ssid.endsWith("\"") -> ssid.substring(1, ssid.length - 1)
                else -> ssid
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "getCurrentWifiSsid: Security exception", e)
            null
        }
    }

    /**
     * Get a list of configured WiFi networks that the device has saved.
     * This can be used to let users pick from networks they've connected to before.
     * 
     * Note: This API is deprecated in Android 10+ and may return empty list.
     * For newer Android versions, we fall back to just using the current SSID.
     */
    @Suppress("DEPRECATION")
    fun getConfiguredWifiNetworks(): List<String> {
        if (!hasLocationPermission()) {
            return emptyList()
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()

        return try {
            // Note: This is deprecated in Android 10 and returns empty list
            wifiManager.configuredNetworks
                ?.mapNotNull { config ->
                    config.SSID?.let { ssid ->
                        // Remove quotes from SSID
                        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid.substring(1, ssid.length - 1)
                        } else {
                            ssid
                        }
                    }
                }
                ?.filter { it.isNotEmpty() && it != "<unknown ssid>" }
                ?.distinct()
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "getConfiguredWifiNetworks: Security exception", e)
            emptyList()
        }
    }

    /**
     * Check if sync should be allowed based on WiFi settings.
     * 
     * @param syncOnlyOnWifi If true, sync only when connected to WiFi
     * @param syncOnSpecificWifi If true, also check for specific SSID
     * @param requiredSsid The specific SSID to sync on (only used if syncOnSpecificWifi is true)
     * @return true if sync is allowed, false otherwise
     */
    fun isSyncAllowed(
        syncOnlyOnWifi: Boolean,
        syncOnSpecificWifi: Boolean,
        requiredSsid: String
    ): Boolean {
        // If WiFi-only sync is disabled, always allow
        if (!syncOnlyOnWifi) {
            return true
        }

        // Check if on WiFi
        if (!isOnWifi()) {
            Log.d(TAG, "isSyncAllowed: Not on WiFi, sync not allowed")
            return false
        }

        // If specific WiFi is required, check SSID
        if (syncOnSpecificWifi && requiredSsid.isNotEmpty()) {
            val currentSsid = getCurrentWifiSsid()
            val allowed = currentSsid == requiredSsid
            Log.d(TAG, "isSyncAllowed: Current SSID='$currentSsid', Required='$requiredSsid', Allowed=$allowed")
            return allowed
        }

        // On WiFi and no specific network required
        Log.d(TAG, "isSyncAllowed: On WiFi, sync allowed")
        return true
    }
}
