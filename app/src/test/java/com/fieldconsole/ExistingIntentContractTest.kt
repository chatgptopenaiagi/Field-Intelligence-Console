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

    @Test fun `existing AI action copies prompt and requests external browser without credentials`() {
        dispatcher.aiAnalysis(context, geo, "test observation", "survey")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val prompt = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(prompt.contains("test observation"))
        assertTrue(prompt.contains("survey"))
        assertEquals(Intent.ACTION_VIEW, launched.single().action)
        assertEquals("chat.openai.com", launched.single().data!!.host)
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
