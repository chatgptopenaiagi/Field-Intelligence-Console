package com.fieldconsole.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldconsole.core.engine.CoreEngineFactory
import com.fieldconsole.core.engine.EngineResult
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.prompt.FieldPromptGenerator
import com.fieldconsole.security.ApiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class AiAnalysisUiState {
    object Idle                              : AiAnalysisUiState()
    object Loading                           : AiAnalysisUiState()
    data class Result(val content: String)   : AiAnalysisUiState()
    data class Error(val message: String)    : AiAnalysisUiState()
    /** No key configured — UI should show the generated prompt for sharing. */
    data class IntentFallback(val prompt: String) : AiAnalysisUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * AiAnalysisViewModel — orchestrates the AI Analysis action.
 *
 * If an API key is configured, routes through the appropriate [CoreEngine].
 * Otherwise, generates a structured prompt and signals [AiAnalysisUiState.IntentFallback]
 * so the UI can offer clipboard copy / share sheet / ChatGPT intent.
 */
@HiltViewModel
class AiAnalysisViewModel @Inject constructor(
    private val engineFactory: CoreEngineFactory,
    private val promptGenerator: FieldPromptGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val uiState: StateFlow<AiAnalysisUiState> = _uiState.asStateFlow()

    /** The generated prompt text (always available regardless of engine state). */
    private val _generatedPrompt = MutableStateFlow("")
    val generatedPrompt: StateFlow<String> = _generatedPrompt.asStateFlow()

    /**
     * Run analysis for [entry] using the best available engine.
     * Optionally force a specific [preferredProvider].
     */
    fun analyse(entry: FieldEntry, preferredProvider: ApiProvider? = null) {
        _generatedPrompt.value = promptGenerator.generate(entry)
        _uiState.value = AiAnalysisUiState.Loading

        viewModelScope.launch {
            val engine = engineFactory.buildEngine(preferredProvider)
            when (val result = engine.analyse(entry)) {
                is EngineResult.Success      -> _uiState.value = AiAnalysisUiState.Result(result.content)
                is EngineResult.Error        -> _uiState.value = AiAnalysisUiState.Error(result.message)
                is EngineResult.NotConfigured -> _uiState.value =
                    AiAnalysisUiState.IntentFallback(_generatedPrompt.value)
            }
        }
    }

    /** Generate the prompt without running an engine (for preview / copy). */
    fun generatePromptOnly(entry: FieldEntry) {
        _generatedPrompt.value = promptGenerator.generate(entry)
        _uiState.value = AiAnalysisUiState.IntentFallback(_generatedPrompt.value)
    }

    fun reset() {
        _uiState.value = AiAnalysisUiState.Idle
    }
}
