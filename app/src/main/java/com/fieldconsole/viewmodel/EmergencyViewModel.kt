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
import javax.inject.Inject

data class EmergencyUiData(
    val services:        List<EmergencyService>  = emptyList(),
    val numbers:         List<EmergencyNumber>   = emptyList(),
    val isLoading:       Boolean                 = false,
    val error:           String?                 = null,
    val countryCode:     String                  = "INT"
)

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val repository: EmergencyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyUiData())
    val state: StateFlow<EmergencyUiData> = _state.asStateFlow()

    fun load(lat: Double, lon: Double, countryCode: String = "AT") {
        _state.update { it.copy(isLoading = true, error = null, countryCode = countryCode) }
        _state.update { it.copy(numbers = EmergencyNumbers.forCountry(countryCode)) }

        // Observe cached services immediately
        viewModelScope.launch {
            repository.observeNearby(lat, lon).collect { services ->
                _state.update { it.copy(services = services) }
            }
        }

        // Refresh from network
        viewModelScope.launch {
            repository.refreshNearby(lat, lon)
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
