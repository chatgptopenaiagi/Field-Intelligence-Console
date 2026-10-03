package com.fieldconsole

import android.app.Application
import androidx.room.Room
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.remote.emergency.*
import com.fieldconsole.data.repository.EmergencyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EmergencyCacheTest {
    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }

    @Test fun `neighboring scan cache is neither reused nor deleted`() = runBlocking {
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build().use { db ->
            val dao = db.emergencyServiceDao()
            dao.insertAll(listOf(EmergencyService(scanLat = 48.2, scanLon = 16.3, name = "Original")))
            assertTrue(dao.getNearby(48.21, 16.31).isEmpty())
            dao.deleteForLocation(48.21, 16.31)
            assertEquals("Original", dao.getNearby(48.2, 16.3).single().name)
        }
    }

    @Test fun `failed cache replacement rolls back deletion`() = runBlocking {
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build().use { db ->
            val dao = db.emergencyServiceDao()
            dao.insertAll(listOf(EmergencyService(scanLat = 1.0, scanLon = 2.0, name = "Keep", fetchedAt = 1L)))
            db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_refresh BEFORE INSERT ON emergency_services
                BEGIN SELECT RAISE(ABORT, 'test insertion failure'); END""")
            val api = object : OverpassApi {
                override suspend fun query(query: String) = OverpassResponse(listOf(
                    OverpassElement(type = "node", lat = 1.01, lon = 2.01, tags = mapOf("amenity" to "police"))))
            }
            assertTrue(EmergencyRepository(api, dao).refreshNearby(1.0, 2.0).isFailure)
            assertEquals("Keep", dao.getNearby(1.0, 2.0).single().name)
        }
    }

    @Test fun `all cached rows must be fresh and timestamps cannot be in future`() = runBlocking {
        for (badTime in listOf(1L, System.currentTimeMillis() + 86_400_000)) {
            val dao = FakeEmergencyServiceDao()
            dao.rows.value = listOf(EmergencyService(fetchedAt = System.currentTimeMillis()), EmergencyService(fetchedAt = badTime))
            var calls = 0
            val api = object : OverpassApi {
                override suspend fun query(query: String): OverpassResponse { calls++; return OverpassResponse(emptyList()) }
            }
            assertTrue(EmergencyRepository(api, dao).refreshNearby(1.0, 2.0).isSuccess)
            assertEquals(1, calls)
        }
    }

    @Test fun `successful empty lookup is reused within session`() = runBlocking {
        var calls = 0
        val api = object : OverpassApi {
            override suspend fun query(query: String): OverpassResponse { calls++; return OverpassResponse(emptyList()) }
        }
        val repository = EmergencyRepository(api, FakeEmergencyServiceDao())
        repeat(2) { assertTrue(repository.refreshNearby(1.0, 2.0).getOrThrow().isEmpty()) }
        assertEquals(1, calls)
        repository.refreshNearby(2.0, 3.0)
        assertEquals(2, calls)
    }
}
