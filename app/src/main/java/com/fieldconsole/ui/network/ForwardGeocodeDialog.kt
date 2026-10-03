package com.fieldconsole.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fieldconsole.location.AddressCandidate
import com.fieldconsole.location.ContactForwardGeocoder
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ForwardGeocodeDialog(entity: JsonObject, address: JsonObject, onCancel: () -> Unit, onSave: (JsonObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("External lookup is OFF until you explicitly send this address.") }
    var candidates by remember { mutableStateOf(emptyList<AddressCandidate>()) }
    val query = remember(address) { listOf("street", "locality", "region", "postal_code", "country").map { address.text(it) }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { address.text("original") } }
    AlertDialog(onDismissRequest = onCancel, title = { Text("Optional address lookup") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Provider: OpenCage — https://opencagedata.com")
            Text("Will send exactly this address: $query")
            Text("An address can be personal data even without a name. Only proceed if authorized to share it. No name, phone, email or notes are sent. Provider terms apply; a trial is for testing, not ongoing free use.")
            OutlinedTextField(key, { key = it }, label = { Text("Your OpenCage key (this dialog only)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text(message)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            candidates.forEach { candidate ->
                Card { Column(Modifier.padding(8.dp)) {
                    Text(candidate.formatted); Text("Derived ${candidate.precision} point: ${candidate.latitude}, ${candidate.longitude}")
                    TextButton(onClick = {
                        val updated = entity.deepCopy()
                        updated.array("associations").add(savedAssociation(address.text("id")).apply {
                            addProperty("source", "external_provider"); addProperty("provider", "OpenCage / OpenStreetMap contributors")
                            addProperty("geocode_status", "resolved"); addProperty("resolved_at", candidate.resolvedAt)
                            addProperty("verified_at", Instant.now().toString()); addProperty("normalized_address", candidate.formatted)
                            add("point", JsonObject().apply { addProperty("latitude", candidate.latitude); addProperty("longitude", candidate.longitude); addProperty("precision", candidate.precision) })
                        })
                        onSave(updated)
                    }) { Text("Confirm this saved association") }
                } }
            }
            Text("Candidates remain unresolved until selected. No result proves current presence. © OpenCage / OpenStreetMap contributors. No confidence percentage is inferred.")
        }
    }, confirmButton = { TextButton(enabled = !busy && key.isNotBlank() && query.length >= 2, onClick = {
        busy = true
        scope.launch {
            try {
                candidates = ContactForwardGeocoder.shared.lookup(query, key, consent = true)
                message = if (candidates.isEmpty()) "No address matches. Original address remains unresolved." else "Review and deliberately select a candidate; ambiguous matches are not auto-saved."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Provider lookup failed. No association was changed." }
            finally { busy = false }
        }
    }) { Text("Send address to OpenCage") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel / close") } })
}
