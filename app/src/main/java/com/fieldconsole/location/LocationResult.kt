package com.fieldconsole.location

/**
 * LocationResult — sealed class representing all possible outcomes of a
 * location request from [LocationManager].
 */
sealed class LocationResult {

    /** A valid GPS fix was obtained. */
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) : LocationResult()

    /** The user denied location permission. */
    object PermissionDenied : LocationResult()

    /** Location services are disabled on the device. */
    object LocationDisabled : LocationResult()

    /** A timeout occurred before a fix was obtained. */
    object Timeout : LocationResult()

    /** An unexpected error occurred. */
    data class Error(val message: String) : LocationResult()
}
