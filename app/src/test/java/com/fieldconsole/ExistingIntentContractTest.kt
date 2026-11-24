package com.fieldconsole

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.location.GeoContext
import com.fieldconsole.prompt.FieldPromptGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExistingIntentContractTest {
    private val launched = mutableListOf<Intent>()
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun startActivity(intent: Intent) { launched.add(intent) }
    }
    private val dispatcher = IntentDispatcher(FieldPromptGenerator())
    private val geo = GeoContext(latitude = 48.2, longitude = 16.3, timestamp = 123L)

    @Test fun `Android parcel preserves the reviewed text in all five analysis modes`() {
        for (note in listOf("test observation", "Wien · București · 東京 🌍 & ? %\n".repeat(140))) {
            val entry = com.fieldconsole.data.local.entity.FieldEntry(latitude=48.2, longitude=16.3, accuracy=3.75f,
                timestamp=123L, note=note, tag="survey")
            for (mode in listOf("INFO SCAN","HISTORY","AI ANALYSIS","RISK / SAFETY","FINANCIAL")) {
                val text = dispatcher.reviewPrompt(mode, entry, geo.copy(provider="gps"))
                val intent = com.fieldconsole.intent.MatrixHandoff.chatIntent(text)
                val parcel = android.os.Parcel.obtain()
                try {
                    intent.writeToParcel(parcel, 0)
                    parcel.setDataPosition(0)
                    val received = Intent.CREATOR.createFromParcel(parcel)
                    assertEquals("com.openai.chatgpt", received.`package`)
                    assertEquals(Intent.ACTION_SEND, received.action)
                    assertEquals(text, received.getStringExtra(Intent.EXTRA_TEXT))
                    for (expected in listOf(note,mode,"3.75", "1970-01-01T00:00:00.123Z", "gps")) assertTrue(text.contains(expected))
                } finally { parcel.recycle() }
            }
        }
        val handoff = com.fieldconsole.intent.MatrixHandoff
        assertFalse(handoff.completeText("Accur"))
        assertFalse(handoff.completeText("x".repeat(70000) + "\n" + handoff.END))
    }

    @Test fun `Earth targets only Earth at valid selected coordinates`() {
        val handoff = com.fieldconsole.intent.MatrixHandoff
        assertTrue(handoff.openEarth(context, geo))
        assertEquals("com.google.earth", launched.single().`package`)
        assertEquals("https://earth.google.com/web/search/48.2,16.3/", launched.single().data.toString())
        val unavailable = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { throw android.content.ActivityNotFoundException() }
        }
        assertFalse(handoff.openEarth(unavailable, geo))
        for (invalid in listOf(GeoContext(), geo.copy(latitude=91.0), geo.copy(longitude=Double.POSITIVE_INFINITY)))
            assertFalse(handoff.openEarth(context, invalid))
        assertEquals(1, launched.size)
    }

    @Test fun `existing report action shares inline text with observation and coordinates`() {
        dispatcher.shareFieldReport(context, geo, "test observation", "survey")
        val chooser = launched.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val report = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(report.contains("test observation"))
        assertTrue(report.contains(geo.coordString))
    }
}
