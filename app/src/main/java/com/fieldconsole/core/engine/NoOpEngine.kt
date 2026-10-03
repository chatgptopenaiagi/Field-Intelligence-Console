package com.fieldconsole.core.engine

import com.fieldconsole.data.local.entity.FieldEntry

/**
 * NoOpEngine — default engine used when no API key is configured.
 *
 * Always returns [EngineResult.NotConfigured] so the calling ViewModel can
 * gracefully fall back to intent-based sharing (e.g., open the ChatGPT app
 * with a pre-built prompt, or use the system share sheet).
 */
class NoOpEngine : CoreEngine {
    override val providerName = "No Engine (Intent Fallback)"
    override val isAvailable  = false
    override suspend fun analyse(entry: FieldEntry): EngineResult = EngineResult.NotConfigured
}
