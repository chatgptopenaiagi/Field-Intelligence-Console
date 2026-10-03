package com.fieldconsole

import com.fieldconsole.network.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class NetworkCodecTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes()
    private fun parse(text: String, format: String = "vcf") = NetworkCodec.parse(text.toByteArray(), format)
    private val card = "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:synthetic\r\nFN:Alex Example\r\nEND:VCARD\r\n"

    @Test fun `shared multivcard preserves Unicode multiple phones addresses and inert properties`() {
        val preview = NetworkCodec.parse(fixture("contacts.vcf"), "vcf")
        assertEquals(2, preview.entities.size); assertTrue(preview.issues.isEmpty())
        val first = preview.entities.first()
        assertEquals("Alex Éxample", first["display_name"].asString)
        assertEquals(2, first.getAsJsonArray("phones").size())
        assertEquals(2, first.getAsJsonArray("addresses").size())
        assertTrue(first.getAsJsonObject("extensions").getAsJsonArray("vcard_properties").toString().contains("X-SYNTHETIC"))
        first.getAsJsonArray("associations").forEach { assertTrue(it.asJsonObject["point"].isJsonNull) }
        assertEquals(preview.entities.map { it["id"] }, NetworkCodec.parse(fixture("contacts.vcf"), "vcf").entities.map { it["id"] })
    }
    @Test fun `folding escaping grouped properties and quoted labels parse safely`() {
        val preview = parse(card.replace("FN:Alex Example", "FN:Alex\\, Example\r\n continued").replace("END:VCARD", "item1.TEL;TYPE=\"work,voice\":+1 202 555 0100\r\nADR;TYPE=work;LABEL=\"A; B\":;;One\\; Street;City;;;Country\r\nEND:VCARD"))
        assertEquals("Alex, Examplecontinued", preview.entities.single()["display_name"].asString)
        assertEquals("One; Street", preview.entities.single().getAsJsonArray("addresses")[0].asJsonObject["street"].asString)
    }
    @Test fun `byte unfolding repairs legal UTF8 split and source update time stays distinct`() {
        val bytes=card.replace("Alex Example","Éxample").replace("END:VCARD","REV:20260101T120000Z\r\nEND:VCARD").toByteArray()
        val index=bytes.indexOf(0xc3.toByte())
        val folded=bytes.copyOfRange(0,index+1) + "\r\n ".toByteArray() + bytes.copyOfRange(index+1,bytes.size)
        val entity=NetworkCodec.parse(folded,"vcf").entities.single()
        assertEquals("Éxample",entity["display_name"].asString)
        assertEquals("2026-01-01T12:00:00Z",entity.getAsJsonArray("ancestry")[0].asJsonObject["updated_at"].asString)
        assertNotEquals(entity.getAsJsonArray("ancestry")[0].asJsonObject["imported_at"],entity.getAsJsonArray("ancestry")[0].asJsonObject["updated_at"])
    }
    @Test fun `unsupported cards are diagnosed while explicitly selectable valid cards remain`() {
        val preview = parse(card + card.replace("VERSION:4.0", "VERSION:2.1"))
        assertEquals(1, preview.entities.size); assertEquals(1, preview.invalidCount)
        assertFalse(preview.issues.single().contains("Alex"))
    }
    @Test fun `external references remain inert and embedded data is rejected`() {
        val safe = parse(card.replace("END:VCARD", "PHOTO:https://example.org/synthetic.jpg\r\nURL:https://example.org\r\nEND:VCARD"))
        assertEquals(1, safe.entities.size)
        val bad = parse(card.replace("END:VCARD", "PHOTO:data:image/png;base64,AAAA\r\nEND:VCARD"))
        assertEquals(0, bad.entities.size); assertEquals(1, bad.invalidCount)
    }
    @Test fun `invalid UTF8 oversized input property and malformed JSON are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.parse(byteArrayOf(0xc3.toByte(),0x28), "vcf") }
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.parse(ByteArray(NetworkCodec.MAX_BYTES + 1), "vcf") }
        assertThrows(IllegalArgumentException::class.java) { parse(card.replace("FN:Alex Example", "FN:" + "x".repeat(16385))) }
        listOf("{\"x\":NaN}", "{\"x\":1,\"x\":2}", "{\"x\":1} trailing", "{x:1}").forEach {
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.parseJson(it) }
        }
    }
    @Test fun `shared canonical JSON roundtrip retains unknown entity metadata`() {
        val original = NetworkCodec.parse(fixture("network-v1.json"), "json")
        original.entities.first().add("future_metadata", JsonParser.parseString("{\"safe\":[1,null,\"unknown\"]}"))
        val bytes = NetworkCodec.export(original.entities, "json").bytes
        assertEquals(original.entities, NetworkCodec.parse(bytes, "json").entities)
    }
    @Test fun `partially invalid JSON previews only valid records with explicit diagnostics`() {
        val original = NetworkCodec.parseJson(String(fixture("network-v1.json")))
        original.getAsJsonArray("entities").add(JsonObject())
        original.add("unknown_envelope", JsonObject())
        val preview = parse(original.toString(),"json")
        assertTrue(preview.entities.isNotEmpty()); assertEquals(1,preview.invalidCount)
        assertTrue(preview.warnings.single().contains("envelope"))
    }
    @Test fun `production Android codec writes synthetic interoperability artifacts`() {
        val entities = NetworkCodec.parse(fixture("network-v1.json"),"json").entities
        val directory=java.io.File("build/interoperability").apply { mkdirs() }
        java.io.File(directory,"android-network.json").writeBytes(NetworkCodec.export(entities,"json").bytes)
        java.io.File(directory,"android-vcf-network.json").writeBytes(NetworkCodec.export(NetworkCodec.parse(fixture("contacts.vcf"),"vcf").entities,"json").bytes)
        val report=NetworkCodec.parseJson(String(fixture("field-report-v1.json")))
        NetworkCodec.validateReport(report)
        java.io.File(directory,"android-report.json").writeText(report.toString())
        assertEquals(entities,NetworkCodec.parse(java.io.File(directory,"android-network.json").readBytes(),"json").entities)
    }
    @Test fun `schema future versions and live presence are rejected`() {
        val dataset = NetworkCodec.parseJson(String(fixture("network-v1.json")))
        dataset.addProperty("schema_version", 2)
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(dataset) }
        dataset.addProperty("schema_version", 1)
        dataset.getAsJsonArray("entities")[0].asJsonObject.getAsJsonArray("associations")[0].asJsonObject.addProperty("temporal_status", "current")
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateNetwork(dataset) }
    }
    @Test fun `shared CSV and custom column mapping are previewable without mutation`() {
        assertTrue(NetworkCodec.parse(fixture("contacts.csv"), "csv").entities.isNotEmpty())
        val csv = "Name,Telephone\r\n\"Example, Alex\",+12025550100\r\n"
        assertEquals(listOf("Name", "Telephone"), parse(csv, "csv").columns)
        val preview = NetworkCodec.parse(csv.toByteArray(), "csv", mapOf("display_name" to "Name", "phone" to "Telephone"))
        assertEquals("Example, Alex", preview.entities.single()["display_name"].asString)
        assertEquals("+12025550100", preview.entities.single().getAsJsonArray("phones")[0].asJsonObject["value"].asString)
    }
    @Test fun `CSV formulas escaped without changing canonical originals`() {
        val e = NetworkCodec.newEntity("=HYPERLINK(\"https://example.org\")")
        e.addProperty("notes", "\tmalicious")
        val export = NetworkCodec.export(listOf(e), "csv")
        assertTrue(String(export.bytes).contains("'=HYPERLINK")); assertTrue(String(export.bytes).contains("'\tmalicious"))
        assertTrue(export.warnings.size >= 2)
        assertTrue(e["display_name"].asString.startsWith("=")); assertTrue(e["notes"].asString.startsWith("\t"))
    }
    @Test fun `phone normalization preserves explicit prefix and extension boundary without guessing region`() {
        assertEquals("+12025550100;=42",NetworkCodec.normalizePhone("TEL:+1 (202) 555-0100;ext=42"))
        assertEquals("02079460000",NetworkCodec.normalizePhone("020 7946 0000"))
        assertEquals("*123#",NetworkCodec.normalizePhone("*123#"))
    }
    @Test fun `duplicate preview uses ancestry without merging same names`() {
        val preview=NetworkCodec.parse(fixture("contacts.vcf"),"vcf")
        val existing=preview.entities.first().deepCopy().apply { addProperty("id","corrected-local-id") }
        assertEquals(ImportCounts(1,1),NetworkCodec.previewCounts(listOf(existing),preview))
        val sameName=NetworkCodec.newEntity(existing["display_name"].asString)
        assertEquals(ImportCounts(2,0),NetworkCodec.previewCounts(listOf(sameName),preview))
        assertEquals(ImportCounts(0,0),NetworkCodec.previewCounts(listOf(existing),preview,emptySet()))
    }
    @Test fun `vCard deliberate export folds UTF8 and preserves main values`() {
        val e = NetworkCodec.newEntity("É".repeat(100))
        val export = NetworkCodec.export(listOf(e), "vcf")
        assertTrue(export.warnings.isNotEmpty())
        String(export.bytes).split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75) }
        assertEquals(e["display_name"], NetworkCodec.parse(export.bytes, "vcf").entities.single()["display_name"])
    }
    @Test fun `field report retains failure stale offline and empty states without NETWORK`() {
        val report = NetworkCodec.parseJson(String(fixture("field-report-v1.json")))
        NetworkCodec.validateReport(report)
        assertEquals(report, NetworkCodec.parseJson(report.toString()))
        report.add("network_summary", JsonObject())
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(report) }
    }
    @Test fun `coordinate validation rejects nonfinite out of range and one missing component`() {
        val report = NetworkCodec.parseJson(String(fixture("field-report-v1.json")))
        val location = report.getAsJsonObject("location")
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -91.0, 91.0)) {
            location.addProperty("latitude", bad)
            assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(report) }
        }
        location.add("latitude", JsonNull.INSTANCE)
        assertThrows(IllegalArgumentException::class.java) { NetworkCodec.validateReport(report) }
        location.add("longitude", JsonNull.INSTANCE); NetworkCodec.validateReport(report)
    }
}
