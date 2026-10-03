package com.fieldconsole.network

import com.google.gson.JsonObject
import kotlin.math.*

/** Queries saved associations only. No location collection or network requests. */
object NetworkGeography {
    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun confirmedPoint(association: JsonObject) = NetworkValidation.usablePoint(association)
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(listOf(lat1, lon1, lat2, lon2).all { it.isFinite() })
        require(lat1 in -90.0..90.0 && lat2 in -90.0..90.0 && lon1 in -180.0..180.0 && lon2 in -180.0..180.0)
        val p = Math.toRadians(lat2 - lat1); val q = Math.toRadians(lon2 - lon1)
        val a = (sin(p / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(q / 2).pow(2)).coerceIn(0.0, 1.0)
        return 6371000 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    fun query(entities: List<JsonObject>, scope: String, place: JsonObject, radiusM: Double = 1000.0): NetworkQuery {
        val explanation = "Saved geographic association is not current physical presence. Text matches do not establish administrative boundaries."
        if (entities.isEmpty()) return NetworkQuery("EMPTY_STORE", emptyList(), 0, explanation)
        return try {
            entities.forEach { NetworkCodec.validateEntity(it) }
            val keys = when (scope.lowercase()) {
                "building", "street" -> listOf("street", "locality", "country")
                "neighborhood" -> listOf("neighborhood", "locality", "country")
                "city" -> listOf("locality", "country")
                "region" -> listOf("region", "country")
                "country" -> listOf("country")
                "radius" -> emptyList()
                else -> return NetworkQuery("QUERY_FAILED", emptyList(), entities.size, "Unsupported geographic scope.")
            }
            if (keys.any { place.text(it).isBlank() }) return NetworkQuery("INSUFFICIENT_ADDRESS_DATA", emptyList(), entities.size, explanation)
            val radius = scope.lowercase() == "radius"
            val lat = place["latitude"]?.takeUnless { it.isJsonNull }?.asDouble
            val lon = place["longitude"]?.takeUnless { it.isJsonNull }?.asDouble
            if (radius && (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0))
                return NetworkQuery("LOCATION_UNAVAILABLE", emptyList(), entities.size, explanation)
            require(radiusM.isFinite() && radiusM >= 0)
            var supported = 0
            val matches = entities.filter { entity ->
                var usable = false
                val match = if (radius) entity.objects("associations").any { association ->
                    val point = association["point"]?.takeIf { it.isJsonObject }?.asJsonObject
                    val allowed = point != null && confirmedPoint(association) && point.text("precision") in setOf("building", "street")
                    if (allowed) usable = true
                    allowed && distance(lat!!, lon!!, point!!["latitude"].asDouble, point["longitude"].asDouble) <= radiusM + 1e-6
                } else entity.objects("addresses").any { address ->
                    val complete = keys.all { address.text(it).isNotBlank() }
                    val building = scope.lowercase() != "building" || entity.objects("associations").any { a ->
                        a.text("address_id") == address.text("id") && confirmedPoint(a) &&
                            a["point"]?.takeIf { it.isJsonObject }?.asJsonObject?.text("precision") == "building"
                    }
                    if (complete && building) usable = true
                    complete && building && keys.all { norm(address.text(it)) == norm(place.text(it)) }
                }
                if (usable) supported++
                match
            }.distinctBy { it.text("id") }
            NetworkQuery(if (matches.isNotEmpty()) "MATCHES_FOUND" else if (supported == 0) "INSUFFICIENT_ADDRESS_DATA" else "NO_MATCHES",
                matches, entities.size - supported, explanation + if (radius) " Distance is to a saved point, not a person's walking distance." else " Offline structured-address text matching.")
        } catch (_: Exception) { NetworkQuery("QUERY_FAILED", emptyList(), entities.size, "Invalid saved data or query. No presence conclusion is available.") }
    }
}
