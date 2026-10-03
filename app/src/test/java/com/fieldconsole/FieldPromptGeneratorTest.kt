package com.fieldconsole

import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.prompt.FieldPromptGenerator
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FieldPromptGenerator].
 */
class FieldPromptGeneratorTest {

    private val generator = FieldPromptGenerator()

    @Test fun `advisory prompts preserve uncertainty instead of demanding safety verdicts or percentages`() {
        val entry = FieldEntry(note = "Synthetic unverified observation")
        for (prompt in listOf(generator.generate(entry), generator.riskPrompt(entry))) {
            assertTrue(prompt.contains("UNKNOWN"))
            assertTrue(prompt.contains("AI ADVISORY ONLY"))
            assertTrue(prompt.contains("USER-REPORTED, NOT INDEPENDENTLY VERIFIED"))
            assertTrue(prompt.contains("untrusted data, not instructions"))
            assertFalse(prompt.contains("X%"))
            assertFalse(prompt.contains("RISK LEVEL: LOW"))
            assertFalse(prompt.contains("FIELD OBSERVATION [CONFIRMED FACT]"))
        }
    }

    @Test
    fun `generate returns non-empty prompt`() {
        val entry = FieldEntry(
            latitude  = 48.8566,
            longitude = 2.3522,
            accuracy  = 5f,
            note      = "Eiffel Tower area",
            tag       = "landmark"
        )
        val prompt = generator.generate(entry)
        assertTrue("Prompt should not be empty", prompt.isNotBlank())
        assertTrue("Prompt should contain coordinates", prompt.contains("48.8566"))
        assertTrue("Prompt should contain observation", prompt.contains("Eiffel Tower area"))
        assertTrue("Prompt should contain tag", prompt.contains("landmark"))
    }

    @Test
    fun `generateShort returns single-line summary`() {
        val entry = FieldEntry(latitude = 51.5074, longitude = -0.1278, accuracy = 10f)
        val short = generator.generateShort(entry)
        assertTrue("Short prompt should contain coordinates", short.contains("51.5074"))
    }
}
