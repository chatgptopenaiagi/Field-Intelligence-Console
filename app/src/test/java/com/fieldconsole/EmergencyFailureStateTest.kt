package com.fieldconsole

import androidx.lifecycle.ViewModelStore
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.remote.emergency.*
import com.fieldconsole.data.repository.EmergencyRepository
import com.fieldconsole.viewmodel.EmergencyViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyFailureStateTest {
    @Test fun `changing location cancels old request and cache observer`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val observed = java.util.concurrent.ConcurrentHashMap.newKeySet<Double>()
            val firstStarted = CompletableDeferred<Unit>()
            val firstCancelled = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val delegate = FakeEmergencyServiceDao()
            val dao = object : com.fieldconsole.data.local.dao.EmergencyServiceDao by delegate {
                override fun observeNearby(lat: Double, lon: Double) = flow {
                    observed.add(lat)
                    try { emit(listOf(EmergencyService(name = "Cache $lat", fetchedAt = 1))); awaitCancellation() }
                    finally { observed.remove(lat) }
                }
            }
            val api = object : OverpassApi {
                override suspend fun query(query: String): OverpassResponse {
                    if (query.contains(",1.0,2.0")) {
                        firstStarted.complete(Unit)
                        try { awaitCancellation() } finally { firstCancelled.complete(Unit) }
                    }
                    secondStarted.complete(Unit)
                    return OverpassResponse(emptyList())
                }
            }
            val vm = EmergencyViewModel(EmergencyRepository(api, dao))
            store.put("emergency", vm)
            vm.load(1.0, 2.0)
            withTimeout(2000) { firstStarted.await() }
            vm.load(3.0, 4.0, "US")
            withTimeout(2000) { firstCancelled.await(); secondStarted.await() }
            val result = withTimeout(2000) { vm.state.first { it.lookupSucceeded } }
            assertEquals(setOf(3.0), observed)
            assertEquals("US", result.countryCode)
            assertTrue(result.services.isEmpty())
            assertTrue(result.showNoResults)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `refresh failure keeps cached rows and exposes visible stale warning`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val cached = EmergencyService(name = "Cached hospital", lat = 48.2, lon = 16.3, fetchedAt = 1)
            val dao = FakeEmergencyServiceDao().apply { rows.value = listOf(cached) }
            val requestStarted = CompletableDeferred<Unit>()
            val releaseFailure = CompletableDeferred<Unit>()
            val api = object : OverpassApi {
                override suspend fun query(query: String): OverpassResponse {
                    requestStarted.complete(Unit)
                    releaseFailure.await()
                    throw HttpException(Response.error<OverpassResponse>(406, "blocked".toResponseBody()))
                }
            }
            val vm = EmergencyViewModel(EmergencyRepository(api, dao))
            store.put("emergency", vm)
            vm.load(48.2, 16.3, "AT")
            withTimeout(5000) { requestStarted.await() }
            assertEquals(listOf(cached), vm.state.value.services)
            assertTrue(vm.state.value.isLoading)
            assertFalse(vm.state.value.showNoResults)
            releaseFailure.complete(Unit)
            val failed = withTimeout(5000) { vm.state.first { it.error != null } }
            assertEquals(listOf(cached), failed.services)
            assertFalse(failed.isLoading)
            assertFalse(failed.lookupSucceeded)
            assertFalse(failed.showNoResults)
            assertTrue(failed.error!!.contains("406"))
            // EmergencyScreen renders this property alongside the failure message.
            assertTrue(failed.staleWarning!!.contains("STALE CACHED SERVICES"))
            assertTrue(failed.numbers.isNotEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
