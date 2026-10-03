package com.fieldconsole

import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.remote.emergency.*
import com.fieldconsole.data.repository.EmergencyRepository
import com.fieldconsole.di.AppModule
import com.google.gson.Gson
import com.fieldconsole.viewmodel.EmergencyUiData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class OverpassContractTest {
    private val server = MockWebServer()
    private val dao = FakeEmergencyServiceDao()
    private lateinit var repository: EmergencyRepository
    private lateinit var api: OverpassApi

    @Before fun start() {
        server.start()
        api = AppModule.provideOverpassRetrofit(Gson()).newBuilder()
            .baseUrl(server.url("/")).build()
            .create(OverpassApi::class.java)
        repository = EmergencyRepository(api, dao)
    }

    @Test fun `every request through production provider is identified`() = runBlocking {
        for (query in listOf(OverpassApi.emergencyQuery(48.2, 16.3), OverpassApi.logisticsQuery(48.2, 16.3))) {
            server.enqueue(MockResponse().setBody("""{"elements":[]}"""))
            api.query(query)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals(listOf("FieldIntelligenceConsole/${BuildConfig.VERSION_NAME} (Android)"),
                request.headers.values("User-Agent"))
        }
    }
    @After fun stop() { server.shutdown() }

    @Test fun `central client identifies requests and preserves form encoded query`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"elements":[]}"""))
        assertTrue(repository.refreshNearby(48.2, 16.3).isSuccess)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("FieldIntelligenceConsole/3.0.0 (Android)", request.getHeader("User-Agent"))
        assertEquals("POST", request.method)
        assertEquals("/api/interpreter", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals("data=" + OverpassApi.emergencyQuery(48.2, 16.3),
            URLDecoder.decode(request.body.readUtf8(), "UTF-8"))
    }

    @Test fun `HTTP failures are mapped without leaking response body`() = runBlocking {
        for (code in listOf(400, 406, 429, 503)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("untrusted-server-detail"))
            val result = repository.refreshNearby(48.2, 16.3)
            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()!!.message!!
            assertTrue(message.contains("HTTP $code"))
            assertFalse(message.contains("untrusted-server-detail"))
        }
    }

    @Test fun `missing elements is query failure not no results`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repository.refreshNearby(48.2, 16.3).isFailure)
    }

    @Test fun `Overpass runtime remark is failure even with partial elements`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"elements":[],"remark":"runtime error: timeout"}"""))
        assertTrue(repository.refreshNearby(48.2, 16.3).isFailure)
    }

    @Test fun `malformed JSON is failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>unavailable</html>"))
        assertTrue(repository.refreshNearby(48.2, 16.3).isFailure)
    }

    @Test fun `valid empty response alone enables no results`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"elements":[]}"""))
        val rows = repository.refreshNearby(48.2, 16.3).getOrThrow()
        assertTrue(EmergencyUiData(services = rows, lookupSucceeded = true).showNoResults)
        assertFalse(EmergencyUiData().showNoResults)
        assertFalse(EmergencyUiData(error = "failure").showNoResults)
        assertFalse(EmergencyUiData(lookupSucceeded = true, isLoading = true).showNoResults)
    }

    @Test fun `failed refresh preserves stale rows but remains a failure`() = runBlocking {
        val stale = EmergencyService(name = "Cached hospital", fetchedAt = 1)
        dao.rows.value = listOf(stale)
        server.enqueue(MockResponse().setResponseCode(406))
        assertTrue(repository.refreshNearby(48.2, 16.3).isFailure)
        assertEquals(listOf(stale), dao.rows.value)
    }

    @Test fun `network and timeout mappings are safe and distinct`() {
        assertTrue(OverpassFailure.from(IOException("private-url")).message!!.contains("connection"))
        assertTrue(OverpassFailure.from(SocketTimeoutException()).message!!.contains("timed out"))
        assertFalse(OverpassFailure.from(IOException("private-url")).message!!.contains("private-url"))
    }

    @Test fun `socket timeout from API is a failed repository lookup`() = runBlocking {
        val timeoutApi = object : OverpassApi {
            override suspend fun query(query: String): OverpassResponse = throw SocketTimeoutException()
        }
        val result = EmergencyRepository(timeoutApi, dao).refreshNearby(48.2, 16.3)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OverpassFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("timed out"))
    }

    @Test fun `cancellation is not converted into an error result`() = runBlocking {
        val api = object : OverpassApi {
            override suspend fun query(query: String): OverpassResponse = throw CancellationException("cancel")
        }
        try {
            EmergencyRepository(api, dao).refreshNearby(48.2, 16.3)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
