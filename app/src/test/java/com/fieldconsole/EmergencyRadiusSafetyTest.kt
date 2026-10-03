package com.fieldconsole

import com.fieldconsole.data.remote.emergency.*
import com.fieldconsole.data.repository.EmergencyRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.fieldconsole.data.local.entity.EmergencyService
import org.junit.Assert.*
import org.junit.Test

class EmergencyRadiusSafetyTest {
    @Test fun `legacy cache cannot reintroduce out of radius rows or heuristic metadata`() = runBlocking {
        val dao = FakeEmergencyServiceDao().apply {
            rows.value = listOf(
                EmergencyService(name = "Near", lat = 0.0, lon = 0.0, isOpen = true, travelTimeMin = 8),
                EmergencyService(name = "Far", lat = 1.0, lon = 0.0, distanceM = 1f))
        }
        val api = object : OverpassApi {
            override suspend fun query(query: String): OverpassResponse = error("Fresh cache should suffice")
        }
        val repository = EmergencyRepository(api, dao)
        val observed = repository.observeNearby(0.0, 0.0).first()
        val refreshed = repository.refreshNearby(0.0, 0.0).getOrThrow()
        assertEquals(listOf("Near"), observed.map { it.name })
        assertEquals(observed, refreshed)
        assertFalse(observed.single().isOpen)
        assertEquals(0, observed.single().travelTimeMin)
        assertEquals(2, dao.rows.value.size) // Sanitizing presentation never deletes old rows.
    }
    private fun node(lat: Double, lon: Double, name: String = "Synthetic") =
        OverpassElement(type = "node", lat = lat, lon = lon,
            tags = mapOf("amenity" to "hospital", "name" to name))

    private fun repository(elements: List<OverpassElement>, called: () -> Unit = {}) =
        EmergencyRepository(object : OverpassApi {
            override suspend fun query(query: String): OverpassResponse {
                called()
                return OverpassResponse(elements)
            }
        }, FakeEmergencyServiceDao())

    @Test fun `area centers outside requested radius are excluded`() = runBlocking {
        val distant = OverpassElement(type = "way", center = OverpassCenter(0.1, 0.0),
            tags = mapOf("amenity" to "hospital"))
        val result = repository(listOf(node(0.0, 0.0), distant))
            .refreshNearby(0.0, 0.0).getOrThrow()
        assertEquals(1, result.size)
        assertEquals(0f, result.single().distanceM)
    }

    @Test fun `invalid scan coordinates fail before network access`() = runBlocking {
        var calls = 0
        for ((lat, lon) in listOf(Double.NaN to 0.0, 91.0 to 0.0,
            0.0 to Double.POSITIVE_INFINITY, 0.0 to -181.0)) {
            assertTrue(repository(emptyList()) { calls++ }.refreshNearby(lat, lon).isFailure)
        }
        assertEquals(0, calls)
    }

    @Test fun `distance crosses antimeridian and handles poles without false exclusions`() = runBlocking {
        val across = repository(listOf(node(0.0, -179.99)))
            .refreshNearby(0.0, 179.99).getOrThrow().single()
        assertEquals(2224f, across.distanceM, 2f)
        val pole = repository(listOf(node(90.0, 180.0))).refreshNearby(90.0, 0.0).getOrThrow().single()
        assertTrue(pole.distanceM < 0.01f)
    }

    @Test fun `radius includes boundary but excludes point beyond it`() = runBlocking {
        val boundary = Math.toDegrees(5000.0 / 6371000.0)
        val result = repository(listOf(node(boundary, 0.0, "Boundary"),
            node(boundary + 0.000001, 0.0, "Outside")))
            .refreshNearby(0.0, 0.0).getOrThrow()
        assertEquals(listOf("Boundary"), result.map { it.name })
    }

    @Test fun `provider discovery does not invent opening status or travel time`() = runBlocking {
        val result = repository(listOf(node(0.0, 0.01))).refreshNearby(0.0, 0.0).getOrThrow().single()
        assertFalse(result.isOpen)
        assertEquals(0, result.travelTimeMin)
    }
}
