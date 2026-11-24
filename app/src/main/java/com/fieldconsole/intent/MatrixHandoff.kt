package com.fieldconsole.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fieldconsole.location.GeoContext

object MatrixHandoff {
    const val CHATGPT = "com.openai.chatgpt"
    const val EARTH = "com.google.earth"
    const val END = "[END FIC CONTEXT]"
    const val MAX_BYTES = 64 * 1024

    fun completeText(text: String): Boolean =
        text.endsWith("\n$END") && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES &&
            Charsets.UTF_8.newEncoder().canEncode(text)

    fun chatIntent(reviewed: String): Intent {
        require(completeText(reviewed))
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(CHATGPT)
            putExtra(Intent.EXTRA_TEXT, reviewed)
            check(getStringExtra(Intent.EXTRA_TEXT) == reviewed)
        }
    }

    fun earthUrl(geo: GeoContext): Uri {
        require(geo.validCoordinates)
        return Uri.parse("https://earth.google.com/web/search/${geo.latitude},${geo.longitude}/")
    }

    fun openEarth(context: Context, geo: GeoContext): Boolean {
        if (!geo.validCoordinates) return false
        val search = "${geo.latitude},${geo.longitude}"
        return listOf(earthUrl(geo), Uri.parse("geo:$search?q=$search")).any { uri ->
            launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage(EARTH))
        }
    }

    fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
