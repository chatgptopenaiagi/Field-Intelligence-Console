package com.fieldconsole

import com.fieldconsole.location.ContactForwardGeocoder
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class ContactForwardGeocoderTest {
    private val server = MockWebServer().apply { start() }
    private val geocoder = ContactForwardGeocoder(server.url("/geocode/v1/json"))
    @After fun close() { server.shutdown() }
    @Test fun `no consent or key means no outbound request`() = runBlocking {
        for ((consent, key) in listOf(false to "synthetic-test", true to "")) {
            try { geocoder.lookup("Synthetic Street", key, consent); fail("Must reject") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, server.requestCount)
    }
    @Test fun `wire request contains only approved address and results remain candidates with precision`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":{"code":200},"results":[
          {"formatted":"Synthetic city","geometry":{"lat":0,"lng":179.9},"components":{"_type":"city"},"confidence":10},
          {"formatted":"Synthetic street","geometry":{"lat":1,"lng":2},"components":{"_type":"road"}}
        ]}"""))
        val results = geocoder.lookup("Synthetic Street", "synthetic-test", true)
        assertEquals(2, results.size); assertEquals("locality", results[0].precision)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Synthetic Street", request.requestUrl!!.queryParameter("q"))
        assertEquals("1", request.requestUrl!!.queryParameter("no_record"))
        assertEquals(setOf("q", "key", "limit", "no_annotations", "no_record"), request.requestUrl!!.queryParameterNames)
        assertEquals(results, geocoder.lookup("Synthetic Street", "synthetic-test", true))
        assertEquals(1, server.requestCount)
    }
    @Test fun `valid zero results differs from malformed success`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":{"code":200},"results":[]}"""))
        assertTrue(geocoder.lookup("Synthetic empty", "synthetic-test", true).isEmpty())
        server.enqueue(MockResponse().setBody("""{"status":{"code":200}}"""))
        try { geocoder.lookup("Synthetic malformed", "synthetic-test", true); fail("Incomplete response must fail") }
        catch (e: IOException) { assertFalse(e.message.orEmpty().contains("synthetic-test")) }
    }
    @Test fun `long RetryAfter fails instead of retrying early`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        try { geocoder.lookup("Synthetic limited", "synthetic-test", true); fail("Rate limit must fail") }
        catch (_: IOException) { }
        assertEquals(1, server.requestCount)
    }
    @Test fun `transient failure has one bounded retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"status":{"code":200},"results":[]}"""))
        assertTrue(geocoder.lookup("Synthetic retry", "synthetic-test", true).isEmpty())
        assertEquals(2, server.requestCount)
    }
    @Test fun `cancellation interrupts request and propagates`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val lookup = async(Dispatchers.Default) { geocoder.lookup("Synthetic cancelled", "synthetic-test", true) }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        lookup.cancel()
        try { lookup.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertTrue(lookup.isCancelled)
    }
}
