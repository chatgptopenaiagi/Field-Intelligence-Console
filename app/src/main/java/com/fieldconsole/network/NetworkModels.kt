package com.fieldconsole.network

import com.google.gson.*
import java.time.OffsetDateTime

data class NetworkPreview(val entities: List<JsonObject>, val issues: List<String>, val sourceId: String,
                          val columns: List<String> = emptyList(), val invalidCount: Int = issues.size,
                          val warnings: List<String> = emptyList())
data class NetworkExport(val bytes: ByteArray, val warnings: List<String>)
data class ImportCounts(val inserted: Int, val duplicates: Int)
data class NetworkQuery(val state: String, val matches: List<JsonObject>, val excluded: Int, val explanation: String)

internal fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString ?: ""
internal fun JsonObject.objects(key: String): List<JsonObject> = getAsJsonArray(key)?.map { it.asJsonObject } ?: emptyList()
internal fun obj(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
    for ((key, value) in values) when (value) {
        null -> add(key, JsonNull.INSTANCE)
        is JsonElement -> add(key, value)
        is Number -> addProperty(key, value)
        is Boolean -> addProperty(key, value)
        else -> addProperty(key, value.toString())
    }
}
internal fun array(values: Iterable<JsonElement>) = JsonArray().apply { values.forEach { add(it) } }

/** Structural validation is independent of Android and never runs imported content. */
internal object NetworkValidation {
    private fun check(ok: Boolean) { require(ok) { "Invalid or unsupported FIC data." } }
    private fun required(o: JsonObject, vararg keys: String) { check(keys.all { o.has(it) }) }
    private fun string(o: JsonObject, key: String) { check(o.has(key) && o[key].isJsonPrimitive && o[key].asJsonPrimitive.isString) }
    private fun date(value: JsonElement?) {
        if (value == null || value.isJsonNull) return
        try { OffsetDateTime.parse(value.asString) } catch (_: Exception) { check(false) }
    }
    private fun encodedBound(value:JsonObject) {
        val encoder=Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        check(encoder.encode(java.nio.CharBuffer.wrap(value.toString())).remaining() <= NetworkCodec.MAX_BYTES)
    }
    fun usablePoint(association:JsonObject):Boolean = association["point"]?.isJsonObject == true &&
        (association.text("geocode_status") == "resolved" ||
            (association.text("geocode_status") == "unresolved" && association.text("basis") == "manual_location" &&
                association.text("source") == "user_observation" && association["verified_at"]?.takeUnless { it.isJsonNull } != null))
    fun tree(value: JsonElement, depth: Int = 0) {
        check(depth <= 32)
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, item) ->
                check(key.length <= 16384)
                check(key.lowercase() !in setOf("live_location", "current_presence", "live_tracking", "presence"))
                tree(item, depth + 1)
            }
            value.isJsonArray -> { check(value.asJsonArray.size() <= 5000); value.asJsonArray.forEach { tree(it, depth + 1) } }
            value.isJsonPrimitive -> {
                val p = value.asJsonPrimitive
                if (p.isNumber) check(p.asDouble.isFinite())
                if (p.isString) check(p.asString.codePointCount(0,p.asString.length) <= 16384)
            }
        }
    }
    fun point(p: JsonObject) {
        val lat = p["latitude"]?.takeUnless { it.isJsonNull }?.asDouble
        val lon = p["longitude"]?.takeUnless { it.isJsonNull }?.asDouble
        check(lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
        check(p.text("precision") in setOf("building", "street", "locality", "region", "country", "approximate"))
    }
    fun entity(e: JsonObject) {
        tree(e)
        encodedBound(e)
        SharedSchemaValidator.entity(e)
        string(e, "id"); check(e.text("id").isNotBlank()); string(e, "display_name")
        check(e.text("kind") in setOf("person", "organization"))
        for (key in listOf("organization", "role", "notes")) string(e, key)
        for (key in listOf("phones", "emails", "addresses", "associations", "ancestry", "tags", "corrections")) check(e[key]?.isJsonArray == true)
        check(e["structured_name"]?.isJsonObject == true && e["extensions"]?.isJsonObject == true)
        listOf("family", "given", "additional", "prefix", "suffix").forEach { string(e.getAsJsonObject("structured_name"),it) }
        check(e.text("id").length <= 256)
        e.getAsJsonArray("tags").forEach { check(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
        e.getAsJsonArray("corrections").forEach { check(it.isJsonObject) }
        for (key in listOf("phones", "emails")) for (entry in e.objects(key)) {
            string(entry, "value"); string(entry, "normalized"); string(entry, "label")
        }
        e.objects("addresses").forEach { a ->
            for (key in listOf("id", "original", "label", "po_box", "extended", "street", "neighborhood", "locality", "region", "postal_code", "country")) string(a, key)
        }
        val addressIds=e.objects("addresses").map { it.text("id") }
        check(addressIds.distinct().size == addressIds.size)
        val associationIds=e.objects("associations").map { it.text("id") }
        check(associationIds.distinct().size == associationIds.size)
        e.objects("associations").forEach { a ->
            required(a,"address_id","point","provider","resolved_at","verified_at","normalized_address")
            string(a, "id")
            check(a.text("basis") in setOf("saved_address", "manual_location", "historical_observation"))
            check(a.text("source") in setOf("imported_contact", "user_observation", "external_provider"))
            check(a.text("temporal_status") in setOf("saved", "historical", "unknown"))
            check(a.text("geocode_status") in setOf("unresolved", "resolved", "ambiguous", "failed"))
            a["point"]?.takeUnless { it.isJsonNull }?.let { point(it.asJsonObject) }
            date(a["resolved_at"]); date(a["verified_at"])
            check(a["address_id"].isJsonNull || a.text("address_id") in addressIds)
            check(a["point"].isJsonNull || usablePoint(a))
        }
        e.objects("ancestry").forEach { a ->
            string(a, "source_id"); string(a, "record_id"); string(a, "format")
            required(a,"imported_at","updated_at"); check(!a["imported_at"].isJsonNull); date(a["imported_at"]); date(a["updated_at"])
        }
    }
    fun network(o: JsonObject) {
        tree(o)
        encodedBound(o)
        SharedSchemaValidator.network(o)
        check(o.text("schema") == "fic.network")
        string(o, "app_version"); string(o,"exported_at"); date(o["exported_at"])
        check(o["entities"]?.isJsonArray == true)
        val entities = o.objects("entities")
        entities.forEach { entity(it) }
        check(entities.map { it.text("id") }.distinct().size == entities.size)
    }
    fun report(o: JsonObject) {
        tree(o)
        encodedBound(o)
        SharedSchemaValidator.report(o)
        check(o.text("schema") == "fic.field-report")
        string(o, "app_version"); string(o, "id"); string(o,"observed_at"); date(o["observed_at"])
        required(o,"ai_advisory","extensions"); check(o["extensions"].isJsonObject)
        fun noNetwork(value:JsonElement) {
            if(value.isJsonObject) value.asJsonObject.entrySet().forEach { (key,child) ->
                check(key.lowercase() !in setOf("network","contacts","network_summary","network_count","entities")); noNetwork(child)
            }
            else if(value.isJsonArray) value.asJsonArray.forEach { noNetwork(it) }
        }
        noNetwork(o)
        val location = o.getAsJsonObject("location") ?: throw IllegalArgumentException("Missing report location.")
        required(location,"latitude","longitude","accuracy_m","address","resolved_at")
        val lat = location["latitude"]?.takeUnless { it.isJsonNull }?.asDouble
        val lon = location["longitude"]?.takeUnless { it.isJsonNull }?.asDouble
        check((lat == null && lon == null) || (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0))
        location["accuracy_m"]?.takeUnless { it.isJsonNull }?.let { check(it.asDouble.isFinite() && it.asDouble >= 0) }
        check(location.text("source") in setOf("device", "manual", "report", "unknown"))
        check(location.text("address_source") in setOf("device_geocoder", "user", "external_provider", "unknown"))
        date(location["resolved_at"])
        val observation = o.getAsJsonObject("observation") ?: throw IllegalArgumentException("Missing observation.")
        string(observation, "note"); string(observation, "tag")
        check(o["providers"]?.isJsonArray == true)
        o.objects("providers").forEach { p ->
            required(p,"fetched_at","data","error")
            string(p, "provider")
            check(p.text("state") in setOf("success", "empty", "offline", "failure", "unavailable"))
            check(p.text("freshness") in setOf("fresh", "stale", "unknown")); date(p["fetched_at"])
        }
        o["ai_advisory"].takeUnless { it.isJsonNull }?.asJsonObject?.let {
            string(it,"provider"); string(it,"text"); string(it,"generated_at"); date(it["generated_at"])
        }
    }
}
