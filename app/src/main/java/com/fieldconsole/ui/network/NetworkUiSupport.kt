package com.fieldconsole.ui.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
internal fun JsonObject.array(key: String): JsonArray = getAsJsonArray(key) ?: JsonArray()
internal fun readBounded(input: InputStream): ByteArray = input.use {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = it.read(buffer)
        if (count < 0) break
        require(result.size() + count <= 5 * 1024 * 1024) { "File exceeds 5 MiB limit." }
        result.write(buffer, 0, count)
    }
    result.toByteArray()
}

internal fun openControlled(context: Context, kind: String, value: String) {
    val uri = when (kind) {
        "tel" -> Uri.fromParts("tel", value, null)
        "mailto" -> Uri.fromParts("mailto", value, null)
        "map" -> {
            val parts = value.split(',')
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
            Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        }
        else -> return
    }
    try {
        context.startActivity(Intent(when (kind) { "tel" -> Intent.ACTION_DIAL; "mailto" -> Intent.ACTION_SENDTO; else -> Intent.ACTION_VIEW }, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No compatible app available.", Toast.LENGTH_LONG).show()
    }
}
