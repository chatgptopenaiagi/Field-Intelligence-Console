package com.fieldconsole.ui.network

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.location.GeoContext
import com.fieldconsole.network.*
import com.fieldconsole.viewmodel.NetworkViewModel
import com.google.gson.JsonObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkScreen(initialGeo: GeoContext?, onBack: () -> Unit, vm: NetworkViewModel = hiltViewModel(), initialPlaceSource: String = "device scan") {
    val context = LocalContext.current
    val entities by vm.entities.collectAsState()
    val preview by vm.preview.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("all") }
    var radius by remember { mutableStateOf("1000") }
    var place by remember { mutableStateOf(JsonObject().apply {
        initialGeo?.let { geo ->
            addProperty("latitude", geo.latitude); addProperty("longitude", geo.longitude)
            addProperty("street", geo.street.orEmpty()); addProperty("neighborhood", geo.district.orEmpty())
            addProperty("locality", geo.city.orEmpty()); addProperty("country", geo.countryCode ?: geo.country.orEmpty())
        }
    }) }
    var placeDialog by remember { mutableStateOf(false) }
    var placeSource by remember { mutableStateOf(initialPlaceSource) }
    var detail by remember { mutableStateOf<JsonObject?>(null) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var importFormat by remember { mutableStateOf("vcf") }
    var exportFormat by remember { mutableStateOf("json") }
    var exportPreview by remember { mutableStateOf<NetworkExport?>(null) }
    var exportNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var phoneConsent by remember { mutableStateOf(false) }
    var csvBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = exportPreview
        if (uri != null && payload != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.bytes) }
                    ?: error("No output stream")
                vm.notify("Selected contacts exported. Keep the file private.")
            } catch (_: Exception) { vm.notify("Export failed. The selected destination may contain a partial file.") }
        }
        exportPreview = null
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (importFormat == "csv") {
                try { csvBytes = readBounded(context.contentResolver.openInputStream(uri) ?: error("No input")) }
                catch (_: Exception) { vm.notify("Import failed: unreadable file or input exceeds 5 MiB.") }
            } else vm.readPreview({ readBounded(context.contentResolver.openInputStream(uri) ?: error("No input")) }, importFormat)
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.phonePreview { PhoneContactsImport.preview(context) }
        else vm.notify("Contacts permission denied. VCF/CSV/JSON and manual entry remain available.")
    }
    val filtered = remember(entities, query) {
        entities.filter { entity ->
            query.isBlank() || listOf("display_name", "organization", "role", "notes").any { entity.text(it).contains(query, true) } ||
                entity.array("phones").any { it.asJsonObject.text("value").contains(query, true) } ||
                entity.array("emails").any { it.asJsonObject.text("value").contains(query, true) } ||
                entity.array("tags").any { it.asString.contains(query, true) }
        }
    }
    val result = remember(filtered, scope, place, radius) {
        if (scope == "all") null else runCatching { NetworkGeography.query(filtered, scope, place, radius.toDoubleOrNull() ?: -1.0) }.getOrNull()
    }
    val visible = if (scope == "all") filtered else result?.matches.orEmpty()

    Scaffold(topBar = { TopAppBar(title = { Text("KNOWN NETWORK") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("People, organizations and contacts associated with this place.")
            Text("Selected place source: $placeSource", style = MaterialTheme.typography.labelSmall)
            Text("Saved geographic association is not current physical presence.", style = MaterialTheme.typography.labelLarge)
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("IMPORTING / processing locally…") }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = NetworkCodec.newEntity("") }, enabled = !busy) { Text("Add contact") }
                OutlinedButton(onClick = { placeDialog = true }) { Text("Select place") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("vcf", "csv", "json").forEach { format ->
                    OutlinedButton(onClick = { importFormat = format; filePicker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Import ${format.uppercase()}") }
                }
            }
            TextButton(onClick = { phoneConsent = true }, enabled = !busy) { Text("Import from phone contacts") }
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search contacts / organizations") }, modifier = Modifier.fillMaxWidth())
            ChoiceField("Scope", scope, listOf("all", "building", "street", "radius", "neighborhood", "city", "region", "country")) { scope = it }
            if (scope == "radius") OutlinedTextField(radius, { radius = it }, label = { Text("Radius in metres (saved points)") })
            Text("Area: " + listOf("street", "locality", "region", "country").map { place.text(it) }.filter { it.isNotBlank() }.joinToString(", ").ifBlank {
                if (place.has("latitude")) "${place.text("latitude")}, ${place.text("longitude")}" else "LOCATION UNAVAILABLE — select a place"
            })
            Text(when {
                entities.isEmpty() -> "EMPTY STORE — import a file or add a contact."
                scope != "all" && result == null -> "QUERY FAILED — check the selected place and radius."
                result != null && result.state in setOf("MATCHES_FOUND", "NO_MATCHES") -> "${result.state.replace('_', ' ')} — ${result.matches.size} saved associations (unique entities). ${result.explanation}"
                result != null -> "${result.state.replace('_', ' ')} — ${result.explanation}"
                visible.isEmpty() -> "NO MATCHES"
                else -> "${visible.size} saved contacts — no current-presence information"
            })
            visible.forEach { entity ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp)) {
                        Checkbox(checked = entity.text("id") in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + entity.text("id") else selected - entity.text("id")
                        })
                        Column(Modifier.weight(1f).clickable { detail = entity }) {
                            Text(entity.text("display_name").ifBlank { "Unnamed contact" }, fontWeight = FontWeight.Bold)
                            Text(entity.text("organization"), style = MaterialTheme.typography.bodySmall)
                            Text("${entity.array("addresses").size()} saved addresses · Open details for provenance", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (entities.isNotEmpty()) {
                Text("${selected.size} selected for deliberate export")
                TextButton(onClick = { selected = visible.map { it.text("id") }.toSet() }) { Text("Select displayed contacts") }
                ChoiceField("Export format", exportFormat, listOf("json", "vcf", "csv")) { exportFormat = it }
                Button(enabled = selected.isNotEmpty() && !busy, onClick = {
                    val chosen = entities.filter { it.text("id") in selected }
                    try { exportPreview = NetworkCodec.export(chosen, exportFormat); exportNames = chosen.map { it.text("display_name") } }
                    catch (_: Exception) { vm.notify("Export validation failed. No file created.") }
                }) { Text("Preview selected export") }
            }
            Text("Local only. Imports never trigger geocoding, AI sharing or cloud sync. Original import files are not retained.", style = MaterialTheme.typography.bodySmall)
        }
    }

    preview?.let { pending ->
        var chosen by remember(pending) { mutableStateOf(emptySet<String>()) }
        val duplicateCount = NetworkCodec.previewCounts(entities, pending).duplicates
        AlertDialog(onDismissRequest = vm::cancelPreview, title = { Text("Review import") }, text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                Text("Valid: ${pending.entities.size}; invalid: ${pending.invalidCount}; already stored: $duplicateCount")
                Text("Selected file → local store. Addresses remain saved associations. Nothing is sent externally.")
                pending.issues.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                pending.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { chosen = pending.entities.map { it.text("id") }.toSet() }) { Text("Select all valid records") }
                pending.entities.forEach { item -> Row {
                    Checkbox(item.text("id") in chosen, { checked -> chosen = if (checked) chosen + item.text("id") else chosen - item.text("id") })
                    Text(item.text("display_name").ifBlank { "Unnamed contact" })
                } }
            }
        }, confirmButton = { TextButton(enabled = chosen.isNotEmpty() && !busy, onClick = { vm.importSelected(chosen) }) { Text("Import selected") } },
            dismissButton = { TextButton(onClick = vm::cancelPreview) { Text("Cancel") } })
    }
    if (exportPreview != null) AlertDialog(onDismissRequest = { exportPreview = null }, title = { Text("Export selected contacts") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("${exportNames.size} selected records. This file can contain private phone numbers, notes and addresses.")
            exportNames.forEach { Text(it) }
            exportPreview!!.warnings.forEach { Text(it) }
            Text("FIC JSON preserves ancestry and associations. VCF/CSV may lose fields as listed above.")
        }
    }, confirmButton = { TextButton(onClick = { exportPicker.launch("FIC-selected-network.$exportFormat") }) { Text("Choose export file") } },
        dismissButton = { TextButton(onClick = { exportPreview = null }) { Text("Cancel") } })
    if (phoneConsent) AlertDialog(onDismissRequest = { phoneConsent = false }, title = { Text("Read phone contacts for preview") },
        text = { Text("FIC will read contacts only for this preview. It will not edit your phone address book or send records anywhere. Select records and confirm before saving a local copy.") },
        confirmButton = { TextButton(onClick = { phoneConsent = false; contactsPermission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Read for preview") } },
        dismissButton = { TextButton(onClick = { phoneConsent = false }) { Text("Cancel") } })
    csvBytes?.let { bytes -> CsvMappingDialog(bytes, onCancel = { csvBytes = null }) { mapping ->
        vm.readPreview({ bytes }, "csv", mapping); csvBytes = null
    } }
    if (placeDialog) PlaceDialog(place, onCancel = { placeDialog = false }) { place = it; placeSource = "manual selection"; placeDialog = false }
    detail?.let { item -> ContactDetailDialog(item, onClose = { detail = null }, onEdit = { editing = item; detail = null }, onDelete = { vm.delete(item.text("id")); detail = null }, onSave = vm::save) }
    editing?.let { item -> ContactEditor(item, onCancel = { editing = null }) { vm.save(it); editing = null } }
}

@Composable
internal fun ChoiceField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${value.ifBlank { "Not mapped" }.uppercase()}") }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option ->
            DropdownMenuItem(text = { Text(option.ifBlank { "Not mapped" }) }, onClick = { onChange(option); expanded = false })
        } }
    }
}

@Composable
private fun CsvMappingDialog(bytes: ByteArray, onCancel: () -> Unit, onConfirm: (Map<String, String>) -> Unit) {
    val columns = remember(bytes) { runCatching { NetworkCodec.parse(bytes, "csv").columns }.getOrDefault(emptyList()) }
    val canonical = listOf("display_name", "kind", "organization", "role", "phone", "email", "street", "neighborhood", "locality", "region", "postal_code", "country", "label", "notes")
    var mapping by remember { mutableStateOf(canonical.associateWith { key -> columns.firstOrNull { it.equals(key, true) }.orEmpty() }) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("CSV column mapping") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text("Map each retained field. Unmapped columns will not be imported; FIC JSON is the lossless format.")
            canonical.forEach { key -> ChoiceField(key, mapping[key].orEmpty(), listOf("") + columns) { mapping = mapping + (key to it) } }
        }
    }, confirmButton = { TextButton(enabled = columns.isNotEmpty(), onClick = { onConfirm(mapping.filterValues { it.isNotBlank() }) }) { Text("Parse preview") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}

@Composable
internal fun PlaceDialog(original: JsonObject, onCancel: () -> Unit, onConfirm: (JsonObject) -> Unit) {
    var fields by remember { mutableStateOf(listOf("latitude", "longitude", "street", "neighborhood", "locality", "region", "country").associateWith { original.text(it) }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Select a place") }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
            Text("Manual selection; not device GPS. Street/building matching needs locality and country. Coordinates are optional.")
            fields.forEach { (key, value) -> OutlinedTextField(value, { fields = fields + (key to it) }, label = { Text(key) }, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it) }
        }
    }, confirmButton = { TextButton(onClick = {
        val lat = fields["latitude"].orEmpty(); val lon = fields["longitude"].orEmpty()
        val a = lat.toDoubleOrNull(); val b = lon.toDoubleOrNull()
        if ((lat.isNotBlank() || lon.isNotBlank()) && (a == null || b == null || !a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0)) error = "Enter both valid coordinates, or leave both blank."
        else onConfirm(JsonObject().apply { fields.filterKeys { it != "latitude" && it != "longitude" }.forEach { (k, v) -> addProperty(k, v) }; if (a != null && b != null) { addProperty("latitude", a); addProperty("longitude", b) } })
    }) { Text("Use selected place") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
