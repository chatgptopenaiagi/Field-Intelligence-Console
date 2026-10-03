package com.fieldconsole

import com.fieldconsole.data.remote.emergency.*
import com.fieldconsole.data.repository.EmergencyRepository
import com.fieldconsole.di.AppModule
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class EmergencyGeometryTest {
    @Test fun `query includes all geometry types and requests centers`() {
        val query = OverpassApi.emergencyQuery(48.2, 16.3)
        assertEquals(7, Regex("nwr\\[").findAll(query).count())
        assertTrue(query.contains("out center;"))
    }

    @Test fun `wire response retains equator node and way and relation centers`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"elements":[
                {"type":"node","id":1,"lat":0.0,"lon":0.01,"tags":{"amenity":"hospital","name":"Equator"}},
                {"type":"way","id":2,"center":{"lat":0.02,"lon":0.0},"tags":{"amenity":"police","name":"Way"}},
                {"type":"relation","id":3,"center":{"lat":0.03,"lon":0.0},"tags":{"amenity":"hospital","name":"Relation"}},
                {"type":"node","id":4,"lat":10.0,"tags":{"amenity":"pharmacy","name":"Missing longitude"}},
                {"type":"way","id":5,"tags":{"amenity":"hospital","name":"No geometry"}},
                {"type":"node","id":6,"lat":91.0,"lon":0.0,"tags":{"amenity":"hospital","name":"Invalid"}}
            ]}"""))
            val api = AppModule.provideOverpassRetrofit(Gson()).newBuilder()
                .baseUrl(server.url("/")).build().create(OverpassApi::class.java)
            val result = EmergencyRepository(api, FakeEmergencyServiceDao()).refreshNearby(0.0, 0.0).getOrThrow()
            assertEquals(listOf("Equator", "Way", "Relation"), result.map { it.name })
            assertEquals(0.01, result.first().lon, 0.0)
            assertTrue(result.all { it.distanceM > 0 })
        } finally { server.shutdown() }
    }
}
