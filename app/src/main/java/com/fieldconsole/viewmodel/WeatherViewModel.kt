package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.data.local.entity.WeatherData
import com.fieldconsole.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WeatherUiState {
    object Idle                              : WeatherUiState()
    object Loading                           : WeatherUiState()
    data class Success(val data: WeatherData): WeatherUiState()
    data class Error(val message: String)    : WeatherUiState()
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repository: WeatherRepository
) : ViewModel() {

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    fun load(lat: Double, lon: Double) {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            repository.getWeather(lat, lon)
                .onSuccess { _state.value = WeatherUiState.Success(it) }
                .onFailure { _state.value = WeatherUiState.Error(it.message ?: "Weather unavailable") }
        }
    }

    fun reset() { _state.value = WeatherUiState.Idle }
}
