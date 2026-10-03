package com.fieldconsole.network

import com.fieldconsole.BuildConfig
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Bounded offline parsers. Reference properties are inert text, never fetched. */
object NetworkCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    val csvColumns = listOf("display_name", "kind", "organization", "role", "phone", "email", "street",
        "neighborhood", "locality", "region", "postal_code", "country", "label", "notes")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun hash(value: String) = hash(value.toByteArray(Charsets.UTF_8))
    fun normalizePhone(value:String):String = (if(value.startsWith("tel:",ignoreCase=true)) value.drop(4) else value)
        .filter { it.isDigit() || it in "+;=#*" }
    internal fun freshRecords(existing:List<JsonObject>, selected:List<JsonObject>):List<JsonObject> {
        fun keys(entity:JsonObject)=entity.objects("ancestry").map { it.text("source_id") + "\u0000" + it.text("record_id") }
        val ids=existing.map { it.text("id") }.toMutableSet()
        val ancestry=existing.flatMap { keys(it) }.toMutableSet()
        return selected.filter { entity ->
            val sourceKeys=keys(entity)
            if(entity.text("id") in ids || sourceKeys.any { it in ancestry }) false
            else { ids.add(entity.text("id")); ancestry.addAll(sourceKeys); true }
        }
    }
    fun previewCounts(existing:List<JsonObject>, preview:NetworkPreview,
                      selectedIds:Set<String> = preview.entities.map { it.text("id") }.toSet()):ImportCounts {
        require(selectedIds.all { id -> preview.entities.any { it.text("id")==id } }) { "Selection does not belong to preview." }
        val selected=preview.entities.filter { it.text("id") in selectedIds }
        val fresh=freshRecords(existing,selected)
        return ImportCounts(fresh.size,selected.size-fresh.size)
    }
    fun newEntity(name: String, kind: String = "person"): JsonObject = obj(
        "id" to UUID.randomUUID().toString(), "kind" to kind, "display_name" to name,
        "structured_name" to obj("family" to "", "given" to "", "additional" to "", "prefix" to "", "suffix" to ""),
        "organization" to "", "role" to "", "notes" to "", "tags" to JsonArray(),
        "phones" to JsonArray(), "emails" to JsonArray(), "addresses" to JsonArray(),
        "associations" to JsonArray(), "ancestry" to JsonArray(), "corrections" to JsonArray(), "extensions" to JsonObject())

    fun validateNetwork(value: JsonObject) = safe { NetworkValidation.network(value) }
    fun validateReport(value: JsonObject) = safe { NetworkValidation.report(value) }
    internal fun validateEntity(value: JsonObject) = safe { NetworkValidation.entity(value) }
    private fun <T> safe(block: () -> T): T = try { block() } catch (_: Exception) {
        throw IllegalArgumentException("Invalid or unsupported FIC data. No records were changed.")
    }
    fun dataset(entities: List<JsonObject>) = obj("schema" to "fic.network", "schema_version" to 1,
        "app_version" to BuildConfig.VERSION_NAME, "exported_at" to Instant.now().toString(), "entities" to array(entities))

    fun parse(bytes: ByteArray, format: String, mapping: Map<String, String> = emptyMap()): NetworkPreview {
        require(bytes.size <= MAX_BYTES) { "Input exceeds the 5 MiB limit." }
        val decodedBytes = if (format.lowercase() in setOf("vcf", "vcard")) unfoldBytes(bytes) else bytes
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decodedBytes)).toString().removePrefix("\uFEFF") }
        catch (_: Exception) { throw IllegalArgumentException("Input must be valid UTF-8.") }
        val source = hash(bytes)
        return when (format.lowercase()) {
            "json", "fic" -> {
                val data = parseJson(text)
                require(data["entities"]?.isJsonArray == true) { "Network records missing." }
                val envelope = data.deepCopy().apply { add("entities", JsonArray()) }
                validateNetwork(envelope)
                val issues = mutableListOf<String>(); val accepted = mutableListOf<JsonObject>(); val ids = mutableSetOf<String>()
                data.getAsJsonArray("entities").forEachIndexed { index, value ->
                    try {
                        val entity = value.asJsonObject
                        validateEntity(entity)
                        require(ids.add(entity.text("id")))
                        accepted.add(entity.deepCopy())
                    } catch (_: Exception) { issues.add("Record ${index + 1}: invalid or duplicate FIC record; not selected for import.") }
                }
                val unknownEnvelope = data.keySet() - setOf("schema", "schema_version", "app_version", "exported_at", "entities")
                NetworkPreview(accepted, issues, source,
                    warnings = if (unknownEnvelope.isEmpty()) emptyList() else listOf("Dataset envelope extensions are not stored as contact fields; retain the original canonical file if needed. Entity extensions are retained."))
            }
            "vcf", "vcard" -> parseVcf(text, source)
            "csv" -> parseCsv(text, source, mapping)
            else -> throw IllegalArgumentException("Unsupported import format.")
        }
    }
    private fun unfoldBytes(bytes: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream(bytes.size); var i = 0
        while (i < bytes.size) {
            val newline = if (bytes[i] == 13.toByte() && i + 1 < bytes.size && bytes[i + 1] == 10.toByte()) 2
                else if (bytes[i] == 10.toByte()) 1 else 0
            if (newline > 0 && i + newline < bytes.size && bytes[i + newline] in listOf(32.toByte(),9.toByte())) i += newline + 1
            else output.write(bytes[i++].toInt())
        }
        return output.toByteArray()
    }

    /** Strict JSON reader rejects trailing content, excessive depth and nonfinite numbers. */
    fun parseJson(text: String): JsonObject = safe {
        require(text.toByteArray().size <= MAX_BYTES)
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val result = readJson(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            result.asJsonObject.also { NetworkValidation.tree(it) }
        }
    }
    private fun readJson(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 32)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                var count = 0
                while (reader.hasNext()) { require(++count <= 5000); val key = reader.nextName(); require(!has(key)); add(key, readJson(reader, depth + 1)) }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray(); while (reader.hasNext()) { require(size() < 5000); add(readJson(reader, depth + 1)) }; reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> { val raw = reader.nextString(); require(raw.toDouble().isFinite()); JsonPrimitive(raw.toBigDecimal()) }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> throw IllegalArgumentException("Malformed JSON.")
        }
    }

    private fun ancestry(entity: JsonObject, source: String, record: String, format: String) {
        entity.addProperty("id", hash("$source:$record"))
        entity.getAsJsonArray("ancestry").add(obj("source_id" to source, "record_id" to record,
            "format" to format, "imported_at" to Instant.now().toString(), "updated_at" to null))
    }
    private fun value(entity: JsonObject, key: String, raw: String, label: String) {
        val normalized = if (key == "phones") normalizePhone(raw)
            else raw.removePrefix("mailto:").trim().lowercase()
        entity.getAsJsonArray(key).add(obj("value" to raw, "normalized" to normalized, "label" to label))
    }
    private fun address(entity: JsonObject, components: List<String>, label: String, neighborhood: String = "", original: String? = null) {
        val index = entity.getAsJsonArray("addresses").size()
        val id = entity.text("id") + "-address-$index"
        val c = (components + List(7) { "" }).take(7)
        entity.getAsJsonArray("addresses").add(obj("id" to id, "original" to (original ?: c.filter { it.isNotBlank() }.joinToString(", ")),
            "label" to label, "po_box" to c[0], "extended" to c[1], "street" to c[2], "neighborhood" to neighborhood,
            "locality" to c[3], "region" to c[4], "postal_code" to c[5], "country" to c[6]))
        entity.getAsJsonArray("associations").add(obj("id" to entity.text("id") + "-association-$index", "address_id" to id,
            "basis" to "saved_address", "source" to "imported_contact", "temporal_status" to "saved", "point" to null,
            "geocode_status" to "unresolved", "provider" to null, "resolved_at" to null, "verified_at" to null, "normalized_address" to null))
    }

    private fun splitEscaped(value: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>(); val current = StringBuilder(); var escaped = false
        for (c in value) {
            if (escaped) { current.append('\\').append(c); escaped = false }
            else if (c == '\\') escaped = true
            else if (c == delimiter) { result.add(current.toString()); current.clear() }
            else current.append(c)
        }
        require(!escaped); result.add(current.toString()); return result
    }
    private fun unescape(value: String): String {
        val out = StringBuilder(); var escaped = false
        value.forEach { c ->
            if (escaped) { require(c in listOf('n','N',',',';','\\')); out.append(if (c == 'n' || c == 'N') '\n' else c); escaped = false }
            else if (c == '\\') escaped = true else out.append(c)
        }
        require(!escaped); return out.toString()
    }
    private fun parameterText(value: String): String {
        val out=StringBuilder(); var i=0
        while(i<value.length) {
            val c=value[i++]
            if(c=='^' && i<value.length && value[i] in listOf('n','N','\'','^'))
                out.append(when(val next=value[i++]) { 'n','N' -> '\n'; '\'' -> '"'; else -> next })
            else out.append(c)
        }
        return out.toString()
    }
    private fun parseVcf(text: String, source: String): NetworkPreview {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if (line.startsWith(" ") || line.startsWith("\t")) {
                require(lines.isNotEmpty()) { "Invalid vCard continuation." }; lines[lines.lastIndex] += line.drop(1)
            } else if (line.isNotEmpty()) lines.add(line)
            require((lines.lastOrNull()?.toByteArray()?.size ?: 0) <= 16384) { "vCard property exceeds size limit." }
        }
        val entities = mutableListOf<JsonObject>(); val issues = mutableListOf<String>(); var card: MutableList<String>? = null; var number = 0
        for (line in lines) {
            when (line.uppercase()) {
                "BEGIN:VCARD" -> { require(card == null) { "Nested vCard." }; require(++number <= 5000); card = mutableListOf(line) }
                "END:VCARD" -> {
                    val cardLines = card ?: throw IllegalArgumentException("Unexpected vCard end.")
                    cardLines.add(line)
                    try { entities.add(parseCard(cardLines, source)) }
                    catch (_: Exception) { issues.add("Record $number: malformed or unsupported vCard (3.0/4.0 UTF-8 text only).") }
                    card = null
                }
                else -> { require(card != null) { "Text outside vCard." }; require(card.size < 1000); card.add(line) }
            }
        }
        if (card != null) issues.add("Record $number: incomplete vCard.")
        require(number > 0) { "No vCards found." }
        return NetworkPreview(entities.distinctBy { it.text("id") }, issues, source,
            warnings = listOf("Unknown vCard properties are retained as inert text; remote references are never fetched. VCF export is not lossless."))
    }
    private fun parseCard(lines: List<String>, source: String): JsonObject {
        data class Property(val name: String, val params: Map<String,String>, val raw: String, val line: String)
        val properties = lines.drop(1).dropLast(1).map { line ->
            var quote = false; var colon = -1
            for (i in line.indices) { if (line[i] == '"') quote = !quote; if (line[i] == ':' && !quote) { colon = i; break } }
            require(colon > 0 && !quote)
            val header = mutableListOf<String>(); val parameter = StringBuilder(); var parameterQuote = false
            line.substring(0, colon).forEach { c ->
                if (c == '"') parameterQuote = !parameterQuote
                if (c == ';' && !parameterQuote) { header.add(parameter.toString()); parameter.clear() }
                else parameter.append(c)
            }
            header.add(parameter.toString())
            val params = header.drop(1).associate { item ->
                val parts = item.split('=', limit = 2); require(parts.size == 2)
                parts[0].uppercase() to parameterText(parts[1].trim('"'))
            }
            require(params["ENCODING"] == null && params["CHARSET"]?.uppercase() in listOf(null, "UTF-8", "UTF8"))
            val name=header[0].substringAfterLast('.').uppercase()
            require(name.isNotEmpty() && name.all { it in 'A'..'Z' || it in '0'..'9' || it=='-' })
            Property(name, params, line.substring(colon + 1), line)
        }
        require(properties.singleOrNull { it.name == "VERSION" }?.raw in setOf("3.0", "4.0"))
        require(properties.count { it.name == "FN" } == 1 && properties.count { it.name == "UID" } <= 1)
        val entity = newEntity(unescape(properties.firstOrNull { it.name == "FN" }?.raw ?: ""))
        require(entity.text("display_name").isNotBlank())
        var record = properties.firstOrNull { it.name == "UID" }?.raw?.takeIf { it.isNotEmpty() }?.let { unescape(it) }
            ?: hash(lines.drop(1).dropLast(1).joinToString("\n"))
        if (record.length > 256) record = hash(record)
        ancestry(entity, source, record, "vcf")
        val unknown = JsonArray()
        properties.forEach { p ->
            val types=p.params["TYPE"]?.lowercase()?.split(',') ?: emptyList()
            val label=listOf("home","work","organization").firstOrNull { it in types } ?: types.joinToString(",").ifBlank { "unspecified" }
            when (p.name) {
                "VERSION", "UID", "FN" -> Unit
                "KIND" -> { require(p.raw in setOf("individual", "org")); if (p.raw == "org") entity.addProperty("kind", "organization") }
                "N" -> { val parts=splitEscaped(p.raw,';'); require(parts.size == 5); parts.forEachIndexed { i, v -> entity.getAsJsonObject("structured_name").addProperty(listOf("family","given","additional","prefix","suffix")[i], unescape(v)) } }
                "ORG" -> entity.addProperty("organization", splitEscaped(p.raw, ';').joinToString(" / ") { unescape(it) })
                "ROLE", "TITLE" -> entity.addProperty("role",unescape(p.raw))
                "NOTE" -> entity.addProperty("notes",listOf(entity.text("notes"),unescape(p.raw)).filter { it.isNotEmpty() }.joinToString("\n"))
                "CATEGORIES" -> splitEscaped(p.raw,',').forEach { entity.getAsJsonArray("tags").add(unescape(it)) }
                "TEL" -> value(entity, "phones", unescape(p.raw), label)
                "EMAIL" -> value(entity, "emails", unescape(p.raw), label)
                "ADR" -> { val parts=splitEscaped(p.raw,';'); require(parts.size == 7); address(entity,parts.map { unescape(it) },label,original=p.params["LABEL"]) }
                "REV" -> {
                    val date = try { java.time.OffsetDateTime.parse(p.raw).toInstant().toString() }
                    catch (_: Exception) { try { java.time.OffsetDateTime.parse(p.raw,java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")).toInstant().toString() } catch (_: Exception) { null } }
                    if (date == null) unknown.add(p.line) else entity.getAsJsonArray("ancestry")[0].asJsonObject.addProperty("updated_at",date)
                }
                else -> { require(!p.raw.startsWith("data:", ignoreCase = true)); unknown.add(p.line) }
            }
        }
        entity.getAsJsonObject("extensions").add("vcard_properties", unknown)
        validateEntity(entity); return entity
    }

    private fun rows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val field = StringBuilder()
        var quoted = false; var endedQuote = false; var i = 0
        fun finishField() { row.add(field.toString()); field.clear(); endedQuote = false }
        fun finishRow() { finishField(); rows.add(row); require(rows.size <= 5001); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') { if (i < text.length && text[i] == '"') { field.append('"'); i++ } else { quoted = false; endedQuote = true } }
                else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !endedQuote); quoted = true }
                ',' -> finishField()
                '\n' -> finishRow()
                '\r' -> { if (i < text.length && text[i] == '\n') i++; finishRow() }
                else -> { require(!endedQuote); field.append(c) }
            }
            require(field.length <= 16384 && row.size <= 1000)
        }
        require(!quoted)
        if (field.isNotEmpty() || row.isNotEmpty() || endedQuote) finishRow()
        return rows
    }
    private fun parseCsv(text: String, source: String, mapping: Map<String, String>): NetworkPreview {
        val rows = try { rows(text) } catch (_: Exception) { throw IllegalArgumentException("Malformed or oversized CSV.") }
        require(rows.isNotEmpty()) { "CSV header missing." }
        val header = rows.first(); require(header.distinct().size == header.size) { "Duplicate CSV columns." }
        val issues = mutableListOf<String>(); val entities = mutableListOf<JsonObject>()
        rows.drop(1).forEachIndexed { index, row ->
            try {
                require(row.size == header.size)
                fun get(key: String): String = header.indexOf(mapping[key] ?: key).let { if (it >= 0) row[it] else "" }
                val e = newEntity(get("display_name"), get("kind").ifBlank { "person" })
                require(e.text("display_name").isNotBlank() || get("phone").isNotBlank() || get("email").isNotBlank() || get("organization").isNotBlank())
                ancestry(e, source, (index + 1).toString(), "csv")
                listOf("organization", "role", "notes").forEach { e.addProperty(it, get(it)) }
                if (get("phone").isNotBlank()) value(e, "phones", get("phone"), get("label"))
                if (get("email").isNotBlank()) value(e, "emails", get("email"), get("label"))
                if (listOf("street", "locality", "region", "country").any { get(it).isNotBlank() })
                    address(e, listOf("", "", get("street"), get("locality"), get("region"), get("postal_code"), get("country")), get("label"), get("neighborhood"))
                val consumed=csvColumns.map { mapping[it] ?: it }.toSet()
                val unmapped=JsonObject()
                header.forEachIndexed { columnIndex,column -> if(column !in consumed) unmapped.addProperty(column,row[columnIndex]) }
                if(unmapped.size()>0) e.getAsJsonObject("extensions").add("csv_unmapped",unmapped)
                validateEntity(e); entities.add(e)
            } catch (_: Exception) { issues.add("Record ${index + 1}: invalid CSV record; not selected for import.") }
        }
        return NetworkPreview(entities, issues, source, header)
    }

    fun export(entities: List<JsonObject>, format: String): NetworkExport {
        val data = dataset(entities); validateNetwork(data)
        val warnings = listOf("This format cannot retain FIC ancestry, geographic associations, corrections, tags and unknown JSON fields. Keep FIC JSON for full fidelity.")
        val text = when (format.lowercase()) {
            "json", "fic" -> gson.toJson(data)
            "csv" -> {
                fun cell(v: String): String {
                    val dangerous = listOf('=', '+', '-', '@', '\t', '\r', '\n')
                    val safe = if (v.firstOrNull() in dangerous || v.trimStart().firstOrNull() in dangerous) "'$v" else v
                    return "\"" + safe.replace("\"", "\"\"") + "\""
                }
                (listOf(csvColumns) + entities.map { e ->
                    val a = e.objects("addresses").firstOrNull() ?: JsonObject()
                    csvColumns.map { key -> when (key) {
                        "phone" -> e.objects("phones").firstOrNull()?.text("value") ?: ""
                        "email" -> e.objects("emails").firstOrNull()?.text("value") ?: ""
                        "street", "neighborhood", "locality", "region", "postal_code", "country", "label" -> a.text(key)
                        else -> e.text(key)
                    } }
                }).joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } } + "\r\n"
            }
            "vcf", "vcard" -> entities.joinToString("") { e ->
                fun escaped(v: String) = v.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
                fun label(v: String) = v.filter { it.isLetterOrDigit() || it in "-," }.ifBlank { "other" }
                val lines = mutableListOf("BEGIN:VCARD", "VERSION:4.0", "UID:${escaped(e.text("id"))}", "FN:${escaped(e.text("display_name"))}")
                if (e.text("kind") == "organization") lines.add("KIND:org")
                val n = e.getAsJsonObject("structured_name")
                lines.add("N:" + listOf("family", "given", "additional", "prefix", "suffix").joinToString(";") { escaped(n.text(it)) })
                listOf("ORG" to "organization", "ROLE" to "role", "NOTE" to "notes").forEach { (property, key) -> if (e.text(key).isNotBlank()) lines.add("$property:${escaped(e.text(key))}") }
                for ((key, property) in listOf("phones" to "TEL", "emails" to "EMAIL")) e.objects(key).forEach {
                    val valueType=if(property=="TEL" && !it.text("value").startsWith("tel:",ignoreCase=true)) ";VALUE=text" else ""
                    lines.add("$property$valueType;TYPE=${label(it.text("label"))}:${escaped(it.text("value"))}")
                }
                e.objects("addresses").forEach { a -> lines.add("ADR;TYPE=${label(a.text("label"))}:" + listOf("po_box", "extended", "street", "locality", "region", "postal_code", "country").joinToString(";") { escaped(a.text(it)) }) }
                lines.add("END:VCARD")
                lines.joinToString("\r\n") { fold(it) } + "\r\n"
            }
            else -> throw IllegalArgumentException("Unsupported export format.")
        }
        val bytes = text.toByteArray(); require(bytes.size <= MAX_BYTES) { "Export exceeds 5 MiB; select fewer records." }
        return NetworkExport(bytes, if (format.lowercase() in setOf("json", "fic")) emptyList() else warnings + if (format.lowercase() == "csv") listOf("CSV retains only the first phone, email and address; formula-leading values are apostrophe-prefixed.") else emptyList())
    }
    private fun fold(line: String): String {
        val out = StringBuilder(); var length = 0
        line.codePoints().forEach { cp ->
            val chars = String(Character.toChars(cp)); val count = chars.toByteArray().size
            if (length + count > 75) { out.append("\r\n "); length = 1 }
            out.append(chars); length += count
        }
        return out.toString()
    }
}
