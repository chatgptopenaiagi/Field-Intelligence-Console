package com.fieldconsole.core.engine.solidworks

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
 * SolidWorksEngine — integrates with the SolidWorks PDM / 3DEXPERIENCE REST API.
 *
 * When a [FieldEntry] tag matches a known part number or assembly keyword,
 * this engine retrieves CAD metadata, BOM information, or 3D model references.
 *
 * Configuration:
 *  - Store a Bearer token (API key) under [ApiProvider.SOLIDWORKS].
 *  - Replace [BASE_URL] with your 3DEXPERIENCE tenant URL in the Settings screen
 *    (future enhancement: tenant URL as a separate settings field).
 *
 * Authentication: Bearer token via 3DEXPERIENCE Connected Services.
 */
class SolidWorksEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://your-tenant.3dexperience.3ds.com/enovia"
) : CoreEngine {

    override val providerName = "SolidWorks / 3DEXPERIENCE"
    override val isAvailable  = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun analyse(entry: FieldEntry): EngineResult = withContext(Dispatchers.IO) {
        try {
            val searchTerm = entry.tag.ifBlank { entry.note.take(50).ifBlank { "field_scan" } }
            val url = "$baseUrl/resources/v1/modeler/documents?\$searchStr=${
                java.net.URLEncoder.encode(searchTerm, "UTF-8")
            }"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext EngineResult.Error(
                    "SolidWorks API HTTP ${response.code}: ${response.message}"
                )
            }

            val json    = JSONObject(response.body!!.string())
            val members = json.optJSONArray("member")

            val summary = buildString {
                appendLine("**SolidWorks / 3DEXPERIENCE — Documents for tag: $searchTerm**")
                appendLine()
                if (members == null || members.length() == 0) {
                    appendLine("No documents found matching this tag.")
                    appendLine()
                    appendLine("Tip: Ensure the tag matches a part number or document title in your PDM vault.")
                } else {
                    for (i in 0 until minOf(members.length(), 8)) {
                        val doc   = members.getJSONObject(i)
                        val title = doc.optString("title", "Unnamed")
                        val type  = doc.optString("type", "")
                        val rev   = doc.optString("revision", "")
                        val state = doc.optString("state", "")
                        appendLine("• $title${if (rev.isNotBlank()) " [Rev $rev]" else ""}")
                        if (type.isNotBlank() || state.isNotBlank()) {
                            appendLine("  Type: $type  State: $state")
                        }
                    }
                }
            }
            EngineResult.Success(summary)
        } catch (e: Exception) {
            EngineResult.Error("SolidWorks request failed: ${e.message}", e)
        }
    }
}
