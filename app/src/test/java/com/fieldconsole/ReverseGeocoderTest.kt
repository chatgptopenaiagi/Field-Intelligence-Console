package com.fieldconsole

import android.app.Application
import android.location.Address
import com.fieldconsole.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReverseGeocoderTest {
    @Test fun `missing callback times out preserving coordinates and cancels backend`() = runTest {
        var cancelled = false
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, _ -> { cancelled = true } })
        val result = resolver.resolve(48.2, 16.3, 7f, 123L)
        assertEquals(3_000L, currentTime)
        assertTrue(cancelled)
        assertEquals(48.2, result.latitude, 0.0)
        assertEquals(16.3, result.longitude, 0.0)
        assertEquals(7f, result.accuracy)
        assertEquals(123L, result.timestamp)
        assertNull(result.city)
        assertTrue(result.geocodingNotice!!.contains("timed out"))
        assertTrue(result.mapsLink.contains("48.2,16.3"))
    }

    @Test fun `late and duplicate callbacks cannot replace timeout or resume twice`() = runTest {
        lateinit var reply: (GeocodeReply) -> Unit
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, callback -> reply = callback; {} })
        val result = resolver.resolve(1.0, 2.0)
        repeat(2) { reply(GeocodeReply(Address(Locale.US).apply { locality = "Late" })) }
        assertNull(result.city)
        assertNotNull(result.geocodingNotice)
    }

    @Test fun `parent cancellation propagates and cancels lookup`() = runTest {
        var cancelled = false
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, _ -> { cancelled = true } })
        val lookup = async { resolver.resolve(1.0, 2.0) }
        runCurrent()
        lookup.cancel()
        try { lookup.await(); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertTrue(cancelled)
    }

    @Test fun `backend error is explicit and contains no invented address`() = runTest {
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, callback ->
            callback(GeocodeReply(failure = "Address lookup failed. Coordinates remain available.")); {}
        })
        val result = resolver.resolve(1.0, 2.0)
        assertNull(result.country)
        assertNull(result.street)
        assertTrue(result.geocodingNotice!!.contains("failed"))
    }

    @Test fun `empty result is distinct from a resolved address`() = runTest {
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, callback -> callback(GeocodeReply()); {} })
        val result = resolver.resolve(1.0, 2.0)
        assertNull(result.city)
        assertTrue(result.geocodingNotice!!.contains("No address"))
    }

    @Test fun `successful duplicate callbacks resolve only once with supplied address`() = runTest {
        val address = Address(Locale.US).apply {
            locality = "Vienna"; countryCode = "AT"; thoroughfare = "Street"; subThoroughfare = "12"
        }
        val resolver = ReverseGeocoder(GeocodeBackend { _, _, callback ->
            callback(GeocodeReply(address)); callback(GeocodeReply()); {}
        })
        val result = resolver.resolve(48.2, 16.3)
        assertEquals("Vienna", result.city)
        assertEquals("12 Street", result.street)
        assertEquals("AT", result.countryCode)
        assertNull(result.geocodingNotice)
    }
}
