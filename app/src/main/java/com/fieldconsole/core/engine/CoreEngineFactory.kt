package com.fieldconsole.core.engine

import com.fieldconsole.core.engine.autodesk.AutodeskEngine
import com.fieldconsole.core.engine.google.GoogleEngine
import com.fieldconsole.core.engine.openai.OpenAIEngine
import com.fieldconsole.core.engine.solidworks.SolidWorksEngine
import com.fieldconsole.security.ApiKeyStore
import com.fieldconsole.security.ApiProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CoreEngineFactory — Strategy selector for the pluggable Core Code Engine.
 *
 * Selects the best available engine based on which API keys are configured.
 * Default priority order: OpenAI → Google Maps → Autodesk → SolidWorks → NoOp.
 *
 * A specific [ApiProvider] can be requested explicitly (e.g., from the
 * AI Analysis screen's provider picker).
 */
@Singleton
class CoreEngineFactory @Inject constructor(
    private val keyStore: ApiKeyStore
) {

    /**
     * Build and return the appropriate [CoreEngine].
     *
     * @param preferredProvider If non-null, attempt to use this provider first.
     *                          Falls back to [NoOpEngine] if the key is absent.
     */
    fun buildEngine(preferredProvider: ApiProvider? = null): CoreEngine {
        val target = preferredProvider ?: detectBestProvider()
        return when (target) {
            ApiProvider.OPENAI -> {
                val key = keyStore.getKey(ApiProvider.OPENAI)
                if (key != null) OpenAIEngine(key) else NoOpEngine()
            }
            ApiProvider.GOOGLE_MAPS, ApiProvider.GOOGLE_EARTH -> {
                val key = keyStore.getKey(ApiProvider.GOOGLE_MAPS)
                    ?: keyStore.getKey(ApiProvider.GOOGLE_EARTH)
                if (key != null) GoogleEngine(key) else NoOpEngine()
            }
            ApiProvider.AUTODESK -> {
                val key = keyStore.getKey(ApiProvider.AUTODESK)
                if (key != null) AutodeskEngine(key) else NoOpEngine()
            }
            ApiProvider.SOLIDWORKS -> {
                val key = keyStore.getKey(ApiProvider.SOLIDWORKS)
                if (key != null) SolidWorksEngine(key) else NoOpEngine()
            }
        }
    }

    /** Returns the highest-priority provider that has a stored key, or OPENAI as default. */
    private fun detectBestProvider(): ApiProvider {
        val priority = listOf(
            ApiProvider.OPENAI,
            ApiProvider.GOOGLE_MAPS,
            ApiProvider.AUTODESK,
            ApiProvider.SOLIDWORKS
        )
        return priority.firstOrNull { keyStore.hasKey(it) } ?: ApiProvider.OPENAI
    }
}
