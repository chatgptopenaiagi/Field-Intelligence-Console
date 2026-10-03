package com.fieldconsole.data.remote.emergency

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Overpass API — OpenStreetMap query API for nearby emergency services.
 * No API key required. Free to use.
 */
interface OverpassApi {

    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun query(
        @Field("data") query: String
    ): OverpassResponse

    companion object {
        const val BASE_URL = "https://overpass-api.de/"

        /**
         * Build an Overpass QL query for emergency services within [radiusM] metres.
         */
        fun emergencyQuery(lat: Double, lon: Double, radiusM: Int = 5000): String = """
            [out:json][timeout:25];
            (
              node["amenity"="hospital"](around:$radiusM,$lat,$lon);
              way["amenity"="hospital"](around:$radiusM,$lat,$lon);
              node["amenity"="police"](around:$radiusM,$lat,$lon);
              node["amenity"="fire_station"](around:$radiusM,$lat,$lon);
              node["amenity"="pharmacy"](around:$radiusM,$lat,$lon);
              node["emergency"="defibrillator"](around:$radiusM,$lat,$lon);
              node["amenity"="shelter"](around:$radiusM,$lat,$lon);
              node["emergency"="access_point"](around:$radiusM,$lat,$lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        /**
         * Build an Overpass QL query for logistics POIs.
         */
        fun logisticsQuery(lat: Double, lon: Double, radiusM: Int = 2000): String = """
            [out:json][timeout:25];
            (
              node["amenity"="restaurant"](around:$radiusM,$lat,$lon);
              node["amenity"="fast_food"](around:$radiusM,$lat,$lon);
              node["tourism"="hotel"](around:$radiusM,$lat,$lon);
              node["amenity"="fuel"](around:$radiusM,$lat,$lon);
              node["amenity"="parking"](around:$radiusM,$lat,$lon);
              node["amenity"="bus_station"](around:$radiusM,$lat,$lon);
              node["amenity"="atm"](around:$radiusM,$lat,$lon);
              node["amenity"="bank"](around:$radiusM,$lat,$lon);
              node["amenity"="pharmacy"](around:$radiusM,$lat,$lon);
              node["amenity"="supermarket"](around:$radiusM,$lat,$lon);
              node["shop"="supermarket"](around:$radiusM,$lat,$lon);
              node["amenity"="car_repair"](around:$radiusM,$lat,$lon);
              node["amenity"="toilets"](around:$radiusM,$lat,$lon);
              node["amenity"="drinking_water"](around:$radiusM,$lat,$lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()
    }
}

// ── Response models ───────────────────────────────────────────────────────────

data class OverpassResponse(
    val elements: List<OverpassElement>? = null,
    val remark: String? = null
)

data class OverpassElement(
    val type: String = "",
    val id:   Long   = 0L,
    val lat:  Double = 0.0,
    val lon:  Double = 0.0,
    val tags: Map<String, String> = emptyMap()
)
