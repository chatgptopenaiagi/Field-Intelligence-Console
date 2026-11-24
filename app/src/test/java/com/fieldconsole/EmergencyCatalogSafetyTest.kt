package com.fieldconsole

import com.fieldconsole.data.emergency.EmergencyNumber
import com.fieldconsole.data.emergency.EmergencyNumbers
import org.junit.Assert.*
import org.junit.Test

class EmergencyCatalogSafetyTest {
    @Test fun `US poison number is labeled human Poison Help`() {
        val entry = EmergencyNumbers.forCountry("US").single { it.category == EmergencyNumber.Category.POISON }
        assertEquals("1-800-222-1222", entry.number)
        assertEquals("https://poisonhelp.hrsa.gov/about-us", entry.source)
    }

    @Test fun `UK professional poison service is not presented as unrestricted public line`() {
        assertFalse(EmergencyNumbers.forCountry("GB").any { it.number == "+44-344-892-0111" })
    }
}
