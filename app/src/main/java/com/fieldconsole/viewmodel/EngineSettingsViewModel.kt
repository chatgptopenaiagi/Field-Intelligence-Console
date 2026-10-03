package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.security.ApiKeyStore
import com.fieldconsole.security.ApiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EngineSettingsUiState(
    val configuredProviders: Set<ApiProvider> = emptySet(),
    val saveSuccess: ApiProvider?             = null,
    val deleteSuccess: ApiProvider?           = null
)

/**
 * EngineSettingsViewModel — manages the Engines & APIs settings screen.
 *
 * Delegates all key storage to [ApiKeyStore]. Exposes reactive [uiState]
 * so the Compose UI can reflect the current configuration without polling.
 */
@HiltViewModel
class EngineSettingsViewModel @Inject constructor(
    private val keyStore: ApiKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EngineSettingsUiState())
    val uiState: StateFlow<EngineSettingsUiState> = _uiState.asStateFlow()

    init { refreshState() }

    fun saveKey(provider: ApiProvider, rawKey: String) {
        viewModelScope.launch {
            keyStore.saveKey(provider, rawKey.trim())
            _uiState.value = _uiState.value.copy(
                configuredProviders = keyStore.configuredProviders(),
                saveSuccess         = provider
            )
        }
    }

    fun deleteKey(provider: ApiProvider) {
        viewModelScope.launch {
            keyStore.deleteKey(provider)
            _uiState.value = _uiState.value.copy(
                configuredProviders = keyStore.configuredProviders(),
                deleteSuccess       = provider
            )
        }
    }

    fun clearTransientState() {
        _uiState.value = _uiState.value.copy(saveSuccess = null, deleteSuccess = null)
    }

    private fun refreshState() {
        _uiState.value = EngineSettingsUiState(
            configuredProviders = keyStore.configuredProviders()
        )
    }
}
