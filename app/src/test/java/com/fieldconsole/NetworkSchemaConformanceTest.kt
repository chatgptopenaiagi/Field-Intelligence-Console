package com.fieldconsole

import com.fieldconsole.network.NetworkCodec
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class NetworkSchemaConformanceTest {
    private fun fixture(name:String)=NetworkCodec.parseJson(javaClass.getResource("/fixtures/$name")!!.readText())
    private fun network()=fixture("network-v1.json")
    private fun report()=fixture("field-report-v1.json")
    private fun entity(value:JsonObject)=value.getAsJsonArray("entities")[0].asJsonObject
    private fun association(value:JsonObject)=entity(value).getAsJsonArray("associations")[0].asJsonObject
    @Test fun `shared cross platform invalid vectors all fail strict validation`() {
        val cases=fixture("invalid-contracts.json").getAsJsonArray("cases")
        assertTrue(cases.size() >= 33)
        cases.forEach { item ->
            val test=item.asJsonObject; val name=test["fixture"].asString; val document=fixture(name)
            val path=test.getAsJsonArray("path").toList()
            var parent:JsonElement=document
            path.dropLast(1).forEach { key -> parent=if(parent.isJsonArray) parent.asJsonArray[key.asInt] else parent.asJsonObject[key.asString] }
            val last=path.last()
            if(test["remove"]?.asBoolean == true) parent.asJsonObject.remove(last.asString)
            else if(parent.isJsonArray) parent.asJsonArray.set(last.asInt,test["value"].deepCopy())
            else parent.asJsonObject.add(last.asString,test["value"].deepCopy())
            assertThrows(test["name"].asString,IllegalArgumentException::class.java) {
                if(name.startsWith("network")) NetworkCodec.validateNetwork(document) else NetworkCodec.validateReport(document)
            }
        }
    }
    @Test fun `all required root fields and date time formats are enforced`() {
        val schemas=listOf("network-v1.schema.json" to "network-v1.json", "field-report-v1.schema.json" to "field-report-v1.json")
        for((schemaName,fixtureName) in schemas) {
            val schema=JsonParser.parseString(javaClass.getResource("/schemas/$schemaName")!!.readText()).asJsonObject
            fun validate(value:JsonObject) = if(schemaName.startsWith("network")) NetworkCodec.validateNetwork(value) else NetworkCodec.validateReport(value)
            schema.getAsJsonArray("required").forEach { key ->
                val value=fixture(fixtureName); value.remove(key.asString)
                assertThrows("missing required field ${key.asString}",IllegalArgumentException::class.java) { validate(value) }
            }
            for(version in listOf(JsonPrimitive(1.2),JsonPrimitive("1"),JsonPrimitive(true),JsonNull.INSTANCE)) {
                val value=fixture(fixtureName); value.add("schema_version",version)
                assertThrows(IllegalArgumentException::class.java) { validate(value) }
            }
            val key=if(schemaName.startsWith("network")) "exported_at" else "observed_at"
            for(date in listOf("2026-10-03", "2026-10-03T12:00:00", "2026-10-03T12:00Z", "2026-02-30T12:00:00Z")) {
                val value=fixture(fixtureName); value.addProperty(key,date)
                assertThrows(IllegalArgumentException::class.java) { validate(value) }
            }
        }
    }
    @Test fun `coordinates accuracy and nullable strings never coerce unrelated JSON types`() {
        for(bad in listOf(JsonPrimitive("0"),JsonPrimitive(false),JsonArray(),JsonObject())) {
            val r=report(); r.getAsJsonObject("location").add("latitude",bad)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(r) }
            val n=network(); association(n).getAsJsonObject("point").add("latitude",bad)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(n) }
            val accuracy=report(); accuracy.getAsJsonObject("location").add("accuracy_m",bad)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(accuracy) }
        }
        for(key in listOf("provider","normalized_address","address_id")) {
            val n=network(); association(n).addProperty(key,123)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(n) }
        }
        val r=report(); r.getAsJsonObject("location").addProperty("address",false)
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(r) }
        val p=report(); p.getAsJsonArray("providers")[0].asJsonObject.add("error",JsonObject())
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(p) }
    }
    @Test fun `nested fields IDs collection bounds and date nullability match schema`() {
        for(key in listOf("id","address_id","basis","source","temporal_status","point","geocode_status","provider","resolved_at","verified_at","normalized_address")) {
            val n=network(); association(n).remove(key)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(n) }
        }
        for(id in listOf("", "a".repeat(257))) {
            val n=network(); entity(n).getAsJsonArray("addresses")[0].asJsonObject.addProperty("id",id)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(n) }
        }
        val tags=network(); entity(tags).add("tags",JsonArray().apply { repeat(1001) { add("synthetic") } })
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(tags) }
        val imported=network(); entity(imported).getAsJsonArray("ancestry")[0].asJsonObject.add("imported_at",JsonNull.INSTANCE)
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(imported) }
        val r=report(); r.addProperty("id","")
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(r) }
    }
    @Test fun `nullable unknowns future entity fields and numeric version equivalence remain valid`() {
        val n=network(); n.addProperty("schema_version",1.0)
        association(n).add("point",JsonNull.INSTANCE); association(n).add("address_id",JsonNull.INSTANCE)
        entity(n).add("future",JsonParser.parseString("{\"nested\":[null,true,1,\"x\"]}"))
        NetworkCodec.validateNetwork(n)
        val r=report(); r.getAsJsonObject("location").apply { add("latitude",JsonNull.INSTANCE); add("longitude",JsonNull.INSTANCE) }
        NetworkCodec.validateReport(r)
    }
}
