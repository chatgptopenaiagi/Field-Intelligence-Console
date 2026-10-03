package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.data.repository.FieldEntryRepository
import com.fieldconsole.location.GeoContext
import com.fieldconsole.location.LocationManager
import com.fieldconsole.location.LocationResult
import com.fieldconsole.location.ReverseGeocoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class MainUiState {
    /** Waiting for user to press SCAN. */
    object Idle : MainUiState()

    /** Permission must be requested before scanning. */
    object RequestingPermission : MainUiState()

    /** GPS fix in progress. */
    object Scanning : MainUiState()

    /** GPS fix obtained — waiting for user observation input. */
    data class AwaitingObservation(val geo: GeoContext) : MainUiState()

    /** Entry saved — show 8-button action matrix. */
    data class ActionMatrix(val entry: FieldEntry, val geo: GeoContext) : MainUiState()

    /** Emergency screen with coordinates and navigation button. */
    data class Emergency(val geo: GeoContext) : MainUiState()

    /** Error with message. */
    data class Error(val message: String) : MainUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository:  FieldEntryRepository,
    private val locationMgr: LocationManager,
    private val geocoder:    ReverseGeocoder
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val allEntries = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entryCount = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Permission callbacks ──────────────────────────────────────────────────

    fun onPermissionGranted() = startScan()

    fun onPermissionDenied() {
        _uiState.value = MainUiState.Error(
            "Location permission is required.\n\n" +
            "Please grant ACCESS_FINE_LOCATION in Settings to use Field Intelligence Console."
        )
    }

    // ── SCAN flow ─────────────────────────────────────────────────────────────

    fun onScanPressed() {
        if (_uiState.value is MainUiState.Scanning) return
        if (!locationMgr.hasLocationPermission()) {
            _uiState.value = MainUiState.RequestingPermission
            return
        }
        startScan()
    }

    private fun startScan() {
        _uiState.value = MainUiState.Scanning
        viewModelScope.launch {
            when (val result = locationMgr.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val geo = geocoder.resolve(
                        latitude  = result.latitude,
                        longitude = result.longitude,
                        accuracy  = result.accuracy,
                        timestamp = result.timestamp
                    )
                    _uiState.value = MainUiState.AwaitingObservation(geo)
                }
                is LocationResult.PermissionDenied ->
                    _uiState.value = MainUiState.Error(
                        "Location permission denied.\nGrant it in Settings and try again."
                    )
                is LocationResult.LocationDisabled ->
                    _uiState.value = MainUiState.Error(
                        "GPS / location services are disabled.\nEnable them in device Settings."
                    )
                is LocationResult.Timeout ->
                    _uiState.value = MainUiState.Error(
                        "GPS fix timed out.\nMove to an open area and try again."
                    )
                is LocationResult.Error ->
                    _uiState.value = MainUiState.Error("Location error: ${result.message}")
            }
        }
    }

    fun onObservationSubmitted(note: String, tag: String, geo: GeoContext) {
        viewModelScope.launch {
            val entry = FieldEntry(
                latitude  = geo.latitude,
                longitude = geo.longitude,
                accuracy  = geo.accuracy,
                note      = note.trim(),
                tag       = tag.trim(),
                timestamp = geo.timestamp
            )
            val id    = repository.save(entry)
            val saved = entry.copy(id = id)
            _uiState.value = MainUiState.ActionMatrix(saved, geo)
        }
    }

    fun onObservationDismissed() { _uiState.value = MainUiState.Idle }

    fun onReset()          { _uiState.value = MainUiState.Idle }
    fun onErrorDismissed() { _uiState.value = MainUiState.Idle }

    fun onEmergencyPressed(geo: GeoContext) {
        _uiState.value = MainUiState.Emergency(geo)
    }

    fun onEmergencyDismissed() { _uiState.value = MainUiState.Idle }

    fun openEntry(entry: FieldEntry) {
        viewModelScope.launch {
            val geo = geocoder.resolve(entry.latitude, entry.longitude,
                                       entry.accuracy, entry.timestamp)
            _uiState.value = MainUiState.ActionMatrix(entry, geo)
        }
    }

    fun deleteEntry(entry: FieldEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }
}
