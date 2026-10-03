package com.fieldconsole

import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.export.ReportExchange
import com.fieldconsole.location.GeoContext
import com.fieldconsole.network.NetworkCodec
import org.junit.Assert.*
import org.junit.Test

class ReportExchangeTest {
    @Test fun `ordinary report preserves observation and coordinate provenance without network fields`() {
        val report = ReportExchange.fromEntry(FieldEntry(id = 1, latitude = 0.0, longitude = -179.9,
            accuracy = 8f, note = "Synthetic note", tag = "test", timestamp = 1000),
            GeoContext(latitude = 0.0, longitude = -179.9, geocodingNotice = "Address lookup failed."))
        val reopened = ReportExchange.decode(ReportExchange.encode(report))
        assertEquals(report, reopened)
        assertEquals(0.0, reopened.getAsJsonObject("location").get("latitude").asDouble, 0.0)
        assertTrue(reopened.getAsJsonObject("location").get("address").isJsonNull)
        assertEquals("failure", reopened.getAsJsonArray("providers")[0].asJsonObject.get("state").asString)
        assertTrue(reopened.get("ai_advisory").isJsonNull)
        assertFalse(reopened.has("network")); assertFalse(reopened.has("network_summary")); assertFalse(reopened.has("entities"))
    }
    @Test fun `shared report states roundtrip without converting missing values or stale into fresh`() {
            // Exercise each explicit state using the same shared canonical report.
            val base = javaClass.getResource("/fixtures/field-report-v1.json") ?: error("Shared report fixture missing")
            val report = NetworkCodec.parseJson(base.readText())
            for (state in listOf("offline", "failure", "empty", "success")) {
                val copy = report.deepCopy()
                copy.getAsJsonArray("providers").forEach { it.asJsonObject.addProperty("state", state); it.asJsonObject.addProperty("freshness", if (state == "success") "stale" else "unknown") }
                assertEquals(copy, ReportExchange.decode(ReportExchange.encode(copy)))
            }
    }
    @Test fun `invalid coordinates and encoding fail safely`() {
        assertThrows(IllegalArgumentException::class.java) { ReportExchange.fromEntry(FieldEntry(latitude = Double.NaN), GeoContext()) }
        assertThrows(Exception::class.java) { ReportExchange.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }
}
