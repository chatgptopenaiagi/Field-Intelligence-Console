package com.fieldconsole.prompt

import android.content.Context
import com.fieldconsole.data.emergency.CountryBoundary
import com.fieldconsole.location.GeoContext
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

class HumanContext(context: Context, val language: String) {
    private val catalog = context.assets.open("cognitive_questions.json").bufferedReader().use { JSONObject(it.readText()) }
    val categories = catalog.getJSONArray("categories").objects()
    val questions = catalog.getJSONArray("questions").objects()

    fun text(key: String) = catalog.getJSONObject("text").getJSONObject(key).getString(language)
    fun local(row: JSONObject, key: String) = row.getJSONObject(key).getString(language)

    fun compose(id: String?, request: String, geo: GeoContext, override: String?, disclosure: String,
                observation: String = "", excerpt: String = "", target: String = ""): String {
        require(disclosure in listOf("none", "region", "exact") && request.isNotBlank())
        val item = id?.let { questions.single { q -> q.getString("id") == id } }
        val lines = mutableListOf("FIELD INTELLIGENCE CONSOLE", "", (id?.let { "$it · " } ?: "") + request.trim(), "")
        fun field(key: String, value: String?) {
            lines += text(key) + " · " + (value?.takeIf { it.isNotBlank() } ?: text("unknown"))
        }
        val origin = when {
            !geo.validCoordinates -> "unknown"
            geo.saved -> "historical"
            geo.manual -> "manual"
            !geo.provider.isNullOrBlank() -> "device"
            else -> "unknown"
        }
        field("location", text(if (disclosure == "none") "none" else origin))
        var country: String? = null
        if (disclosure != "none") {
            val known = origin != "unknown"
            val detected = if (known) geo.countryCode?.takeIf { it.isNotBlank() }
                ?: CountryBoundary.interior(geo.latitude, geo.longitude) else null
            if (disclosure == "exact") {
                field("coords", if (known) "${geo.latitude}, ${geo.longitude}" else null)
                field("accuracy", if (known && !geo.manual && geo.accuracy > 0 && geo.accuracy.isFinite())
                    "${geo.accuracy} m" else null)
                field("measured", geo.measuredAt.takeIf { known && it != "UNKNOWN" })
                field("geocoded", if (known) listOfNotNull(geo.street, geo.district, geo.city).joinToString(", ") else null)
            }
            field("area", geo.region.takeIf { known })
            field("detected", detected)
            field("override", override)
            if (detected != null && override != null && !detected.equals(override, true)) lines += text("mismatch")
            else country = override ?: detected
        }
        if (id == "Q08" && country in listOf("RO", "DE", "AT"))
            field("service", if (country == "RO") "SMURD" else "Rettungsdienst")
        if (id == "Q30") field("target", target.trim().ifEmpty { text("askTarget") })
        lines += ""
        lines += text("rules")
        item?.let { question ->
            val category = categories.single { it.getString("id") == question.getString("category") }
            lines += local(category, "rules")
        }
        for ((key, value) in listOf("notes" to observation, "earth" to excerpt)) {
            if (value.isNotBlank()) { lines += ""; field(key, value.trim()) }
        }
        return lines.joinToString("\n")
    }
}
