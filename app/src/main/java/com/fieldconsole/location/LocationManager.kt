package com.fieldconsole.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager as AndroidLocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationManager — wraps Google Play Services [FusedLocationProviderClient]
 * to provide coroutine-friendly GPS access.
 *
 * Responsibilities:
 *  - Check location permissions before any request.
 *  - Check that the device location services are enabled.
 *  - Expose a one-shot [getCurrentLocation] suspend function.
 *  - Expose a continuous [locationUpdates] Flow for live tracking.
 */
@Singleton
class LocationManager @Inject constructor(
    private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /** Returns true if both FINE and COARSE location permissions are granted. */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Returns true if the device location services (GPS or network) are enabled. */
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        return lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
    }

    /**
     * Obtain a single GPS fix.
     *
     * @param timeoutMs Maximum milliseconds to wait for a fix (default 10 s).
     * @return [LocationResult] — Success, PermissionDenied, LocationDisabled, or Timeout.
     */
    suspend fun getCurrentLocation(timeoutMs: Long = 10_000L): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied
        if (!isLocationEnabled())    return LocationResult.LocationDisabled

        val result = withTimeoutOrNull(timeoutMs) {
            val playAvailable = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
            if (!playAvailable) return@withTimeoutOrNull platformFix()
            try {
                locationUpdates(
                interval    = 1_000L,
                fastestInterval = 500L,
                priority    = Priority.PRIORITY_HIGH_ACCURACY,
                maxUpdates  = 1
                ).first()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                platformFix()
            }
        }
        return result ?: LocationResult.Timeout
    }

    @SuppressLint("MissingPermission")
    private suspend fun platformFix(): LocationResult {
        val manager = context.getSystemService(AndroidLocationManager::class.java)
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = listOfNotNull(AndroidLocationManager.GPS_PROVIDER.takeIf { precise }, AndroidLocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } ?: return LocationResult.LocationDisabled
        return callbackFlow<LocationResult> {
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    trySend(LocationResult.Success(loc.latitude, loc.longitude,
                        if (loc.hasAccuracy()) loc.accuracy else 0f, loc.time, loc.provider))
                }
                override fun onProviderDisabled(name: String) { trySend(LocationResult.LocationDisabled) }
                override fun onProviderEnabled(name: String) = Unit
                @Deprecated("Legacy callback")
                override fun onStatusChanged(name: String?, status: Int, extras: android.os.Bundle?) = Unit
            }
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                trySend(LocationResult.PermissionDenied)
            } catch (_: IllegalArgumentException) {
                trySend(LocationResult.LocationDisabled)
            }
            awaitClose { manager.removeUpdates(listener) }
        }.first()
    }

    /**
     * Continuous location update [Flow].
     *
     * Emits [LocationResult.Success] for every new fix.
     * The flow is cold — it starts the provider when collected and stops it on cancellation.
     *
     * @param interval          Desired update interval in milliseconds.
     * @param fastestInterval   Fastest acceptable update interval.
     * @param priority          [Priority] constant from Play Services.
     * @param maxUpdates        Maximum number of updates (Int.MAX_VALUE = unlimited).
     */
    @SuppressLint("MissingPermission") // Permission checked at the top of the flow before any API call
    fun locationUpdates(
        interval: Long = 5_000L,
        fastestInterval: Long = 2_000L,
        priority: Int = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        maxUpdates: Int = Int.MAX_VALUE
    ): Flow<LocationResult> = callbackFlow {

        if (!hasLocationPermission()) {
            trySend(LocationResult.PermissionDenied)
            close()
            return@callbackFlow
        }
        if (!isLocationEnabled()) {
            trySend(LocationResult.LocationDisabled)
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(fastestInterval)
            .setMaxUpdates(maxUpdates)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        LocationResult.Success(
                            latitude  = loc.latitude,
                            longitude = loc.longitude,
                            accuracy  = if (loc.hasAccuracy()) loc.accuracy else 0f,
                            timestamp = loc.time, provider = loc.provider
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener { close(it) }

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
