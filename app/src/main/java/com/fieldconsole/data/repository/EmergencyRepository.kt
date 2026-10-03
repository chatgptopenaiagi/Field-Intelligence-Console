package com.fieldconsole.data.repository

import com.fieldconsole.data.local.dao.EmergencyServiceDao
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.remote.emergency.OverpassApi
import com.fieldconsole.data.remote.emergency.OverpassElement
import com.fieldconsole.data.remote.emergency.OverpassFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class EmergencyRepository @Inject constructor(
    private val api: OverpassApi,
    private val dao: EmergencyServiceDao
) {
    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 1 hour
    }

    fun observeNearby(lat: Double, lon: Double): Flow<List<EmergencyService>> =
        dao.observeNearby(lat, lon)

    suspend fun refreshNearby(lat: Double, lon: Double): Result<List<EmergencyService>> =
        withContext(Dispatchers.IO) {
            try {
                // Check cache freshness
                val cached = dao.getNearby(lat, lon)
                if (cached.isNotEmpty() &&
                    System.currentTimeMillis() - (cached.firstOrNull()?.fetchedAt ?: 0L) < CACHE_TTL_MS) {
                    return@withContext Result.success(cached)
                }

                val response = api.query(OverpassApi.emergencyQuery(lat, lon))
                if (!response.remark.isNullOrBlank()) {
                    throw OverpassFailure("Emergency service query did not complete. Try again later.")
                }
                val services = (response.elements
                    ?: throw OverpassFailure("Emergency service response is missing results."))
                    .filter { it.type == "node" && it.lat != 0.0 }
                    .mapNotNull { el -> mapElement(el, lat, lon) }
                    .sortedBy { it.distanceM }

                dao.deleteForLocation(lat, lon)
                dao.insertAll(services)
                Result.success(services)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The UI can keep its cached rows, but a failed refresh is never an empty success.
                Result.failure(OverpassFailure.from(e))
            }
        }

    private fun mapElement(el: OverpassElement, scanLat: Double, scanLon: Double): EmergencyService? {
        val tags = el.tags
        val amenity = tags["amenity"] ?: tags["emergency"] ?: return null
        val type = when (amenity) {
            "hospital"       -> "HOSPITAL"
            "police"         -> "POLICE"
            "fire_station"   -> "FIRE"
            "pharmacy"       -> "PHARMACY"
            "defibrillator"  -> "AED"
            "shelter"        -> "SHELTER"
            "access_point"   -> "RAILWAY_ACCESS"
            else             -> return null
        }
        val name    = tags["name"] ?: tags["operator"] ?: type.lowercase().replaceFirstChar { it.uppercase() }
        val address = buildAddress(tags)
        val phone   = tags["phone"] ?: tags["contact:phone"] ?: ""
        val is24h   = tags["opening_hours"] == "24/7"
        val dist    = haversineM(scanLat, scanLon, el.lat, el.lon)
        val travelMin = (dist / 1000f / 40f * 60f).toInt().coerceAtLeast(1) // assume 40 km/h

        return EmergencyService(
            scanLat       = scanLat,
            scanLon       = scanLon,
            name          = name,
            type          = type,
            address       = address,
            phone         = phone,
            lat           = el.lat,
            lon           = el.lon,
            distanceM     = dist,
            travelTimeMin = travelMin,
            isOpen        = true, // Overpass doesn't return real-time open status
            is24h         = is24h,
            fetchedAt     = System.currentTimeMillis()
        )
    }

    private fun buildAddress(tags: Map<String, String>): String {
        val parts = listOfNotNull(
            tags["addr:street"]?.let { street ->
                tags["addr:housenumber"]?.let { "$street $it" } ?: street
            },
            tags["addr:city"],
            tags["addr:postcode"]
        )
        return parts.joinToString(", ").ifBlank { tags["addr:full"] ?: "" }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return (2 * r * atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }
}
