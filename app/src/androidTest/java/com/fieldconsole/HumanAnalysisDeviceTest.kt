package com.fieldconsole

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fieldconsole.intent.MatrixHandoff
import com.fieldconsole.prompt.HumanContext
import com.fieldconsole.location.GeoContext
import com.fieldconsole.ui.main.HumanAnalysis
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HumanAnalysisDeviceTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val point = GeoContext(latitude = 41.125, longitude = 12.75, accuracy = 4.5f,
        timestamp = 981173106000, provider = "synthetic", countryCode = "RO", street = "Synthetic private street",
        region = "Synthetic region")

    @Test fun packagedCatalogAndSyntheticProvenance() {
        for (lang in listOf("en","ro","de")) {
            val c = HumanContext(ui.activity, lang)
            assertEquals(7, c.categories.size)
            assertEquals((1..35).map { "Q%02d".format(java.util.Locale.ROOT,it) }, c.questions.map { it.getString("id") })
            for (category in c.categories) assertEquals(5, c.questions.count { it.getString("category") == category.getString("id") })
            for (q in c.questions) {
                val id = q.getString("id")
                val request = c.local(q,"question")
                assertTrue(c.compose(id, request, point, "AT", "none").contains(request))
            }
            val q = c.questions[7]
            fun draft(geo: GeoContext, mode: String, country: String?) = c.compose("Q08",c.local(q,"question"),geo,country,mode)
            assertFalse(draft(point,"none","AT").contains("41.125"))
            val region = draft(point,"region","AT")
            assertTrue(region.contains(c.text("mismatch")))
            assertTrue(region.contains("Synthetic region"))
            assertFalse(region.contains("Synthetic private street"))
            assertFalse(region.contains("41.125"))
            assertTrue(draft(point,"exact",null).contains("SMURD"))
            assertFalse(draft(point.copy(provider="manual"),"exact",null).contains("4.5 m"))
            assertTrue(draft(point.copy(saved=true),"exact",null).contains(c.text("historical")))
            assertTrue(draft(point.copy(latitude=Double.NaN),"exact",null).contains(c.text("unknown")))
            ui.activity.cacheDir.resolve("synthetic-human-$lang.txt").writeText(draft(point,"exact","AT"))
        }
    }

    @Test fun selectEditCopyAndCancel() {
        val lang = ui.activity.resources.configuration.locales[0].language.let { if (it in listOf("ro","de","en")) it else "en" }
        val c = HumanContext(ui.activity, lang)
        var closed = false
        ui.setContent { HumanAnalysis(point, "", "PRIVATE_SYNTHETIC", { closed = true }, {}) }
        ui.onNodeWithText(c.text("catalog")).performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Q16")
        ui.onNodeWithText("Q16 · " + c.local(c.questions[15],"title")).performClick()
        ui.onNodeWithText(c.text("preview")).performScrollTo().performClick()
        val editor = ui.onNode(hasSetTextAction())
        editor.assertTextContains("Q16", substring=true)
        val original = editor.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertFalse(original.contains("PRIVATE_SYNTHETIC") || original.contains("41.125"))
        val edited = "Întrebare & 東京? 🌍\n".repeat(160)
        editor.performTextReplacement(edited)
        ui.onNodeWithText(c.text("copy")).performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(edited, ui.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
        }
        ui.onNodeWithText(c.text("cancel")).performClick()
        ui.runOnIdle { assertTrue(closed) }
        assertFalse(MatrixHandoff.reviewable("ă".repeat(32769)))
    }
}
