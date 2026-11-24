package com.fieldconsole.location

/**
 * GeoContext — human-readable place context derived from reverse geocoding.
 *
 * All fields are optional; the app gracefully falls back to raw coordinates
 * whenever geocoding is unavailable or returns empty results.
 */
data class GeoContext(
    val street:      String? = null,
    val district:    String? = null,
    val city:        String? = null,
    val country:     String? = null,   // human-readable country name
    val countryCode: String? = null,   // ISO 3166-1 alpha-2 (e.g. "AT", "DE", "US")
    val latitude:    Double  = Double.NaN,
    val longitude:   Double  = Double.NaN,
    val accuracy:    Float   = 0f,
    val timestamp:   Long    = System.currentTimeMillis(),
    val geocodingNotice: String? = null,
    val region: String? = null,
    val provider: String? = null,
    val saved: Boolean = false
) {
    val manual get() = provider == "manual"
    val validCoordinates get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
    val accuracyText get() = if (accuracy.isFinite() && accuracy > 0) "±$accuracy m" else "UNKNOWN"
    val measuredAt get() = if (!manual && timestamp > 0) java.time.Instant.ofEpochMilli(timestamp).toString() else "UNKNOWN"

    /** Short display label: "Street, City" or fallback to coordinates. */
    val shortLabel: String get() {
        val parts = listOfNotNull(street, city).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) parts.joinToString(", ")
        else "%.5f, %.5f".format(java.util.Locale.ROOT, latitude, longitude)
    }

    /** Full place name: "Street, District, City, Country" */
    val fullPlaceName: String get() {
        val parts = listOfNotNull(street, district, city, country).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) parts.joinToString(", ")
        else "%.6f, %.6f".format(java.util.Locale.ROOT, latitude, longitude)
    }

    /** Coordinates as a clean string. */
    val coordString: String get() = "%.6f, %.6f".format(java.util.Locale.ROOT, latitude, longitude)

    /** Google Maps link for this location. */
    val mapsLink: String get() = "https://maps.google.com/?q=$latitude,$longitude"

    /** True if we have at least a city name. */
    val hasPlaceName: Boolean get() = !city.isNullOrBlank()
}
