package com.fieldconsole.data.emergency

import com.fieldconsole.location.GeoContext
import com.google.gson.Gson
import java.util.Locale
import kotlin.math.floor

data class EmergencyNumber(
    val country: String, val region: String?, val scope: String,
    val category: Category, val number: String, val source: String,
    val verified: String, val status: String
) {
    enum class Category { POLICE, FIRE, AMBULANCE, MEDICAL_ADVICE, POISON, GENERAL }
}

object EmergencyNumbers {
    private data class Catalog(val version: Int, val records: List<EmergencyNumber>)
    private val records by lazy {
        requireNotNull(javaClass.getResourceAsStream("/emergency-v1.json")).bufferedReader().use {
            Gson().fromJson(it, Catalog::class.java).also { catalog -> require(catalog.version == 1) }.records
        }
    }
    fun forCountry(code: String): List<EmergencyNumber> = records.filter {
        it.country == code.uppercase(Locale.ROOT) && it.status == "verified" &&
            it.scope == "national" && it.region == null
    }
}

object CountryBoundary {
    private data class Span(val start: Int, val stop: Int, val iso: String)
    private val rows by lazy {
        requireNotNull(javaClass.getResourceAsStream("/country-grid-v1.json")).bufferedReader().use { reader ->
            com.google.gson.JsonParser.parseReader(reader).asJsonArray.map { row ->
                row.asJsonArray.map { item ->
                    val span = item.asJsonArray
                    Span(span[0].asInt, span[1].asInt, span[2].asString)
                }
            }
        }
    }
    fun interior(lat: Double, lon: Double): String? {
        if (!(lat >= -90 && lat < 90 && lon >= -180 && lon < 180)) return null
        val col = floor((lon + 180) * 5).toInt()
        return rows.getOrNull(floor((lat + 90) * 5).toInt())?.firstOrNull { col in it.start until it.stop }?.iso
    }

    fun current(geo: GeoContext?, now: Long): String? {
        if (geo == null || geo.manual || geo.saved || geo.timestamp <= 0 || geo.timestamp > now ||
            now - geo.timestamp > 120_000 || !(geo.accuracy > 0 && geo.accuracy <= 1000)) return null
        val code = interior(geo.latitude, geo.longitude) ?: return null
        return code.takeIf { geo.countryCode.isNullOrBlank() || it.equals(geo.countryCode, true) }
    }
}
