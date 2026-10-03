package com.fieldconsole.security

/**
 * ApiProvider — enumeration of all optional external API providers supported
 * by the Field Intelligence Console Core Engine system.
 *
 * Each entry carries a human-readable [displayName] shown in the Settings UI,
 * and a [prefKey] used as the identifier in encrypted storage.
 */
enum class ApiProvider(val displayName: String, val prefKey: String, val description: String) {

    OPENAI(
        displayName  = "OpenAI / ChatGPT",
        prefKey      = "api_openai",
        description  = "In-app AI analysis via GPT-4o. Replaces intent-based ChatGPT sharing."
    ),
    GOOGLE_MAPS(
        displayName  = "Google Maps & Places",
        prefKey      = "api_google_maps",
        description  = "Live nearby-place enrichment for Logistics, Risk, and Info Scan."
    ),
    GOOGLE_EARTH(
        displayName  = "Google Earth Engine",
        prefKey      = "api_google_earth",
        description  = "Satellite imagery metadata and land-use context for Navigation & History."
    ),
    SOLIDWORKS(
        displayName  = "SolidWorks / 3DEXPERIENCE",
        prefKey      = "api_solidworks",
        description  = "CAD/part lookup by field tag for infrastructure and engineering contexts."
    ),
    AUTODESK(
        displayName  = "Autodesk APS / Forge",
        prefKey      = "api_autodesk",
        description  = "BIM and infrastructure model context. Format: clientId:clientSecret"
    )
}
