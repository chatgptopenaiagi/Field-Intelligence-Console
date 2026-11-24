package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.data.emergency.EmergencyNumber
import com.fieldconsole.data.emergency.EmergencyNumbers
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.data.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class EmergencyUiData(
    val services:        List<EmergencyService>  = emptyList(),
    val numbers:         List<EmergencyNumber>   = emptyList(),
    val isLoading:       Boolean                 = false,
    val error:           String?                 = null,
    val countryCode:     String                  = "INT",
    val lookupSucceeded: Boolean                = false
) {
    val showNoResults: Boolean get() = lookupSucceeded && !isLoading && error == null && services.isEmpty()
    val staleWarning: String? get() = if (error != null && services.isNotEmpty())
        "STALE CACHED SERVICES — refresh failed; availability may have changed." else null
}

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val repository: EmergencyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyUiData())
    val state: StateFlow<EmergencyUiData> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load(lat: Double, lon: Double, countryCode: String = "UNKNOWN") {
        loadJob?.cancel()
        _state.value = EmergencyUiData(isLoading = true, countryCode = countryCode,
            numbers = EmergencyNumbers.forCountry(countryCode))

        // Observe cached services immediately
        loadJob = viewModelScope.launch {
            launch {
                repository.observeNearby(lat, lon).collect { services ->
                    _state.update { it.copy(services = services) }
                }
            }

            // Refresh and observation share a lifetime; changing scan cancels both.
            repository.refreshNearby(lat, lon)
                .onSuccess { services -> _state.update { it.copy(isLoading = false, services = services, lookupSucceeded = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
