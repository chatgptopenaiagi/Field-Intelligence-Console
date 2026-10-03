package com.fieldconsole.ui.network

import com.fieldconsole.network.NetworkCodec

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Composable
internal fun ContactEditor(original: JsonObject, onCancel: () -> Unit, onSave: (JsonObject) -> Unit) {
    var draft by remember(original) { mutableStateOf(original.deepCopy()) }
    fun set(key: String, value: String) { draft = draft.deepCopy().apply { addProperty(key, value) } }
    fun arraySet(key: String, values: JsonArray) { draft = draft.deepCopy().apply { add(key, values) } }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Edit local contact") }, text = {
        Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Local record. Saved addresses do not establish current residence or presence.")
            ChoiceField("Kind", draft.text("kind"), listOf("person", "organization")) { set("kind", it) }
            listOf("display_name", "organization", "role", "notes").forEach { key ->
                OutlinedTextField(draft.text(key), { if (it.length <= 16_384) set(key, it) }, label = { Text(key.replace('_', ' ')) }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(draft.array("tags").joinToString(", ") { it.asString }, { value ->
                arraySet("tags", JsonArray().apply { value.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(100).forEach { add(it) } })
            }, label = { Text("User tags (comma separated)") })
            listOf("phones", "emails").forEach { key ->
                Text(key.uppercase())
                draft.array(key).forEachIndexed { index, entry ->
                    val item = entry.asJsonObject
                    Row {
                        OutlinedTextField(item.text("label"), { value ->
                            arraySet(key, draft.array(key).deepCopy().apply { get(index).asJsonObject.addProperty("label", value) })
                        }, label = { Text("Label") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(item.text("value"), { value ->
                            arraySet(key, draft.array(key).deepCopy().apply { get(index).asJsonObject.apply {
                                addProperty("value", value)
                                addProperty("normalized", if (key == "emails") value.trim().lowercase(Locale.ROOT) else NetworkCodec.normalizePhone(value))
                            } })
                        }, label = { Text("Original value") }, modifier = Modifier.weight(2f))
                    }
                    TextButton(onClick = { arraySet(key, draft.array(key).deepCopy().apply { remove(index) }) }) { Text("Remove value") }
                }
                TextButton(onClick = { arraySet(key, draft.array(key).deepCopy().apply { add(JsonObject().apply { addProperty("label", "unspecified"); addProperty("value", ""); addProperty("normalized", "") }) }) }) { Text("Add ${if (key == "phones") "phone" else "email"}") }
            }
            Text("SAVED ADDRESSES")
            draft.array("addresses").forEachIndexed { index, entry ->
                val address = entry.asJsonObject
                Text("Address ${index + 1}")
                listOf("label", "original", "street", "neighborhood", "locality", "region", "postal_code", "country").forEach { field ->
                    OutlinedTextField(address.text(field), { value ->
                        val updated = draft.deepCopy()
                        updated.array("addresses")[index].asJsonObject.addProperty(field, value)
                        // A corrected address cannot silently inherit an old geocoded point.
                        updated.array("associations").filter { it.asJsonObject.text("address_id") == address.text("id") && it.asJsonObject.text("source") == "external_provider" }.forEach {
                            it.asJsonObject.apply { add("point", JsonNull.INSTANCE); addProperty("geocode_status", "unresolved"); add("resolved_at", JsonNull.INSTANCE) }
                        }
                        draft = updated
                    }, label = { Text(field.replace('_', ' ')) }, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = {
                    val updated = draft.deepCopy(); updated.array("addresses").remove(index)
                    updated.add("associations", JsonArray().apply { draft.array("associations").filter { it.asJsonObject.text("address_id") != address.text("id") }.forEach { add(it.deepCopy()) } })
                    draft = updated
                }) { Text("Remove address and its associations") }
            }
            TextButton(onClick = {
                val id = UUID.randomUUID().toString()
                val updated = draft.deepCopy()
                updated.array("addresses").add(JsonObject().apply {
                    addProperty("id", id)
                    listOf("original", "po_box", "extended", "street", "neighborhood", "locality", "region", "postal_code", "country").forEach { addProperty(it, "") }
                    addProperty("label", "unspecified")
                })
                updated.array("associations").add(savedAssociation(id))
                draft = updated
            }) { Text("Add saved address") }
        }
    }, confirmButton = { TextButton(enabled = draft.text("display_name").isNotBlank() || draft.text("organization").isNotBlank(), onClick = {
        val saved = draft.deepCopy()
        saved.array("corrections").add(JsonObject().apply { addProperty("at", Instant.now().toString()); addProperty("source", "user_observation"); addProperty("action", "local edit") })
        onSave(saved)
    }) { Text("Save locally") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}

internal fun savedAssociation(addressId: String?): JsonObject = JsonObject().apply {
    addProperty("id", UUID.randomUUID().toString())
    if (addressId == null) add("address_id", JsonNull.INSTANCE) else addProperty("address_id", addressId)
    addProperty("basis", "saved_address"); addProperty("source", "user_observation"); addProperty("temporal_status", "saved")
    addProperty("geocode_status", "unresolved")
    listOf("point", "provider", "resolved_at", "verified_at", "normalized_address").forEach { add(it, JsonNull.INSTANCE) }
}

@Composable
internal fun ContactDetailDialog(entity: JsonObject, onClose: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSave: (JsonObject) -> Unit) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }
    var manualPoint by remember { mutableStateOf(false) }
    var geocodeAddress by remember { mutableStateOf<JsonObject?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(entity.text("display_name").ifBlank { "Contact details" }) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entity.text("organization")); Text(entity.text("role")); Text(entity.text("notes"))
            Text("Saved information only. No current-presence evidence.")
            entity.array("ancestry").forEach { source -> Text("Source: ${source.asJsonObject.text("format")} · imported ${source.asJsonObject.text("imported_at")} · source update ${source.asJsonObject.text("updated_at").ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall) }
            entity.array("phones").forEach { phone -> TextButton(onClick = { openControlled(context, "tel", phone.asJsonObject.text("value")) }) { Text("Dial ${phone.asJsonObject.text("label")}: ${phone.asJsonObject.text("value")}") } }
            entity.array("emails").forEach { email -> TextButton(onClick = { openControlled(context, "mailto", email.asJsonObject.text("value")) }) { Text("Email ${email.asJsonObject.text("value")}") } }
            entity.array("addresses").forEach { entry ->
                val address = entry.asJsonObject
                Text("${address.text("label")} saved address: ${address.text("original").ifBlank { listOf("street", "locality", "region", "country").map { address.text(it) }.filter { it.isNotBlank() }.joinToString(", ") }}")
                TextButton(onClick = { geocodeAddress = address }) { Text("Review optional address lookup") }
            }
            entity.array("associations").forEach { element ->
                val association = element.asJsonObject
                val point = association.get("point")?.takeIf { it.isJsonObject }?.asJsonObject
                Text("${association.text("basis")} · ${association.text("source")} · ${point?.text("precision") ?: "text only / unresolved"} · ${association.text("temporal_status")}", style = MaterialTheme.typography.bodySmall)
                Text("Provider: ${association.text("provider").ifBlank { "none" }} · resolved ${association.text("resolved_at").ifBlank { "unknown" }} · confirmed ${association.text("verified_at").ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall)
                if (point != null) TextButton(onClick = { openControlled(context, "map", "${point.text("latitude")},${point.text("longitude")}") }) { Text("Open saved point on map") }
            }
            TextButton(onClick = { manualPoint = true }) { Text("Add manual saved point") }
            TextButton(onClick = { deleting = true }) { Text("Delete local contact") }
        }
    }, confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete local contact?") }, text = { Text("Removes this local record and its derived associations. Original files, phone contacts and previous exports are unaffected.") }, confirmButton = { TextButton(onClick = onDelete) { Text("Delete locally") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    if (manualPoint) ManualPointDialog(onCancel = { manualPoint = false }) { point ->
        val updated = entity.deepCopy()
        updated.array("associations").add(savedAssociation(null).apply {
            addProperty("basis", "manual_location"); add("point", point); addProperty("verified_at", Instant.now().toString())
        })
        onSave(updated); manualPoint = false; onClose()
    }
    geocodeAddress?.let { address -> ForwardGeocodeDialog(entity, address, onCancel = { geocodeAddress = null }, onSave = { onSave(it); geocodeAddress = null; onClose() }) }
}

@Composable
private fun ManualPointDialog(onCancel: () -> Unit, onConfirm: (JsonObject) -> Unit) {
    var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }
    var precision by remember { mutableStateOf("approximate") }; var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Manual saved association") }, text = { Column {
        Text("This is an association with a saved point, not current whereabouts. Select only the precision you can support.")
        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }); OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") })
        ChoiceField("Precision", precision, listOf("building", "street", "locality", "region", "country", "approximate")) { precision = it }
        if (error) Text("Both coordinates must be finite and within range.")
    } }, confirmButton = { TextButton(onClick = {
        val a = lat.toDoubleOrNull(); val b = lon.toDoubleOrNull()
        if (a == null || b == null || !a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0) error = true
        else onConfirm(JsonObject().apply { addProperty("latitude", a); addProperty("longitude", b); addProperty("precision", precision) })
    }) { Text("Save association") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
