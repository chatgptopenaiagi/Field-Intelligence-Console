package com.fieldconsole.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fieldconsole.intent.MatrixHandoff
import com.fieldconsole.data.emergency.CountryBoundary
import com.fieldconsole.location.GeoContext
import com.fieldconsole.prompt.HumanContext

@Composable
private fun Picker(label: String, choices: List<Pair<String, String>>, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            choices.forEach { (key, value) ->
                DropdownMenuItem(text = { Text(value) }, onClick = { open = false; select(key) })
            }
        }
    }
}

@Composable
internal fun HumanAnalysis(geo: GeoContext, initialExcerpt: String, selectedNote: String, close: () -> Unit, emergency: () -> Unit) {
    val context = LocalContext.current
    val lang = context.resources.configuration.locales[0].language.takeIf { it in setOf("ro", "de", "en") } ?: "en"
    val catalog = remember(lang) { HumanContext(context, lang) }
    val t = catalog::text
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var question by rememberSaveable { mutableStateOf(t("default")) }
    var category by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var disclosure by rememberSaveable { mutableStateOf("none") }
    var details by rememberSaveable { mutableStateOf(initialExcerpt.isNotEmpty()) }
    var observation by rememberSaveable { mutableStateOf("") }
    var excerpt by rememberSaveable { mutableStateOf(initialExcerpt) }
    var target by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf("") }
    val back = { if (preview != null) { preview = null; status = "" } else if (browsing) browsing = false else close() }
    BackHandler(onBack = back)
    val urgent = id in catalog.questions.take(10).map { it.getString("id") }
    val override = context.getSharedPreferences("fic_regional", Context.MODE_PRIVATE).getString("manual_country", null)
    val detected = remember(geo) { geo.countryCode ?: CountryBoundary.interior(geo.latitude, geo.longitude) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text(t("back")) }
            if (urgent) TextButton(onClick = emergency) { Text(t("exit")) }
            if (preview != null) TextButton(onClick = close) { Text(t("cancel")) }
        }
        if (urgent) {
            Text(t("emergency"), color = MaterialTheme.colorScheme.error)
            if (detected != null && override != null && !detected.equals(override, true))
                Text(t("mismatch") + " " + t("detected") + " · " + detected + " / " + t("override") + " · " + override)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val reviewed = preview
            if (reviewed != null) {
                OutlinedTextField(value = reviewed, onValueChange = { preview = it; status = "" },
                    label = { Text(t("edit")) }, modifier = Modifier.fillMaxWidth(), minLines = 6, maxLines = 12)
                val valid = MatrixHandoff.reviewable(reviewed)
                if (!valid) Text(t("limit"))
                Button(enabled = valid, onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("FIC", reviewed))
                    status = t("copied")
                }) { Text(t("copy")) }
                OutlinedButton(enabled = valid, onClick = {
                    status = t(if (MatrixHandoff.launch(context, MatrixHandoff.reviewedIntent(reviewed))) "verify" else "fallback")
                }) { Text(t("share")) }
                if (status.isNotEmpty()) Text(status)
            } else if (browsing) {
                Text(t("catalog"), style = MaterialTheme.typography.headlineSmall)
                val choices = listOf("" to t("all")) + catalog.categories.map { it.getString("id") to catalog.local(it, "title") }
                Picker(choices.first { it.first == category }.second, choices) { category = it }
                OutlinedTextField(search, { search = it }, label = { Text(t("search")) }, singleLine = true)
                val matches = catalog.questions.filter {
                    (category.isEmpty() || it.getString("category") == category) &&
                        listOf(it.getString("id"), catalog.local(it,"title"), catalog.local(it,"question")).any { s -> s.contains(search.trim(), true) }
                }
                if (matches.isEmpty()) Text(t("noMatch"))
                matches.forEach { q ->
                    TextButton(onClick = {
                        id = q.getString("id"); question = catalog.local(q,"question"); browsing = false
                    }) { Text(q.getString("id") + " · " + catalog.local(q,"title")) }
                }
            } else {
                Text(id ?: t("custom"), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { browsing = true }) { Text(t("catalog")) }
                if (id != null) TextButton(onClick = { id = null; question = t("default") }) { Text(t("custom")) }
                OutlinedTextField(question, { question = it }, label = { Text(t("question")) }, modifier = Modifier.fillMaxWidth())
                Picker(t(disclosure), listOf("none","region","exact").map { it to t(it) }) { disclosure = it }
                if (id == "Q30") OutlinedTextField(target, { target = it }, label = { Text(t("target")) })
                TextButton(onClick = { details = !details }) { Text(t("details")) }
                if (details) {
                    if (selectedNote.isNotBlank()) TextButton(onClick = { observation = selectedNote }) { Text(t("useNote")) }
                    OutlinedTextField(observation, { observation = it }, label = { Text(t("notes")) }, modifier = Modifier.fillMaxWidth())
                    Text(t("evidenceHint"))
                    OutlinedTextField(excerpt, { excerpt = it }, label = { Text(t("earth")) }, modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = question.isNotBlank(), onClick = {
                    preview = catalog.compose(id, question, geo, override, disclosure, observation, excerpt, target)
                }) { Text(t("preview")) }
            }
        }
    }
}
