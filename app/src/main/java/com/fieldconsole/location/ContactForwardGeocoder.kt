package com.fieldconsole.location

import com.fieldconsole.BuildConfig
import com.fieldconsole.network.NetworkCodec
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AddressCandidate(val formatted: String, val latitude: Double, val longitude: Double,
                            val precision: String, val resolvedAt: String)

/** Opt-in only. Callers must preview the exact address and provider before each lookup.
 * No names, phone numbers, notes or persistent keys are accepted by this adapter.
 */
class ContactForwardGeocoder internal constructor(
    private val endpoint: HttpUrl = "https://api.opencagedata.com/geocode/v1/json".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
) {
    private val mutex = Mutex()
    private var nextRequestNanos = 0L
    private val cache = LinkedHashMap<String, Pair<Long, List<AddressCandidate>>>()

    suspend fun lookup(address: String, key: String, consent: Boolean): List<AddressCandidate> {
        require(consent) { "Explicit address-sharing consent required." }
        require(key.isNotBlank()) { "OpenCage is not configured. Offline matching remains available." }
        require(address.length in 2..2048) { "A bounded address is required." }
        try {
            return withTimeout(30_000) { mutex.withLock {
                val cacheKey = MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
                cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first in 0..86_400_000 }?.let { return@withLock it.second }
                for (attempt in 0..1) {
                    val remaining = (nextRequestNanos - System.nanoTime()) / 1_000_000
                    if (remaining > 0) delay(remaining + 1)
                    nextRequestNanos = System.nanoTime() + 1_100_000_000
                    val url = endpoint.newBuilder().addQueryParameter("q", address).addQueryParameter("key", key)
                        .addQueryParameter("limit", "5").addQueryParameter("no_annotations", "1").addQueryParameter("no_record", "1").build()
                    val response = request(Request.Builder().url(url).header("User-Agent", "FieldIntelligenceConsole/${BuildConfig.VERSION_NAME} (Android)").build())
                    if (response.code == 429 || response.code in 500..599) {
                        val waitMillis = retryMillis(response.retryAfter)
                        if (attempt == 0 && waitMillis <= 15_000) { delay(waitMillis); continue }
                        throw IOException("Address provider is unavailable or rate limited. Try later.")
                    }
                    if (response.code != 200) throw IOException("Address lookup failed (HTTP ${response.code}). Check provider access.")
                    val parsed = NetworkCodec.parseJson(response.body.toString(Charsets.UTF_8))
                    require(parsed.getAsJsonObject("status")?.get("code")?.asInt == 200)
                    val rows = parsed.getAsJsonArray("results") ?: throw IOException("Incomplete address response.")
                    val resolvedAt = Instant.now().toString()
                    val candidates = rows.take(5).mapNotNull { row ->
                        val item = row.asJsonObject
                        val geometry = item.getAsJsonObject("geometry") ?: return@mapNotNull null
                        val lat = geometry.get("lat")?.asDouble ?: return@mapNotNull null
                        val lon = geometry.get("lng")?.asDouble ?: return@mapNotNull null
                        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                        val precision = when (item.getAsJsonObject("components")?.get("_type")?.asString) {
                            "building", "house" -> "building"
                            "road", "street" -> "street"
                            "city", "town", "village", "hamlet" -> "locality"
                            "state", "county", "region" -> "region"
                            "country" -> "country"
                            else -> "approximate"
                        }
                        AddressCandidate(item.get("formatted")?.asString.orEmpty(), lat, lon, precision, resolvedAt)
                    }
                    if (cache.size >= 32) cache.remove(cache.keys.first())
                    cache[cacheKey] = System.currentTimeMillis() to candidates
                    return@withLock candidates
                }
                emptyList()
            } }
        } catch (_: TimeoutCancellationException) { throw IOException("Address lookup timed out. Saved address remains unresolved.") }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw IOException("Address lookup failed or is unavailable. Saved address remains unresolved.") }
    }

    private data class HttpResult(val code: Int, val body: ByteArray, val retryAfter: String?)
    private suspend fun request(request: Request): HttpResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Address connection failed."))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bytes = ByteArrayOutputStream()
                        response.body?.byteStream()?.use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = stream.read(buffer); if (n < 0) break
                                require(bytes.size() + n <= 262_144); bytes.write(buffer, 0, n)
                            }
                        }
                        if (continuation.isActive) continuation.resume(HttpResult(response.code, bytes.toByteArray(), response.header("Retry-After")))
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Address response unavailable."))
                    }
                }
            }
        })
    }
    private fun retryMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 2_000
        value.toLongOrNull()?.let { return if (it in 0..15) maxOf(1_100, it * 1000) else 60_000 }
        return runCatching { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_100) }.getOrDefault(60_000)
    }
    companion object { val shared = ContactForwardGeocoder() }
}
