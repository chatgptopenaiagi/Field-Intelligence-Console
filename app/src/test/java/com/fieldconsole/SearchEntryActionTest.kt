package com.fieldconsole

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.room.Room
import com.fieldconsole.data.local.database.AppDatabase
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.data.repository.FieldEntryRepository
import com.fieldconsole.location.*
import com.fieldconsole.ui.search.openSearchEntry
import com.fieldconsole.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SearchEntryActionTest {
    @Test fun `selected search result reopens original scan on existing main destination without writing`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val repository = FieldEntryRepository(db.fieldEntryDao())
            val original = FieldEntry(id = 42, latitude = 48.2, longitude = 16.3, accuracy = 7f,
                timestamp = 123L, note = "Find this observation", tag = "survey", missionId = "mission",
                priority = "HIGH", operator = "operator", isFavorite = true)
            repository.save(original)
            val selected = withTimeout(5_000) { repository.search("observation").first() }.single()
            val geocoder = ReverseGeocoder(GeocodeBackend { _, _, callback ->
                callback(GeocodeReply(failure = "Address unavailable. Coordinates remain available.")); {}
            })
            val mainVm = MainViewModel(repository, LocationManager(app), geocoder)
            store.put("main", mainVm)
            val nav = NavHostController(app)
            nav.setViewModelStore(store)
            nav.navigatorProvider.addNavigator(ComposeNavigator())
            nav.graph = nav.createGraph(startDestination = "main") {
                composable("main") {}
                composable("search") {}
            }
            val mainEntry = nav.getBackStackEntry("main")
            nav.navigate("search")
            assertEquals("search", nav.currentDestination!!.route)
            openSearchEntry(nav, mainVm, selected)
            assertEquals("main", nav.currentDestination!!.route)
            assertSame(mainEntry, nav.getBackStackEntry("main"))
            val state = mainVm.uiState.value as MainUiState.ActionMatrix
            assertEquals(original, state.entry)
            assertEquals(original.latitude, state.geo.latitude, 0.0)
            assertEquals(original.timestamp, state.geo.timestamp)
            assertNotNull(state.geo.geocodingNotice)
            assertEquals(listOf(original), db.fieldEntryDao().getAll())
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
}
