package com.fieldconsole.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fieldconsole.R
import com.fieldconsole.intent.MatrixHandoff
import com.fieldconsole.location.GeoContext
import org.json.JSONTokener

private val files = mapOf("index.html" to "text/html", "earth.css" to "text/css",
    "core.js" to "application/javascript", "panel.js" to "application/javascript", "map.js" to "application/javascript",
    "gpu.js" to "application/javascript")
private val sourceHosts = setOf("earthquake.usgs.gov", "celestrak.org", "www.nhc.noaa.gov", "eonet.gsfc.nasa.gov",
    "api.open-meteo.com", "gbfs.citibikenyc.com", "www.ndbc.noaa.gov", "doi.org", "api.weather.gov",
    "www.mta.info", "www.nps.gov")

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EarthPanel(geo: GeoContext, back: () -> Unit, home: () -> Unit) {
    val context = LocalContext.current
    var rendererGone by rememberSaveable { mutableStateOf(false) }
    var flatOnly by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    fun fail() = Toast.makeText(context, R.string.earth_operation_failed, Toast.LENGTH_LONG).show()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && (text == null || runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
            }.isFailure)) fail()
    }
    BackHandler(onBack = back)
    if (rendererGone) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.earth_renderer_stopped))
            Button(onClick = { flatOnly = true; rendererGone = false }) { Text(stringResource(R.string.earth_reopen_2d)) }
            Button(onClick = back) { Text(stringResource(R.string.back)) }
        }
        return
    }
    AndroidView(modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() }, factory = { activity ->
        WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    rendererGone = true
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val uri = req.url
                    if (uri.host != "fic-earth.invalid") return null
                    val name = uri.path.orEmpty().removePrefix("/")
                    val mime = files[name] ?: return WebResourceResponse("text/plain", "UTF-8", null)
                    return WebResourceResponse(mime, "UTF-8", context.assets.open(name))
                }
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    if (!req.isForMainFrame || !req.hasGesture()) return true
                    val uri = req.url
                    if (uri.scheme == "fic-local") {
                        when (uri.host) {
                            "back" -> back()
                            "home" -> home()
                            "share" -> view.evaluateJavascript("window.ficReviewed()") { json ->
                                val text = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
                                if (text.isNullOrBlank() || text.length > 12000) fail()
                                else {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, text)
                                    if (!MatrixHandoff.launch(context, Intent(send).setPackage(MatrixHandoff.CHATGPT)) &&
                                        !MatrixHandoff.launch(context, Intent.createChooser(send, null))) fail()
                                }
                            }
                            "export" -> view.evaluateJavascript("window.ficExport()") { json ->
                                val text = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
                                if (text.isNullOrEmpty() || text.length > 1024 * 1024) fail()
                                else { pendingExport = text; save.launch("fic-earth-keypoints.json") }
                            }
                        }
                    } else if (uri.scheme == "https" && uri.host == "earth.google.com") {
                        val coords = uri.path.orEmpty().removePrefix("/web/search/").trimEnd('/').split(',')
                        val lat = coords.getOrNull(0)?.toDoubleOrNull()
                        val lon = coords.getOrNull(1)?.toDoubleOrNull()
                        if (coords.size == 2 && lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            if (!MatrixHandoff.launch(context, Intent(intent).setPackage("com.google.earth")) &&
                                !MatrixHandoff.launch(context, intent)) fail()
                        }
                    } else if (uri.scheme == "https" && uri.host in sourceHosts) {
                        if (!MatrixHandoff.launch(context, Intent(Intent.ACTION_VIEW, uri))) fail()
                    }
                    return true
                }
            }
            val lang = activity.resources.configuration.locales[0].language
            val via = when { geo.saved -> "saved"; geo.manual -> "manual"; else -> "scan" }
            val stamp = if (geo.measuredAt != "UNKNOWN") "&at=${Uri.encode(geo.measuredAt)}" else ""
            val renderer = if (flatOnly) "&renderer=2d" else ""
            loadUrl("https://fic-earth.invalid/index.html#lat=${geo.latitude}&lon=${geo.longitude}&lang=$lang&via=$via$stamp$renderer")
        }
    })
}
