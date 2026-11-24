package com.fieldconsole.ui.emergency

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldconsole.R
import com.fieldconsole.data.emergency.CountryBoundary
import com.fieldconsole.data.emergency.EmergencyNumber.Category
import com.fieldconsole.data.emergency.EmergencyNumbers
import com.fieldconsole.intent.EmergencyDialer
import com.fieldconsole.location.GeoContext
import com.fieldconsole.location.RegionalLanguage
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
private fun currentTime(): Long {
    val clock by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    return clock
}

@Composable
fun MeasurementDetails(geo: GeoContext) {
    val now = currentTime()
    val age = if (!geo.manual && geo.timestamp in 1..now) (now - geo.timestamp) / 1000 else null
    val status = when {
        geo.manual -> R.string.manual_point
        geo.saved -> R.string.saved_measurement
        age == null -> R.string.unknown
        now - geo.timestamp <= 120_000 -> R.string.recent_measurement
        else -> R.string.stale_measurement
    }
    Text(stringResource(status) + " · " + geo.measuredAt, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.measurement_detail, age?.toString() ?: "UNKNOWN", geo.provider ?: "UNKNOWN"),
        style = MaterialTheme.typography.bodySmall)
    val code = remember(geo, age != null && now - geo.timestamp <= 120_000) { CountryBoundary.current(geo, now) }
    Text(stringResource(R.string.country_label) + " " + (code?.let { Locale("", it).displayCountry + " ($it)" }
        ?: stringResource(R.string.unknown)), style = MaterialTheme.typography.bodySmall)
    listOfNotNull(geo.region, geo.city).distinct().joinToString(" · ").takeIf { it.isNotBlank() }?.let {
        Text(stringResource(R.string.geocoder_label) + " " + it, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RegionalPanel(geo: GeoContext?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fic_regional", Context.MODE_PRIVATE) }
    var manual by remember { mutableStateOf(prefs.getString("manual_country", null)) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val now = currentTime()
    val detected = remember(geo, geo != null && now - geo.timestamp in 0..120_000) { CountryBoundary.current(geo, now) }
    val code = manual ?: detected
    val unknown = stringResource(R.string.unknown)
    Text(stringResource(R.string.country_label) + " " + (code?.let { Locale("", it).displayCountry + " ($it)" } ?: unknown))
    Text(stringResource(if (manual != null) R.string.manual_country else if (detected != null) R.string.detected_country else R.string.confirm_country))
    if (manual == null && detected != null && detected == geo?.countryCode)
        Text(stringResource(R.string.region_label) + " " + (geo.region ?: unknown))
    TextButton(onClick = { input = code.orEmpty(); editing = true }) { Text(stringResource(R.string.select_country)) }
    if (editing) {
        val valid = input in Locale.getISOCountries()
        OutlinedTextField(input, { input = it.take(2).uppercase(Locale.ROOT) }, singleLine = true,
            label = { Text(stringResource(R.string.iso_country)) })
        if (valid) Text(Locale("", input).displayCountry)
        Row {
            TextButton(enabled = valid, onClick = {
                manual = input
                prefs.edit().putString("manual_country", input).apply()
                editing = false
                RegionalLanguage.refreshAutomatic(context)
            }) { Text(stringResource(R.string.confirm_selection)) }
            TextButton(onClick = {
                manual = null
                prefs.edit().remove("manual_country").apply()
                editing = false
                RegionalLanguage.refreshAutomatic(context)
            }) { Text(stringResource(R.string.use_location)) }
        }
    }
    val records = remember(code) { EmergencyNumbers.forCountry(code.orEmpty()) }
    if (records.isEmpty()) Text(stringResource(R.string.unverified))
    fun categoryLabel(category: Category): Int = when (category) {
            Category.POLICE -> R.string.police
            Category.AMBULANCE -> R.string.ambulance
            Category.FIRE -> R.string.fire
            Category.GENERAL -> R.string.general_emergency
            Category.MEDICAL_ADVICE -> R.string.medical_advice
            Category.POISON -> R.string.poison
        }
    if (records.isNotEmpty()) {
        val missing = listOf(Category.POLICE, Category.AMBULANCE, Category.FIRE, Category.MEDICAL_ADVICE)
            .filter { category -> records.none { it.category == category } }
        if (missing.isNotEmpty()) {
            val labels = mutableListOf<String>()
            for (category in missing) labels.add(stringResource(categoryLabel(category)))
            Text(stringResource(R.string.unverified) + " · " + labels.joinToString(", "))
        }
    }
    records.forEach { record ->
        val label = stringResource(categoryLabel(record.category))
        Column {
            OutlinedButton(onClick = { EmergencyDialer.open(context, record.number) }) { Text("$label · ${record.number}") }
            Text(stringResource(R.string.verified_source, record.verified, record.source), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RegionalSettings(onClose: () -> Unit, geo: GeoContext?) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(RegionalLanguage.mode(context)) }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.regional_settings)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.language))
            for ((id,label) in listOf("auto" to stringResource(R.string.auto_language), "de" to "Deutsch", "ro" to "Română", "en" to "English")) {
                TextButton(onClick = { mode = id; RegionalLanguage.select(context, id) }) { Text((if (mode == id) "✓ " else "") + label) }
            }
            HorizontalDivider()
            RegionalPanel(geo)
        } }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.ok)) } })
}
