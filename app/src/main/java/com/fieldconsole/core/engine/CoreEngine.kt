package com.fieldconsole.core.engine

import com.fieldconsole.data.local.entity.FieldEntry

/**
 * CoreEngine — contract for all optional external AI/CAD/GIS engine providers.
 *
 * Every provider (OpenAI, Google, SolidWorks, Autodesk) implements this interface.
 * When no API key is configured the [NoOpEngine] is used and the app falls back
 * to intent-based sharing (open ChatGPT app, share sheet, etc.).
 */
interface CoreEngine {

    /** Human-readable provider name shown in the UI. */
    val providerName: String

    /** Returns true if this engine has a valid key and is ready to process. */
    val isAvailable: Boolean

    /**
     * Analyse a [FieldEntry] and return a structured text result.
     * Implementations are responsible for their own network calls.
     * Must be called from a coroutine (suspend function).
     */
    suspend fun analyse(entry: FieldEntry): EngineResult
}

// ── Result type ───────────────────────────────────────────────────────────────

/** Sealed result type returned by every [CoreEngine] implementation. */
sealed class EngineResult {

    /** Analysis completed successfully. [content] contains the formatted response. */
    data class Success(val content: String) : EngineResult()

    /** The engine encountered an error. */
    data class Error(val message: String, val cause: Throwable? = null) : EngineResult()

    /** No API key is configured; the app should fall back to intent-based sharing. */
    object NotConfigured : EngineResult()
}
