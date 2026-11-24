package com.fieldconsole

import com.fieldconsole.data.emergency.CountryBoundary
import com.fieldconsole.data.emergency.EmergencyNumbers
import com.fieldconsole.location.GeoContext
import org.junit.Assert.*
import org.junit.Test

class RegionalCountryTest {
    private val now = 1_800_000_000_000L
    private val vienna = GeoContext(latitude=48.21, longitude=16.37, timestamp=now, accuracy=4f, countryCode="AT")

    @Test fun freshInteriorAndCountrySwitching() {
        assertEquals("AT", CountryBoundary.current(vienna, now))
        assertEquals("RO", CountryBoundary.current(vienna.copy(latitude=44.43, longitude=26.10, countryCode="RO"), now))
        assertEquals("DE", CountryBoundary.current(vienna.copy(latitude=52.52, longitude=13.4, countryCode=null), now))
        assertEquals("BE", CountryBoundary.interior(50.85, 4.35))
    }

    @Test fun languageRespectsSupportedPreferencesAndCountryFallback() {
        val language = com.fieldconsole.location.RegionalLanguage
        assertEquals("en", language.automatic("AT", listOf("fr", "en")))
        assertEquals("ro", language.automatic("DE", listOf("ro", "de")))
        assertEquals("de", language.automatic("AT", listOf("it")))
        assertEquals("ro", language.automatic("RO", emptyList()))
        assertEquals("en", language.automatic("BE", listOf("nl", "fr")))
    }

    @Test fun uncertaintyRequiresConfirmation() {
        for (geo in listOf(null, vienna.copy(saved=true), vienna.copy(provider="manual"), vienna.copy(accuracy=0f), vienna.copy(accuracy=5000f),
            vienna.copy(countryCode="DE"), vienna.copy(latitude=48.14,longitude=17.10),
            vienna.copy(timestamp=now-120001), vienna.copy(timestamp=now+1), vienna.copy(latitude=Double.NaN))) {
            assertNull(CountryBoundary.current(geo, now))
        }
        assertTrue(EmergencyNumbers.forCountry("ZZ").isEmpty())
        assertEquals(setOf("112"), EmergencyNumbers.forCountry("RO").map { it.number }.toSet())
        assertEquals("133", EmergencyNumbers.forCountry("AT").single { it.category.name=="POLICE" }.number)
    }
}
