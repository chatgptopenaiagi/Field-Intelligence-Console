package com.fieldconsole.ui.network

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.export.ReportExchange
import com.fieldconsole.viewmodel.NetworkViewModel
import com.google.gson.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(initialReport: JsonObject?, onBack: () -> Unit, onSelectPlace: (JsonObject) -> Unit, vm: NetworkViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsState()
    val notice by vm.notice.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember(initialReport) { mutableStateOf(initialReport) }
    var incoming by remember { mutableStateOf<JsonObject?>(null) }
    var export by remember { mutableStateOf<JsonObject?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try { incoming = ReportExchange.decode(readBounded(context.contentResolver.openInputStream(uri) ?: error("No input"))) }
        catch (_: Exception) { vm.notify("Report import failed: invalid, unsupported or oversized file. Nothing saved.") }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && export != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(ReportExchange.encode(export!!)) } ?: error("No output")
            vm.notify("Selected field report exported.")
        } catch (_: Exception) { vm.notify("Report export failed; destination may contain a partial file.") }
        export = null
    }
    Scaffold(topBar = { TopAppBar(title = { Text("FIELD REPORTS") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Local report exchange. NETWORK contacts and counts are excluded from ordinary reports.")
            notice?.let { Text(it) }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import FIC report") }
            OutlinedTextField(query, { query = it }, label = { Text("Search report history") }, modifier = Modifier.fillMaxWidth())
            reports.filter { it.getAsJsonObject("observation").text("note").contains(query, true) || it.text("observed_at").contains(query, true) }.forEach { report ->
                Card(Modifier.fillMaxWidth().clickable { selected = report }) { Column(Modifier.padding(12.dp)) {
                    Text(report.text("observed_at")); Text(report.getAsJsonObject("observation").text("note").take(120))
                } }
            }
            if (reports.isEmpty()) Text("No saved exchange reports. Existing scan history remains in HISTORY.")
            selected?.let { report ->
                HorizontalDivider()
                ReportSummary(report)
                Button(onClick = { vm.saveReport(report) }) { Text("Save report to local history") }
                OutlinedButton(onClick = { export = report }) { Text("Preview report export") }
                TextButton(onClick = { onSelectPlace(report) }) { Text("Use report place in Known Network") }
                val location = report.getAsJsonObject("location")
                if (!location.get("latitude").isJsonNull && !location.get("longitude").isJsonNull)
                    TextButton(onClick = { openControlled(context, "map", "${location.text("latitude")},${location.text("longitude")}") }) { Text("Open report coordinates on map") }
                if (reports.any { it.text("id") == report.text("id") }) TextButton(onClick = { deleting = true }) { Text("Delete saved report") }
            }
        }
    }
    incoming?.let { report -> AlertDialog(onDismissRequest = { incoming = null }, title = { Text("Preview report import") }, text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) { ReportSummary(report) } },
        confirmButton = { TextButton(onClick = { vm.saveReport(report); selected = report; incoming = null }) { Text("Confirm local import") } }, dismissButton = { TextButton(onClick = { incoming = null }) { Text("Cancel") } }) }
    export?.let { report -> AlertDialog(onDismissRequest = { export = null }, title = { Text("Preview selected report export") }, text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
        Text("Includes location and observations in this report. Check before sharing.")
        Text(report.toString(), style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { exporter.launch("FIC-field-report.json") }) { Text("Choose export file") } }, dismissButton = { TextButton(onClick = { export = null }) { Text("Cancel") } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete saved report?") }, text = { Text("Only this exchange-library record is deleted. Original scan history and previous exports are preserved.") }, confirmButton = { TextButton(onClick = { selected?.let { vm.deleteReport(it.text("id")) }; selected = null; deleting = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}

@Composable
private fun ReportSummary(report: JsonObject) {
    val location = report.getAsJsonObject("location")
    Text("Observed: ${report.text("observed_at")}")
    Text("Location source: ${location.text("source")}; reported accuracy: ${location.text("accuracy_m").ifBlank { "unknown" }} m")
    Text("Coordinates: ${location.text("latitude").ifBlank { "unavailable" }}, ${location.text("longitude").ifBlank { "unavailable" }}")
    Text("Address: ${location.text("address").ifBlank { "unavailable" }} (${location.text("address_source")})")
    Text("Observation: ${report.getAsJsonObject("observation").text("note")}")
    Text("Tag: ${report.getAsJsonObject("observation").text("tag")}")
    report.array("providers").forEach { item -> val p = item.asJsonObject
        Text("${p.text("provider")}: ${p.text("state")} / ${p.text("freshness")} · ${p.text("fetched_at").ifBlank { "time unknown" }}")
        if (p.text("error").isNotBlank()) Text(p.text("error"))
    }
    report.get("ai_advisory")?.takeIf { it.isJsonObject }?.asJsonObject?.let { Text("AI ADVISORY — ${it.text("provider")}: ${it.text("text")}") }
}
