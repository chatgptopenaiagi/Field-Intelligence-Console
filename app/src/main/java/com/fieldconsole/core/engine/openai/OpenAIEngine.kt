package com.fieldconsole.core.engine.openai

import com.fieldconsole.core.engine.CoreEngine
import com.fieldconsole.core.engine.EngineResult
import com.fieldconsole.data.local.entity.FieldEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAIEngine — calls the OpenAI Chat Completions API to perform in-app
 * AI analysis of a [FieldEntry].
 *
 * Default model: gpt-4o. Can be overridden via the Settings screen.
 * Requires an API key stored under [ApiProvider.OPENAI].
 */
class OpenAIEngine(
    private val apiKey: String,
    private val model: String = "gpt-4o"
) : CoreEngine {

    override val providerName = "OpenAI / ChatGPT"
    override val isAvailable  = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    override suspend fun analyse(entry: FieldEntry): EngineResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", buildUserPrompt(entry))
                    })
                })
                put("max_tokens", 1024)
                put("temperature", 0.4)
            }.toString().toRequestBody(JSON_MEDIA)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("User-Agent", "FieldIntelligenceConsole/1.0")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext EngineResult.Error(
                    "OpenAI HTTP ${response.code}: ${response.message}"
                )
            }

            val json    = JSONObject(response.body!!.string())
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            EngineResult.Success(content)
        } catch (e: Exception) {
            EngineResult.Error("OpenAI request failed: ${e.message}", e)
        }
    }

    private fun buildUserPrompt(entry: FieldEntry) = """
        |Location : ${entry.latitude}, ${entry.longitude} (accuracy ±${entry.accuracy}m)
        |Observation : ${entry.note.ifBlank { "None provided" }}
        |Tag : ${entry.tag.ifBlank { "Untagged" }}
        |Timestamp : ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(entry.timestamp))}
        |
        |Please provide a structured field intelligence analysis covering:
        |1. Current function and context of this location
        |2. Historical background
        |3. Infrastructure and urban logic
        |4. Nearby logistics and services
        |5. Safety considerations
        |6. Research suggestions
        |
        |Clearly separate confirmed facts from hypotheses.
    """.trimMargin()

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a field intelligence analyst. Provide concise, structured, factual analysis " +
            "suitable for rapid field decision-making. Use plain text with numbered sections."
    }
}
