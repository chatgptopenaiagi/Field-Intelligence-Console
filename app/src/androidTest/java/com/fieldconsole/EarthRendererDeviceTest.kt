package com.fieldconsole

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fieldconsole.location.GeoContext
import com.fieldconsole.ui.main.EarthPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EarthRendererDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun findWeb(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> view.children.firstNotNullOfOrNull { findWeb(it) }
        else -> null
    }

    private fun renderer(): String? {
        val result = LinkedBlockingQueue<String>()
        compose.runOnIdle {
            val web = findWeb(compose.activity.window.decorView)
            if (web == null) result.offer("null")
            else web.evaluateJavascript("document.getElementById('globe')?.dataset.renderer") {
                result.offer(it ?: "null")
            }
        }
        return result.poll(2, TimeUnit.SECONDS)
    }

    @Test fun reopenIn2dAfterRendererTermination() {
        compose.setContent { EarthPanel(GeoContext(latitude = 0.0, longitude = 0.0), {}, {}) }
        compose.waitUntil(30_000) { renderer() in setOf("\"cesium-webgl\"", "\"2d\"") }
        compose.runOnIdle {
            val web = requireNotNull(findWeb(compose.activity.window.decorView))
            assertTrue(requireNotNull(web.webViewRenderProcess).terminate())
        }
        val reopen = compose.activity.getString(R.string.earth_reopen_2d)
        compose.waitUntil(30_000) { findWebOnIdle() == null }
        compose.onNodeWithText(reopen).performClick()
        compose.waitUntil(30_000) { renderer() == "\"2d\"" }
        compose.runOnIdle {
            val web = requireNotNull(findWeb(compose.activity.window.decorView))
            assertEquals("fic-earth.invalid", android.net.Uri.parse(web.url).host)
            assertTrue(requireNotNull(web.url).endsWith("&renderer=2d"))
        }
    }

    private fun findWebOnIdle() = compose.runOnIdle { findWeb(compose.activity.window.decorView) }
}
