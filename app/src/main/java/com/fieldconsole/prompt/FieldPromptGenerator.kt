package com.fieldconsole.prompt

import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.location.GeoContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FieldPromptGenerator v2 — generates structured AI prompts for all 12 modules.
 *
 * Every prompt distinguishes:
 *  - CONFIRMED FACTS (from GPS, geocoder, user input)
 *  - AI INFERENCE (requested from the LLM)
 *  - CONFIDENCE LEVEL (requested from the LLM)
 *
 * Never presents speculation as fact.
 */
@Singleton
class FieldPromptGenerator @Inject constructor() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'Z", Locale.US)

    // ── Full AI Analysis prompt (Module 4) ────────────────────────────────────

    fun generate(entry: FieldEntry, geo: GeoContext? = null): String = buildString {
        appendHeader(entry, geo)
        appendLine()
        appendLine("ANALYSIS REQUEST — FIELD INTELLIGENCE REPORT")
        appendLine("Please produce a structured professional field intelligence report.")
        appendLine("For each section, mark every statement as [CONFIRMED FACT] or [AI INFERENCE].")
        appendLine("End each section with a CONFIDENCE LEVEL: HIGH / MEDIUM / LOW.")
        appendLine()
        appendLine("1. SITUATION OVERVIEW")
        appendLine("   Current function and land use of this location.")
        appendLine("   Zone type (residential / industrial / commercial / rural / mixed).")
        appendLine()
        appendLine("2. OPERATIONAL ASSESSMENT")
        appendLine("   Suitability for field operations.")
        appendLine("   Access routes and mobility constraints.")
        appendLine()
        appendLine("3. TERRAIN ANALYSIS")
        appendLine("   Terrain type, elevation profile, and natural features.")
        appendLine("   Visibility range and line-of-sight considerations.")
        appendLine()
        appendLine("4. INFRASTRUCTURE")
        appendLine("   Roads, utilities, transport links.")
        appendLine("   Building density and construction type.")
        appendLine()
        appendLine("5. NEARBY STRATEGIC ASSETS")
        appendLine("   Hospitals, police, fire, fuel, transport within 2 km.")
        appendLine()
        appendLine("6. CIVILIAN ACTIVITY ESTIMATION")
        appendLine("   Estimated population density and activity level.")
        appendLine()
        appendLine("7. RISK SCORE")
        appendLine("   Overall risk level: LOW / MEDIUM / HIGH / CRITICAL.")
        appendLine("   Key risk factors.")
        appendLine()
        appendLine("8. HISTORICAL CONTEXT")
        appendLine("   Known historical background, former land use, significant events.")
        appendLine()
        appendLine("9. FINANCIAL OVERVIEW")
        appendLine("   Estimated property values and economic activity.")
        appendLine()
        appendLine("10. RECOMMENDED NEXT MOVE")
        appendLine("    Actionable recommendations for field personnel.")
        appendLine()
        appendLine("11. CONFIDENCE PERCENTAGE")
        appendLine("    Overall confidence in this assessment: X%")
        appendLine()
        appendLine("FORMAT: Use numbered sections. Be concise and field-readable.")
        appendLine("===========================================")
    }

    // ── Info Scan prompt (Module 1) ───────────────────────────────────────────

    fun infoScanPrompt(entry: FieldEntry, geo: GeoContext? = null): String = buildString {
        appendHeader(entry, geo)
        appendLine()
        appendLine("INFO SCAN REQUEST")
        appendLine("Provide a comprehensive location intelligence report covering:")
        appendLine()
        appendLine("1. CURRENT LAND USE — What is this area used for right now?")
        appendLine("2. HISTORICAL CONTEXT — What was here before?")
        appendLine("3. URBAN PLANNING — Zoning, development plans, regulations.")
        appendLine("4. INFRASTRUCTURE — Roads, utilities, public services.")
        appendLine("5. TRANSPORTATION — Public transport, access routes, parking.")
        appendLine("6. ENVIRONMENTAL — Green spaces, water, pollution, hazards.")
        appendLine("7. POPULATION — Density, demographics, activity patterns.")
        appendLine("8. STRATEGIC IMPORTANCE — Why does this location matter?")
        appendLine("9. BUILDING DENSITY — Low / Medium / High / Very High.")
        appendLine("10. LAND OWNERSHIP — Public, private, government (if known).")
        appendLine("11. KNOWN LANDMARKS — Notable buildings, monuments, features.")
        appendLine("12. AI SUMMARY — 3-sentence executive summary.")
        appendLine()
        appendLine("Mark each statement: [CONFIRMED FACT] or [AI INFERENCE]")
        appendLine("End with: CONFIDENCE LEVEL: HIGH / MEDIUM / LOW")
    }

    // ── History prompt (Module 2) ─────────────────────────────────────────────

    fun historyPrompt(entry: FieldEntry, geo: GeoContext? = null): String = buildString {
        appendHeader(entry, geo)
        appendLine()
        appendLine("HISTORY MODULE REQUEST")
        appendLine("Provide a historical intelligence report covering:")
        appendLine()
        appendLine("1. URBAN DEVELOPMENT TIMELINE — When was this area developed?")
        appendLine("2. PREVIOUS LAND USAGE — Agricultural, industrial, military, residential?")
        appendLine("3. HISTORICAL MAPS — What do historical maps show for this area?")
        appendLine("4. KNOWN HISTORICAL EVENTS — Battles, disasters, notable events.")
        appendLine("5. PROTECTED BUILDINGS — Listed or heritage-protected structures.")
        appendLine("6. FORMER RAIL INFRASTRUCTURE — Old rail lines, stations, depots.")
        appendLine("7. INDUSTRIAL HISTORY — Former factories, mines, processing plants.")
        appendLine("8. MUNICIPAL HISTORY — Administrative changes, boundary shifts.")
        appendLine("9. SATELLITE IMAGERY NOTES — What changes are visible over time?")
        appendLine()
        appendLine("Mark each statement: [CONFIRMED FACT] or [AI INFERENCE]")
        appendLine("End with: CONFIDENCE LEVEL: HIGH / MEDIUM / LOW")
    }

    // ── Risk/Safety prompt (Module 5) ─────────────────────────────────────────

    fun riskPrompt(entry: FieldEntry, geo: GeoContext? = null): String = buildString {
        appendHeader(entry, geo)
        appendLine()
        appendLine("RISK & SAFETY ASSESSMENT REQUEST")
        appendLine("Provide a comprehensive risk assessment covering:")
        appendLine()
        appendLine("1. CONSTRUCTION ZONES — Active construction, road works.")
        appendLine("2. ROAD CLOSURES — Known or probable road closures.")
        appendLine("3. FLOOD RISK — Proximity to water, flood plain status.")
        appendLine("4. WEATHER HAZARDS — Seasonal weather risks for this location.")
        appendLine("5. CRIME AWARENESS — General safety profile of this area.")
        appendLine("6. RESTRICTED AREAS — Military, government, private zones.")
        appendLine("7. RAILWAY HAZARDS — Level crossings, rail infrastructure risks.")
        appendLine("8. HIGH VOLTAGE INFRASTRUCTURE — Power lines, substations.")
        appendLine("9. NIGHT VISIBILITY — Lighting conditions after dark.")
        appendLine("10. INDUSTRIAL HAZARDS — Factories, chemical plants, refineries.")
        appendLine("11. FIRE RISK — Vegetation, industrial, structural fire risk.")
        appendLine("12. EMERGENCY ACCESS — Routes for emergency vehicles.")
        appendLine("13. TERRAIN HAZARDS — Steep slopes, unstable ground, water.")
        appendLine()
        appendLine("RISK LEVEL: LOW / MEDIUM / HIGH / CRITICAL")
        appendLine("RISK EXPLANATION: 2-3 sentences.")
        appendLine()
        appendLine("Mark each statement: [CONFIRMED FACT] or [AI INFERENCE]")
        appendLine("End with: CONFIDENCE LEVEL: HIGH / MEDIUM / LOW")
    }

    // ── Financial prompt (Module 7) ───────────────────────────────────────────

    fun financialPrompt(entry: FieldEntry, geo: GeoContext? = null): String = buildString {
        appendHeader(entry, geo)
        appendLine()
        appendLine("FINANCIAL INTELLIGENCE REQUEST")
        appendLine("Provide a financial and economic assessment covering:")
        appendLine()
        appendLine("1. RESIDENTIAL PRICES — Estimated property values per m².")
        appendLine("2. COMMERCIAL PRICES — Office/retail space costs.")
        appendLine("3. RENTAL ESTIMATION — Monthly rental rates.")
        appendLine("4. BUSINESS DENSITY — Number and type of businesses.")
        appendLine("5. INDUSTRIAL ACTIVITY — Manufacturing, logistics, warehousing.")
        appendLine("6. EMPLOYMENT PROFILE — Major employers, job types.")
        appendLine("7. ECONOMIC ACTIVITY — GDP contribution, economic zone status.")
        appendLine("8. URBAN DEVELOPMENT — Recent or planned development projects.")
        appendLine("9. INVESTMENT ATTRACTIVENESS — Score 1-10 with justification.")
        appendLine("10. PROPERTY TRENDS — Rising, stable, or declining values?")
        appendLine("11. CONSTRUCTION PROJECTS — Active or planned construction.")
        appendLine()
        appendLine("Mark each statement: [CONFIRMED FACT] or [AI INFERENCE]")
        appendLine("End with: CONFIDENCE LEVEL: HIGH / MEDIUM / LOW")
    }

    // ── Short summary for clipboard ───────────────────────────────────────────

    fun generateShort(entry: FieldEntry, geo: GeoContext? = null): String {
        val place = geo?.fullPlaceName ?: "${entry.latitude}, ${entry.longitude}"
        return "Field scan at $place (±${entry.accuracy.toInt()}m) " +
               "on ${dateFmt.format(Date(entry.timestamp))}. " +
               "Observation: ${entry.note.ifBlank { "none" }}. " +
               "Tag: ${entry.tag.ifBlank { "untagged" }}. Please analyse."
    }

    // ── Shared header ─────────────────────────────────────────────────────────

    private fun StringBuilder.appendHeader(entry: FieldEntry, geo: GeoContext?) {
        appendLine("=== FIELD INTELLIGENCE CONSOLE — ANALYSIS REQUEST ===")
        appendLine()
        appendLine("LOCATION DATA [CONFIRMED FACT]")
        appendLine("  Latitude   : ${entry.latitude}")
        appendLine("  Longitude  : ${entry.longitude}")
        appendLine("  Accuracy   : ±${entry.accuracy.toInt()} m")
        appendLine("  Timestamp  : ${dateFmt.format(Date(entry.timestamp))}")
        if (geo != null) {
            appendLine("  Place Name : ${geo.fullPlaceName}")
            appendLine("  Maps Link  : ${geo.mapsLink}")
        }
        if (entry.note.isNotBlank()) {
            appendLine()
            appendLine("FIELD OBSERVATION [CONFIRMED FACT]")
            appendLine("  Note       : ${entry.note}")
            appendLine("  Tag        : ${entry.tag.ifBlank { "untagged" }}")
            if (entry.priority.isNotBlank()) appendLine("  Priority   : ${entry.priority}")
            if (entry.missionId.isNotBlank()) appendLine("  Mission ID : ${entry.missionId}")
            if (entry.operator.isNotBlank()) appendLine("  Operator   : ${entry.operator}")
        }
    }
}
