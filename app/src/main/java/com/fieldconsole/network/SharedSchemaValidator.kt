package com.fieldconsole.network

import com.google.gson.*
import java.time.OffsetDateTime

/** Executes the bounded keyword subset used by the bundled FIC v1 schemas.
 * Schemas are trusted application resources, never supplied by imported files.
 */
internal object SharedSchemaValidator {
    private val schemas = mutableMapOf<String, JsonObject>()
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})")
    @Synchronized private fun schema(name: String) = schemas.getOrPut(name) {
        SharedSchemaValidator::class.java.getResourceAsStream("/$name")!!.bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
    }
    fun network(value: JsonObject) = validate(value, schema("network-v1.schema.json"))
    fun entity(value: JsonObject) = validate(value, schema("network-v1.schema.json")
        .getAsJsonObject("properties").getAsJsonObject("entities").getAsJsonObject("items"))
    fun report(value: JsonObject) = validate(value, schema("field-report-v1.schema.json"))
    private fun validDate(value: String): Boolean = try {
        datePattern.matches(value) && OffsetDateTime.parse(value).let { true }
    } catch (_: Exception) { false }
    private fun check(ok: Boolean) { require(ok) { "Invalid or unsupported FIC data." } }

    private fun validate(value: JsonElement, schema: JsonObject, depth: Int = 0) {
        check(depth <= 32)
        check(schema.keySet().all { it in setOf("\$schema", "\$id", "type", "properties", "required", "additionalProperties",
            "items", "maxItems", "minItems", "maxLength", "minLength", "minimum", "maximum", "const", "enum", "anyOf", "format") })
        schema["anyOf"]?.asJsonArray?.let { alternatives ->
            check(alternatives.any { try { validate(value,it.asJsonObject,depth); true } catch (_:IllegalArgumentException) { false } })
        }
        schema["const"]?.let { check(value == it) }
        schema["enum"]?.asJsonArray?.let { values -> check(values.any { it == value }) }
        schema["type"]?.asString?.let { type ->
            check(when(type) {
                "object" -> value.isJsonObject
                "array" -> value.isJsonArray
                "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                "number" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite()
                "integer" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite() && value.asBigDecimal.stripTrailingZeros().scale() <= 0
                "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                "null" -> value.isJsonNull
                else -> false
            })
        }
        if(value.isJsonObject) {
            val objectValue=value.asJsonObject
            schema["required"]?.asJsonArray?.forEach { check(objectValue.has(it.asString)) }
            val properties=schema["properties"]?.asJsonObject ?: JsonObject()
            objectValue.entrySet().forEach { (key,child) ->
                if(properties.has(key)) validate(child,properties[key].asJsonObject,depth+1)
                else check(schema["additionalProperties"]?.takeIf { it.isJsonPrimitive }?.asBoolean != false)
            }
        }
        if(value.isJsonArray) {
            schema["maxItems"]?.asInt?.let { check(value.asJsonArray.size() <= it) }
            schema["minItems"]?.asInt?.let { check(value.asJsonArray.size() >= it) }
            schema["items"]?.asJsonObject?.let { item -> value.asJsonArray.forEach { validate(it,item,depth+1) } }
        }
        if(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            val text=value.asString; val length=text.codePointCount(0,text.length)
            schema["minLength"]?.asInt?.let { check(length >= it) }
            schema["maxLength"]?.asInt?.let { check(length <= it) }
            schema["format"]?.asString?.let { check(it == "date-time" && validDate(text)) }
        }
        if(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            check(value.asDouble.isFinite())
            schema["minimum"]?.asBigDecimal?.let { check(value.asBigDecimal >= it) }
            schema["maximum"]?.asBigDecimal?.let { check(value.asBigDecimal <= it) }
        }
    }
}
