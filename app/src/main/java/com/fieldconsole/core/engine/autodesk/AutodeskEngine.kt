package com.fieldconsole.core.engine.autodesk

import com.fieldconsole.core.engine.CoreEngine
import com.fieldconsole.core.engine.EngineResult
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AutodeskEngine — integrates with the Autodesk Platform Services (APS) API,
 * formerly known as Forge.
 *
 * Authentication: 2-legged OAuth2 (client_credentials grant).
 * Store the credentials as a single colon-separated string in the Keystore:
 *   "clientId:clientSecret"
 *
 * On first call the engine obtains an access token and uses it to query the
 * Data Management API for BIM hub context relevant to the scanned location.
 */
class AutodeskEngine(private val apiKey: String) : CoreEngine {

    override val providerName = "Autodesk APS / Forge"
    override val isAvailable  = apiKey.isNotBlank() && apiKey.contains(":")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val APS_AUTH_URL  = "https://developer.api.autodesk.com/authentication/v2/token"
    private val APS_HUBS_URL  = "https://developer.api.autodesk.com/project/v1/hubs"

    // ── Token management ──────────────────────────────────────────────────────

    private var cachedToken: String? = null
    private var tokenExpiry: Long    = 0L

    private suspend fun getAccessToken(clientId: String, clientSecret: String): String? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (cachedToken != null && now < tokenExpiry - 60_000L) return@withContext cachedToken

            try {
                val body = FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("scope", "data:read")
                    .build()
                val request = Request.Builder()
                    .url(APS_AUTH_URL)
                    .addHeader("Authorization", Credentials.basic(clientId, clientSecret))
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body!!.string())
                cachedToken  = json.optString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                tokenExpiry  = now + expiresIn * 1000L
                cachedToken
            } catch (e: Exception) {
                null
            }
        }

    // ── CoreEngine ────────────────────────────────────────────────────────────

    override suspend fun analyse(entry: FieldEntry): EngineResult = withContext(Dispatchers.IO) {
        try {
            val parts = apiKey.split(":")
            if (parts.size < 2) return@withContext EngineResult.Error(
                "Autodesk key must be formatted as clientId:clientSecret"
            )

            val token = getAccessToken(parts[0], parts[1])
                ?: return@withContext EngineResult.Error("Autodesk authentication failed.")

            val request = Request.Builder()
                .url(APS_HUBS_URL)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext EngineResult.Error(
                    "APS API HTTP ${response.code}: ${response.message}"
                )
            }

            val json = JSONObject(response.body!!.string())
            val hubs = json.optJSONArray("data")

            val summary = buildString {
                appendLine("**Autodesk APS — BIM Hub Context**")
                appendLine("Location: ${entry.latitude}, ${entry.longitude}")
                appendLine()
                if (hubs == null || hubs.length() == 0) {
                    appendLine("No BIM hubs found for this Autodesk account.")
                } else {
                    appendLine("Connected hubs:")
                    for (i in 0 until hubs.length()) {
                        val hub    = hubs.getJSONObject(i)
                        val attrs  = hub.optJSONObject("attributes")
                        val name   = attrs?.optString("name", "Unnamed") ?: "Unnamed"
                        val region = attrs?.optString("region", "") ?: ""
                        val type   = hub.optString("type", "")
                        appendLine("• $name${if (region.isNotBlank()) " [$region]" else ""} — $type")
                    }
                }
                appendLine()
                appendLine("Tag: ${entry.tag.ifBlank { "None" }}")
                appendLine("Observation: ${entry.note.ifBlank { "None" }}")
            }
            EngineResult.Success(summary)
        } catch (e: Exception) {
            EngineResult.Error("Autodesk request failed: ${e.message}", e)
        }
    }
}
