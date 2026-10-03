package com.fieldconsole.data.repository

import com.fieldconsole.data.local.dao.EmergencyServiceDao
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.remote.emergency.OverpassApi
import com.fieldconsole.data.remote.emergency.OverpassElement
import com.fieldconsole.data.remote.emergency.OverpassFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class EmergencyRepository @Inject constructor(
    private val api: OverpassApi,
    private val dao: EmergencyServiceDao
) {
    private val refreshMutex = Mutex()
    // Bounded, session-only negative cache; no persistent schema change.
    private var lastEmptyLookup: Triple<Double, Double, Long>? = null

    companion object {
        private const val RADIUS_M = 5000
        private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 1 hour
    }

    fun observeNearby(lat: Double, lon: Double): Flow<List<EmergencyService>> =
        dao.observeNearby(lat, lon).map { validCachedServices(it, lat, lon) }

    // Older caches may contain out-of-radius area centers and heuristic status/ETA.
    private fun validCachedServices(rows: List<EmergencyService>, lat: Double, lon: Double) =
        rows.mapNotNull { row ->
            if (!row.lat.isFinite() || !row.lon.isFinite() || row.lat !in -90.0..90.0 ||
                row.lon !in -180.0..180.0) return@mapNotNull null
            val distance = haversineM(lat, lon, row.lat, row.lon)
            if (!distance.isFinite() || distance > RADIUS_M) null
            else row.copy(distanceM = distance, travelTimeMin = 0, isOpen = false)
        }.sortedBy { it.distanceM }

    suspend fun refreshNearby(lat: Double, lon: Double): Result<List<EmergencyService>> =
        withContext(Dispatchers.IO) {
          refreshMutex.withLock {
            try {
                require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    "Invalid scan coordinates."
                }
                // Check cache freshness
                val cached = dao.getNearby(lat, lon)
                val now = System.currentTimeMillis()
                if (cached.isNotEmpty() && cached.all { now - it.fetchedAt in 0 until CACHE_TTL_MS }) {
                    return@withContext Result.success(validCachedServices(cached, lat, lon))
                }
                val empty = lastEmptyLookup
                if (cached.isEmpty() && empty != null && empty.first == lat && empty.second == lon &&
                    now - empty.third in 0 until CACHE_TTL_MS) {
                    return@withContext Result.success(emptyList())
                }

                val response = api.query(OverpassApi.emergencyQuery(lat, lon, RADIUS_M))
                if (!response.remark.isNullOrBlank()) {
                    throw OverpassFailure("Emergency service query did not complete. Try again later.")
                }
                val services = (response.elements
                    ?: throw OverpassFailure("Emergency service response is missing results."))
                    .mapNotNull { el -> mapElement(el, lat, lon) }
                    .sortedBy { it.distanceM }

                dao.replaceForLocation(lat, lon, services)
                lastEmptyLookup = if (services.isEmpty()) Triple(lat, lon, System.currentTimeMillis()) else null
                Result.success(services)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The UI can keep its cached rows, but a failed refresh is never an empty success.
                Result.failure(OverpassFailure.from(e))
            }
          }
        }

    private fun mapElement(el: OverpassElement, scanLat: Double, scanLon: Double): EmergencyService? {
        // Area centers are approximate locations, not verified entrances or routing points.
        val lat = when (el.type) {
            "node" -> el.lat
            "way", "relation" -> el.center?.lat
            else -> null
        } ?: return null
        val lon = when (el.type) {
            "node" -> el.lon
            "way", "relation" -> el.center?.lon
            else -> null
        } ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
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
        val dist    = haversineM(scanLat, scanLon, lat, lon)
        if (!dist.isFinite() || dist > RADIUS_M) return null

        return EmergencyService(
            scanLat       = scanLat,
            scanLon       = scanLon,
            name          = name,
            type          = type,
            address       = address,
            phone         = phone,
            lat           = lat,
            lon           = lon,
            distanceM     = dist,
            travelTimeMin = 0, // Unknown; never presented as an ETA
            isOpen        = false, // No verified open-now evidence; UI presents status as unknown
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
        return (2 * r * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt(1 - a.coerceIn(0.0, 1.0)))).toFloat()
    }
}
