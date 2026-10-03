package com.fieldconsole

import com.fieldconsole.data.emergency.EmergencyNumber
import com.fieldconsole.data.emergency.EmergencyNumbers
import org.junit.Assert.*
import org.junit.Test

class EmergencyCatalogSafetyTest {
    @Test fun `US poison number is labeled human Poison Help`() {
        val entry = EmergencyNumbers.forCountry("US").single { it.category == EmergencyNumber.Category.POISON }
        assertEquals("1-800-222-1222", entry.number)
        assertEquals("US Poison Help (human)", entry.description)
    }

    @Test fun `UK professional poison service is not presented as unrestricted public line`() {
        val entry = EmergencyNumbers.forCountry("GB").single { it.category == EmergencyNumber.Category.POISON }
        assertEquals("+44-344-892-0111", entry.number)
        assertTrue(entry.description.contains("healthcare professionals only"))
        assertTrue(entry.description.contains("England/Scotland/Wales"))
    }
}
