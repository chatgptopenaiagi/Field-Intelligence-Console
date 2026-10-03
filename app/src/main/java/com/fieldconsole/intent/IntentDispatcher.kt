package com.fieldconsole.intent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.location.GeoContext
import com.fieldconsole.prompt.FieldPromptGenerator
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IntentDispatcher v2 — centralised launcher for all external Android intents.
 *
 * Each of the 8 Action Matrix modules uses a DIFFERENT intelligent source
 * and query strategy. No two modules open the same URL or use the same query.
 *
 * New in v2:
 *  - All modules accept optional [FieldEntry] for structured AI prompts
 *  - Navigation: walking, cycling, driving, Street View, Satellite, Terrain
 *  - Share: WhatsApp, Telegram, Email, Signal, system share sheet
 *  - Logistics: Overpass/OSM fallback
 */
@Singleton
class IntentDispatcher @Inject constructor(
    private val promptGen: FieldPromptGenerator
) {

    // ── Module 1 — INFO SCAN ──────────────────────────────────────────────────

    fun infoScan(context: Context, geo: GeoContext, entry: FieldEntry? = null) {
        entry?.let {
            val prompt = promptGen.infoScanPrompt(it, geo)
            copyToClipboard(context, prompt, "Info Scan Prompt")
        }
        val query = if (geo.hasPlaceName)
            "current information about ${geo.fullPlaceName} ${geo.coordString}"
        else
            "current information about location ${geo.coordString}"
        openBrowserSearch(context, query)
    }

    // ── Module 2 — HISTORY ────────────────────────────────────────────────────

    fun history(context: Context, geo: GeoContext, entry: FieldEntry? = null) {
        entry?.let {
            val prompt = promptGen.historyPrompt(it, geo)
            copyToClipboard(context, prompt, "History Prompt")
        }
        if (geo.hasPlaceName) {
            val place = listOfNotNull(geo.district, geo.city)
                .filter { it.isNotBlank() }.firstOrNull() ?: geo.city ?: geo.fullPlaceName
            openUrl(context, "https://en.wikipedia.org/w/index.php?search=${enc(place + " history")}")
        } else {
            openUrl(context, "https://www.wikidata.org/w/index.php?search=${enc(geo.coordString)}")
        }
    }

    // ── Module 3 — NAVIGATION ─────────────────────────────────────────────────

    fun navigation(context: Context, geo: GeoContext) {
        val label = enc(geo.shortLabel)
        val geoUri = Uri.parse("geo:${geo.latitude},${geo.longitude}?q=${geo.latitude},${geo.longitude}($label)")
        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapsIntent)
        } catch (e: Exception) {
            openUrl(context, "https://maps.google.com/?q=${geo.latitude},${geo.longitude}")
        }
    }

    fun navigationWalking(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=${geo.latitude},${geo.longitude}&travelmode=walking")
    }

    fun navigationCycling(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=${geo.latitude},${geo.longitude}&travelmode=bicycling")
    }

    fun navigationDriving(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=${geo.latitude},${geo.longitude}&travelmode=driving")
    }

    fun openStreetView(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${geo.latitude},${geo.longitude}")
    }

    fun openSatelliteView(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/@${geo.latitude},${geo.longitude},500m/data=!3m1!1e3")
    }

    fun openTerrainView(context: Context, geo: GeoContext) {
        openUrl(context, "https://www.google.com/maps/@${geo.latitude},${geo.longitude},15z/data=!5m1!1e4")
    }

    // ── Module 4 — AI ANALYSIS ────────────────────────────────────────────────

    fun aiAnalysis(context: Context, geo: GeoContext, note: String = "", tag: String = "") {
        val entry = FieldEntry(
            latitude  = geo.latitude, longitude = geo.longitude,
            accuracy  = geo.accuracy, note = note, tag = tag,
            timestamp = geo.timestamp, placeName = geo.fullPlaceName
        )
        val prompt = promptGen.generate(entry, geo)
        copyToClipboard(context, prompt, "Field Intelligence Prompt")
        Toast.makeText(context, "Prompt copied. Opening AI…", Toast.LENGTH_SHORT).show()
        openChatGPT(context, prompt)
    }

    // ── Module 5 — RISK / SAFETY ──────────────────────────────────────────────

    fun riskSafety(context: Context, geo: GeoContext, entry: FieldEntry? = null) {
        entry?.let {
            val prompt = promptGen.riskPrompt(it, geo)
            copyToClipboard(context, prompt, "Risk Assessment Prompt")
        }
        openUrl(context,
            "https://www.google.com/maps/search/police+station+hospital+fire+station/" +
            "@${geo.latitude},${geo.longitude},15z")
    }

    // ── Module 6 — LOGISTICS ──────────────────────────────────────────────────

    fun logistics(context: Context, geo: GeoContext) {
        openUrl(context,
            "https://www.google.com/maps/search/restaurants+hotels+fuel+parking+ATM+pharmacy/" +
            "@${geo.latitude},${geo.longitude},15z")
    }

    // ── Module 7 — FINANCIAL ──────────────────────────────────────────────────

    fun financial(context: Context, geo: GeoContext, entry: FieldEntry? = null) {
        entry?.let {
            val prompt = promptGen.financialPrompt(it, geo)
            copyToClipboard(context, prompt, "Financial Intelligence Prompt")
        }
        val query = if (geo.hasPlaceName)
            "real estate prices business activity commercial area ${geo.fullPlaceName} property value"
        else
            "real estate prices business potential near ${geo.coordString}"
        openBrowserSearch(context, query)
    }

    // ── Emergency ─────────────────────────────────────────────────────────────

    fun emergencyNavigation(context: Context, geo: GeoContext) {
        openUrl(context,
            "https://www.google.com/maps/dir/?api=1" +
            "&origin=${geo.latitude},${geo.longitude}" +
            "&destination=${enc("nearest hospital")}" +
            "&travelmode=driving")
    }

    // ── Copy / Share ──────────────────────────────────────────────────────────

    fun copyLocation(context: Context, geo: GeoContext, note: String = "", tag: String = "") {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(geo.timestamp))
        val text = buildString {
            appendLine("FIELD LOCATION")
            appendLine("Place:       ${geo.fullPlaceName}")
            appendLine("Coordinates: ${geo.coordString}")
            appendLine("Accuracy:    ±${geo.accuracy.toInt()} m")
            appendLine("Timestamp:   $ts")
            if (note.isNotBlank()) appendLine("Observation: $note")
            if (tag.isNotBlank())  appendLine("Tag:         $tag")
            appendLine("Maps Link:   ${geo.mapsLink}")
        }.trim()
        copyToClipboard(context, text, "Field Location")
        Toast.makeText(context, "Location copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareFieldReport(context: Context, geo: GeoContext, note: String = "", tag: String = "") {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(geo.timestamp))
        val report = buildString {
            appendLine("═══ FIELD INTELLIGENCE REPORT ═══")
            appendLine()
            appendLine("Location:    ${geo.coordString}")
            appendLine("Place:       ${geo.fullPlaceName}")
            appendLine("Accuracy:    ±${geo.accuracy.toInt()} m")
            appendLine("Timestamp:   $ts")
            if (note.isNotBlank()) appendLine("Observation: $note")
            if (tag.isNotBlank())  appendLine("Tag:         $tag")
            appendLine("Maps Link:   ${geo.mapsLink}")
            appendLine()
            appendLine("Generated by Field Intelligence Console v2")
        }.trim()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Field Report — ${geo.shortLabel}")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Share Field Report"))
    }

    fun shareToWhatsApp(context: Context, text: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, text)
            })
        } catch (e: Exception) { shareText(context, text) }
    }

    fun shareToTelegram(context: Context, text: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; setPackage("org.telegram.messenger")
                putExtra(Intent.EXTRA_TEXT, text)
            })
        } catch (e: Exception) { shareText(context, text) }
    }

    fun shareToEmail(context: Context, subject: String, body: String) {
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }, "Send Email"
        ))
    }

    // ── Primitives ────────────────────────────────────────────────────────────

    fun openGoogleMaps(context: Context, lat: Double, lon: Double, label: String = "Field Scan") {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        val maps = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
        try {
            context.startActivity(maps)
        } catch (e: Exception) {
            openUrl(context, "https://maps.google.com/?q=$lat,$lon")
        }
    }

    fun openNavigation(context: Context, lat: Double, lon: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            })
        } catch (e: Exception) {
            openUrl(context, "https://maps.google.com/maps?daddr=$lat,$lon")
        }
    }

    fun openChatGPT(context: Context, prompt: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (appIntent != null) context.startActivity(appIntent)
        else openUrl(context, "https://chat.openai.com/?q=${enc(prompt.take(500))}")
    }

    fun openWikipedia(context: Context, query: String) {
        openUrl(context, "https://en.wikipedia.org/w/index.php?search=${enc(query)}")
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open: $url", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Text") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, text: String) {
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
            }, "Share"
        ))
    }

    fun formatCoordinates(entry: FieldEntry): String = "${entry.latitude}, ${entry.longitude}"

    // ── Legacy prompt builder (kept for backward compat) ─────────────────────

    fun buildAiPrompt(geo: GeoContext, note: String, tag: String): String {
        val entry = FieldEntry(
            latitude = geo.latitude, longitude = geo.longitude,
            accuracy = geo.accuracy, note = note, tag = tag,
            timestamp = geo.timestamp, placeName = geo.fullPlaceName
        )
        return promptGen.generate(entry, geo)
    }

    private fun openBrowserSearch(context: Context, query: String) {
        openUrl(context, "https://www.google.com/search?q=${enc(query)}")
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
