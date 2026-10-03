package com.fieldconsole.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ReverseGeocoder — converts latitude/longitude into a [GeoContext] using
 * Android's built-in [Geocoder].
 *
 * On API 33+ the async listener API is used. On older APIs the synchronous
 * call is dispatched on [Dispatchers.IO]. If geocoding fails for any reason
 * (no network, no backend service, etc.) a coordinate-only [GeoContext] is
 * returned — the app never crashes or shows empty data.
 */
@Singleton
class ReverseGeocoder @Inject constructor(private val context: Context) {

    suspend fun resolve(
        latitude: Double,
        longitude: Double,
        accuracy: Float = 0f,
        timestamp: Long = System.currentTimeMillis()
    ): GeoContext {
        val base = GeoContext(
            latitude  = latitude,
            longitude = longitude,
            accuracy  = accuracy,
            timestamp = timestamp
        )

        if (!Geocoder.isPresent()) return base

        return try {
            val address = fetchAddress(latitude, longitude) ?: return base
            GeoContext(
                street       = buildStreet(address),
                district     = address.subLocality
                                  ?: address.locality
                                  ?: address.subAdminArea,
                city         = address.locality
                                  ?: address.subAdminArea
                                  ?: address.adminArea,
                country      = address.countryName,
                countryCode  = address.countryCode,  // ISO alpha-2
                latitude     = latitude,
                longitude    = longitude,
                accuracy     = accuracy,
                timestamp    = timestamp
            )
        } catch (e: Exception) {
            base
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun fetchAddress(
        lat: Double,
        lon: Double
    ): android.location.Address? {
        val geocoder = Geocoder(context, Locale.getDefault())

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ async listener
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1,
                    Geocoder.GeocodeListener { addresses ->
                        cont.resume(addresses.firstOrNull())
                    }
                )
            }
        } else {
            // Older API — blocking call on IO dispatcher
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
        }
    }

    private fun buildStreet(address: android.location.Address): String? {
        val number = address.subThoroughfare
        val street = address.thoroughfare
        return when {
            number != null && street != null -> "$number $street"
            street != null                  -> street
            else                            -> null
        }
    }
}
