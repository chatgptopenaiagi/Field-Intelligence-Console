package com.fieldconsole

import com.fieldconsole.network.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class NetworkGeographyTest {
    private fun entities() = NetworkCodec.parse(javaClass.getResourceAsStream("/fixtures/network-v1.json")!!.readBytes(), "json").entities
    private fun place() = JsonParser.parseString("""{"latitude":0,"longitude":0,"street":"1 Example Street","neighborhood":"Example District","locality":"Example City","region":"Example Region","country":"Example Country"}""").asJsonObject
    @Test fun `all supported scopes deduplicate entity counts and never claim presence`() {
        for (scope in listOf("building", "street", "neighborhood", "city", "region", "country", "radius")) {
            val e = entities(); val first = e.first(); first.getAsJsonArray("addresses").add(first.getAsJsonArray("addresses")[0].deepCopy().asJsonObject.apply { addProperty("id","synthetic-second-address") })
            val result = NetworkGeography.query(e, scope, place(), 1000.0)
            assertEquals("MATCHES_FOUND", result.state); assertEquals(1, result.matches.size)
            assertTrue(result.explanation.contains("not current physical presence"))
        }
    }
    @Test fun `missing scope and empty data have distinct honest states`() {
        assertEquals("EMPTY_STORE", NetworkGeography.query(emptyList(), "radius", JsonObject()).state)
        assertEquals("LOCATION_UNAVAILABLE", NetworkGeography.query(entities(), "radius", JsonObject()).state)
        assertEquals("INSUFFICIENT_ADDRESS_DATA", NetworkGeography.query(entities(), "street", JsonObject()).state)
        val p = place().apply { addProperty("country", "Other Country") }
        assertEquals("NO_MATCHES", NetworkGeography.query(entities(), "country", p).state)
        assertEquals("QUERY_FAILED", NetworkGeography.query(entities(), "planet", place()).state)
    }
    @Test fun `city centroids and ambiguous matches never yield meter proximity`() {
        val e = entities(); val a = e.first().getAsJsonArray("associations")[0].asJsonObject
        a.getAsJsonObject("point").addProperty("precision", "locality")
        assertEquals("INSUFFICIENT_ADDRESS_DATA", NetworkGeography.query(e, "radius", place()).state)
        a.getAsJsonObject("point").addProperty("precision", "building"); a.addProperty("geocode_status", "ambiguous")
        assertEquals("QUERY_FAILED", NetworkGeography.query(e, "radius", place()).state)
    }
    @Test fun `spherical distances handle antimeridian poles negatives and inclusive boundary`() {
        assertEquals(2223.8985, NetworkGeography.distance(0.0,179.99,0.0,-179.99),0.01)
        assertTrue(NetworkGeography.distance(90.0,0.0,90.0,180.0) < 0.001)
        assertEquals(0.0, NetworkGeography.distance(-10.0,-20.0,-10.0,-20.0),0.0)
        val e = entities(); val p = place().apply { addProperty("latitude", Math.toDegrees(1000.0 / 6371000)) }
        assertEquals("MATCHES_FOUND", NetworkGeography.query(e,"radius",p,1000.0).state)
        assertEquals("NO_MATCHES", NetworkGeography.query(e,"radius",p,999.9).state)
    }
    @Test fun `unqualified same street name cannot establish association`() {
        val p = place().apply { addProperty("locality", "Elsewhere") }
        assertEquals("NO_MATCHES", NetworkGeography.query(entities(), "street", p).state)
        p.remove("country")
        assertEquals("INSUFFICIENT_ADDRESS_DATA", NetworkGeography.query(entities(), "street", p).state)
    }
    @Test fun `confirmed manual point supports offline proximity without fabricated geocoding`() {
        val e=entities(); val a=e.first().getAsJsonArray("associations")[0].asJsonObject
        a.addProperty("geocode_status","unresolved")
        assertEquals("MATCHES_FOUND",NetworkGeography.query(e,"radius",place()).state)
        assertEquals("MATCHES_FOUND",NetworkGeography.query(e,"building",place()).state)
        assertEquals("unresolved",a["geocode_status"].asString)
        a.add("verified_at",JsonNull.INSTANCE)
        assertEquals("QUERY_FAILED",NetworkGeography.query(e,"radius",place()).state)
        a.addProperty("verified_at","2026-10-03T12:00:00Z"); a.addProperty("source","imported_contact")
        assertEquals("QUERY_FAILED",NetworkGeography.query(e,"radius",place()).state)
        a.addProperty("source","user_observation"); a.addProperty("geocode_status","ambiguous")
        assertEquals("QUERY_FAILED",NetworkGeography.query(e,"radius",place()).state)
    }
}
