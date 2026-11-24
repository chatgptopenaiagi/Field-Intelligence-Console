package com.fieldconsole.export

import com.fieldconsole.BuildConfig
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.location.GeoContext
import com.fieldconsole.network.NetworkCodec
import com.google.gson.*
import java.time.Instant

/** Allowlisted field-only exchange. No dependency on NETWORK or contact storage. */
object ReportExchange {
    fun fromEntry(entry: FieldEntry, geo: GeoContext): JsonObject = JsonObject().apply {
        addProperty("schema", "fic.field-report"); addProperty("schema_version", 1)
        addProperty("app_version", BuildConfig.VERSION_NAME)
        addProperty("id", "android-observation-${entry.id}-${entry.timestamp}")
        addProperty("observed_at", Instant.ofEpochMilli(entry.timestamp).toString())
        add("location", JsonObject().apply {
            addProperty("latitude", entry.latitude); addProperty("longitude", entry.longitude)
            if (entry.accuracy.isFinite() && entry.accuracy > 0) addProperty("accuracy_m", entry.accuracy)
            else add("accuracy_m", JsonNull.INSTANCE)
            addProperty("source", if (geo.manual) "manual" else "device")
            if (hasResolvedAddress(geo)) addProperty("address", geo.fullPlaceName) else add("address", JsonNull.INSTANCE)
            addProperty("address_source", if (get("address").isJsonNull) "unknown" else "device_geocoder")
            // The existing geocoder has no separate resolution timestamp. Never invent it.
            add("resolved_at", JsonNull.INSTANCE)
        })
        add("observation", JsonObject().apply { addProperty("note", entry.note); addProperty("tag", entry.tag) })
        add("providers", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("provider", "device_geocoder")
                addProperty("state", if (geo.geocodingNotice != null) "failure" else if (!hasResolvedAddress(geo)) "unavailable" else "success")
                addProperty("freshness", "unknown"); add("fetched_at", JsonNull.INSTANCE); add("data", JsonNull.INSTANCE)
                if (geo.geocodingNotice == null) add("error", JsonNull.INSTANCE) else addProperty("error", geo.geocodingNotice)
            })
        })
        add("ai_advisory", JsonNull.INSTANCE)
        add("extensions", JsonObject().apply {
            add("fic_observation", JsonObject().apply {
                addProperty("priority", entry.priority); addProperty("mission_id", entry.missionId)
                addProperty("operator", entry.operator); addProperty("favorite", entry.isFavorite)
            })
        })
        NetworkCodec.validateReport(this)
    }

    private fun hasResolvedAddress(geo: GeoContext): Boolean =
        listOf(geo.street, geo.district, geo.city, geo.country).any { !it.isNullOrBlank() }
    fun encode(report: JsonObject): ByteArray {
        NetworkCodec.validateReport(report)
        return GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report).toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes: ByteArray): JsonObject {
        require(bytes.size <= NetworkCodec.MAX_BYTES)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        return NetworkCodec.parseJson(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()).also(NetworkCodec::validateReport)
    }
}
