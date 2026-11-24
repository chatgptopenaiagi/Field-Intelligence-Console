package com.fieldconsole.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

internal fun interface GeocodeBackend {
    fun request(lat: Double, lon: Double, reply: (GeocodeReply) -> Unit): () -> Unit
}

internal data class GeocodeReply(val address: Address? = null, val failure: String? = null)

@Singleton
class ReverseGeocoder internal constructor(
    private val backend: GeocodeBackend,
    private val timeoutMillis: Long = 3_000
) {
    @Inject constructor(context: Context) : this(AndroidGeocodeBackend(context.applicationContext))

    suspend fun resolve(latitude: Double, longitude: Double, accuracy: Float = 0f,
                        timestamp: Long = System.currentTimeMillis()): GeoContext {
        val base = GeoContext(latitude = latitude, longitude = longitude,
            accuracy = accuracy, timestamp = timestamp)
        val result = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<GeocodeReply> { continuation ->
                val finished = AtomicBoolean(false)
                val cancel = backend.request(latitude, longitude) { reply ->
                    if (finished.compareAndSet(false, true)) continuation.resume(reply)
                }
                continuation.invokeOnCancellation {
                    finished.set(true)
                    cancel()
                }
            }
        } ?: return base.copy(geocodingNotice = "Address lookup timed out. Coordinates remain available.")
        result.failure?.let { return base.copy(geocodingNotice = it) }
        val address = result.address ?: return base.copy(
            geocodingNotice = "No address found. Coordinates remain available.")
        return base.copy(
            street = listOfNotNull(address.subThoroughfare, address.thoroughfare)
                .joinToString(" ").takeIf { it.isNotBlank() },
            district = address.subLocality ?: address.locality ?: address.subAdminArea,
            city = address.locality, region = address.adminArea,
            country = address.countryName, countryCode = address.countryCode)
    }
}

/** Keeps even an uninterruptible legacy Binder call off the caller. Interrupting
 * that call is best-effort; a stuck worker cannot create unlimited threads.
 */
private class AndroidGeocodeBackend(private val context: Context) : GeocodeBackend {
    override fun request(lat: Double, lon: Double, reply: (GeocodeReply) -> Unit): () -> Unit {
        return try {
            val future = worker.submit {
                try {
                    if (!Geocoder.isPresent()) {
                        reply(GeocodeReply(failure = "Address service unavailable. Coordinates remain available."))
                    } else {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        if (Build.VERSION.SDK_INT >= 33) {
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    reply(GeocodeReply(addresses.firstOrNull()))
                                }
                                override fun onError(errorMessage: String?) { reply(failed()) }
                            })
                        } else {
                            @Suppress("DEPRECATION")
                            val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                            reply(GeocodeReply(address))
                        }
                    }
                } catch (_: Exception) { reply(failed()) }
            }
            val cancel: () -> Unit = { future.cancel(true); Unit }
            cancel
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            reply(failed())
            val cancel: () -> Unit = {}
            cancel
        }
    }

    private fun failed() = GeocodeReply(failure = "Address lookup failed. Coordinates remain available.")

    companion object {
        private val worker = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            SynchronousQueue(), { task -> Thread(task, "reverse-geocoder").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy())
    }
}
