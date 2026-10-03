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
        description  = "Reserved provider setting. In-app analysis is not connected; AI ANALYSIS opens the existing external prompt action."
    ),
    GOOGLE_MAPS(
        displayName  = "Google Maps & Places",
        prefKey      = "api_google_maps",
        description  = "Reserved provider setting. Live Places enrichment is not connected."
    ),
    GOOGLE_EARTH(
        displayName  = "Google Earth Engine",
        prefKey      = "api_google_earth",
        description  = "Reserved provider setting. Satellite and land-use integration is not connected."
    ),
    SOLIDWORKS(
        displayName  = "SolidWorks / 3DEXPERIENCE",
        prefKey      = "api_solidworks",
        description  = "Incomplete CAD adapter; saving a key does not activate a working integration."
    ),
    AUTODESK(
        displayName  = "Autodesk APS / Forge",
        prefKey      = "api_autodesk",
        description  = "Incomplete BIM adapter. Do not enter confidential server client secrets in this mobile application."
    )
}
