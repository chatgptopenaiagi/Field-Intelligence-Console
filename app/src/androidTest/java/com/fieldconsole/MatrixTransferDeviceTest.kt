package com.fieldconsole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.intent.MatrixHandoff
import com.fieldconsole.location.GeoContext
import com.fieldconsole.prompt.FieldPromptGenerator
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatrixTransferDeviceTest {
    @Test fun automaticLanguageUsesDevicePreferencesAfterManualSelection() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = target.getSystemService(android.app.LocaleManager::class.java)
        val settings = target.getSharedPreferences("fic_regional", Context.MODE_PRIVATE)
        val original = manager.applicationLocales
        val originalMode = settings.getString("language", null)
        val originalApplied = settings.getString("applied", null)
        val deviceLanguages = manager.systemLocales
        val expected = (0 until deviceLanguages.size()).map { deviceLanguages[it].language }
            .firstOrNull { it in setOf("de", "ro", "en") }
        org.junit.Assume.assumeNotNull(expected)
        val alternate = if (expected == "de") "ro" else "de"
        try {
            com.fieldconsole.location.RegionalLanguage.select(target, alternate)
            assertEquals(alternate, manager.applicationLocales.toLanguageTags())
            com.fieldconsole.location.RegionalLanguage.select(target, "auto")
            assertEquals(expected, manager.applicationLocales.toLanguageTags())
        } finally {
            settings.edit().putString("language", originalMode).putString("applied", originalApplied).apply()
            manager.applicationLocales = original
        }
    }

    @Test fun shortAndLongPayloadsReachAnAndroidReceivingActivityIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val dispatcher = IntentDispatcher(FieldPromptGenerator())
        for (count in listOf(1, 120)) {
            val entry = FieldEntry(latitude=44.43,longitude=26.10,accuracy=4.25f,
                timestamp=1700000000123L,note="București 🌍 東京 & café\n".repeat(count),tag="synthetic")
            val reviewed = dispatcher.reviewPrompt("AI ANALYSIS",entry,GeoContext(latitude=entry.latitude,
                longitude=entry.longitude,accuracy=entry.accuracy,timestamp=entry.timestamp,provider="synthetic"))
            val launch = MatrixHandoff.chatIntent(reviewed)
            assertEquals(MatrixHandoff.CHATGPT,launch.`package`)
            val token = "com.fieldconsole.test.receipt." + UUID.randomUUID()
            val inbox = LinkedBlockingQueue<String>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { intent.getStringExtra("text")?.let { inbox.add(it) } }
            }
            androidx.core.content.ContextCompat.registerReceiver(target, receiver, IntentFilter(token),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            try {
                // Change only the destination. The official ChatGPT app does not expose a readback API.
                launch.setPackage(instrumentation.context.packageName)
                launch.setClassName(instrumentation.context.packageName, SyntheticShareReceiver::class.java.name)
                target.startActivity(launch.putExtra("receipt",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val delivered = requireNotNull(inbox.poll(20,TimeUnit.SECONDS))
                assertArrayEquals(reviewed.toByteArray(),delivered.toByteArray())
                target.cacheDir.resolve("synthetic-matrix-$count.txt").writeText(delivered)
            } finally { target.unregisterReceiver(receiver) }
        }
    }
}
