package com.fieldconsole.core.engine.google

import com.fieldconsole.core.engine.CoreEngine
import com.fieldconsole.core.engine.EngineResult
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GoogleEngine — uses the Google Places API (Nearby Search) to enrich a
 * [FieldEntry] with real-time place data within a 500 m radius.
 *
 * Requires a Maps/Places API key with the Places API enabled in Google Cloud
 * Console. Store the key under [ApiProvider.GOOGLE_MAPS].
 *
 * Google Earth Engine integration is prepared as a stub — full EE REST API
 * calls require OAuth2 service-account tokens which are handled separately.
 */
class GoogleEngine(private val apiKey: String) : CoreEngine {

    override val providerName = "Google Maps / Places"
    override val isAvailable  = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun analyse(entry: FieldEntry): EngineResult = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
                append("?location=${entry.latitude},${entry.longitude}")
                append("&radius=500")
                append("&key=$apiKey")
            }

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext EngineResult.Error(
                    "Google Places HTTP ${response.code}: ${response.message}"
                )
            }

            val json    = JSONObject(response.body!!.string())
            val status  = json.optString("status")
            if (status != "OK" && status != "ZERO_RESULTS") {
                return@withContext EngineResult.Error(
                    "Google Places API error: $status — ${json.optString("error_message")}"
                )
            }

            val results = json.optJSONArray("results")
            val summary = buildString {
                appendLine("**Nearby Places — Google Places API**")
                appendLine("Radius: 500 m · Centre: ${entry.latitude}, ${entry.longitude}")
                appendLine()
                if (results == null || results.length() == 0) {
                    appendLine("No places found in this area.")
                } else {
                    for (i in 0 until minOf(results.length(), 12)) {
                        val place  = results.getJSONObject(i)
                        val name   = place.optString("name", "Unknown")
                        val types  = place.optJSONArray("types")?.let { arr ->
                            (0 until arr.length()).joinToString(", ") { arr.getString(it) }
                        } ?: ""
                        val rating = place.optDouble("rating", -1.0)
                        val open   = place.optJSONObject("opening_hours")
                            ?.optBoolean("open_now")
                        val openStr = when (open) {
                            true  -> " [Open]"
                            false -> " [Closed]"
                            null  -> ""
                        }
                        val ratingStr = if (rating > 0) " ★${"%.1f".format(rating)}" else ""
                        appendLine("• $name$ratingStr$openStr")
                        if (types.isNotBlank()) appendLine("  $types")
                    }
                }
            }
            EngineResult.Success(summary)
        } catch (e: Exception) {
            EngineResult.Error("Google Places request failed: ${e.message}", e)
        }
    }
}
