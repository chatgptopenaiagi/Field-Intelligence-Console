package com.fieldconsole.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.data.emergency.EmergencyNumber
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.intent.EmergencyDialer
import com.fieldconsole.location.GeoContext
import com.fieldconsole.viewmodel.EmergencyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    geo:        GeoContext,
    dispatcher: IntentDispatcher,
    onDismiss:  () -> Unit,
    vm:         EmergencyViewModel = hiltViewModel()
) {
    val state   = vm.state.collectAsState().value
    val context = LocalContext.current

    LaunchedEffect(geo) { vm.load(geo.latitude, geo.longitude, geo.countryCode ?: "INT") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null,
                             tint = Color(0xFFCC0000), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("EMERGENCY", style = MaterialTheme.typography.titleLarge,
                                 fontWeight = FontWeight.Black, color = Color(0xFFCC0000))
                            Text(geo.shortLabel, style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Quick navigation buttons ──────────────────────────────────────
            Text("Quick Actions", style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { dispatcher.emergencyNavigation(context, geo) },
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick  = { dispatcher.copyLocation(context, geo) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy GPS", fontSize = 12.sp)
                }
            }

            // ── Emergency numbers ─────────────────────────────────────────────
            Text("Emergency Numbers — ${state.countryCode}",
                 style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (state.numbers.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                state.numbers.forEach { num ->
                    EmergencyNumberCard(num)
                }
            }

            // ── Nearby services ───────────────────────────────────────────────
            Text("Nearby Emergency Services",
                 style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (state.isLoading && state.services.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Searching nearby services via OpenStreetMap…",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            state.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(listOfNotNull(err, state.staleWarning).joinToString("\n"), modifier = Modifier.padding(12.dp),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (state.showNoResults) {
                Text("No emergency services found in 5 km radius.",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            state.services.forEach { svc ->
                EmergencyServiceCard(svc)
            }

            // ── Data source ───────────────────────────────────────────────────
            Text(
                text  = "Services: OpenStreetMap via Overpass API. Locations may be area centers, not entrances. Verify access before travel.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EmergencyNumberCard(num: EmergencyNumber) {
    val context = LocalContext.current
    val catColor = when (num.category) {
        EmergencyNumber.Category.POLICE          -> Color(0xFF1565C0)
        EmergencyNumber.Category.FIRE            -> Color(0xFFB71C1C)
        EmergencyNumber.Category.AMBULANCE       -> Color(0xFF1B5E20)
        EmergencyNumber.Category.MEDICAL_ADVICE  -> Color(0xFF2E7D32)
        EmergencyNumber.Category.PHARMACY        -> Color(0xFF4A148C)
        EmergencyNumber.Category.POISON          -> Color(0xFF880E4F)
        EmergencyNumber.Category.GAS             -> Color(0xFFE65100)
        EmergencyNumber.Category.GENERAL         -> Color(0xFF37474F)
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = catColor, shape = MaterialTheme.shapes.small) {
                Text(
                    text     = num.number,
                    color    = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(num.label, fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.bodyMedium)
                Text(num.description, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = {
                    EmergencyDialer.open(context, num.number)
                }) {
                    Icon(Icons.Default.Call, "Call", tint = Color(0xFF2E7D32))
                }
                IconButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("number", num.number))
                }) {
                    Icon(Icons.Default.ContentCopy, "Copy")
                }
            }
        }
    }
}

@Composable
private fun EmergencyServiceCard(svc: EmergencyService) {
    val context = LocalContext.current
    val typeIcon = when (svc.type) {
        "HOSPITAL"       -> Icons.Default.LocalHospital
        "POLICE"         -> Icons.Default.LocalPolice
        "FIRE"           -> Icons.Default.LocalFireDepartment
        "PHARMACY"       -> Icons.Default.MedicalServices
        "AED"            -> Icons.Default.FavoriteBorder
        "SHELTER"        -> Icons.Default.Home
        else             -> Icons.Default.Place
    }
    val typeColor = when (svc.type) {
        "HOSPITAL"  -> Color(0xFF1B5E20)
        "POLICE"    -> Color(0xFF1565C0)
        "FIRE"      -> Color(0xFFB71C1C)
        "PHARMACY"  -> Color(0xFF4A148C)
        "AED"       -> Color(0xFFE65100)
        else        -> Color(0xFF37474F)
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(svc.name, fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.weight(1f))
                if (svc.is24h) {
                    Surface(color = Color(0xFF2E7D32), shape = MaterialTheme.shapes.extraSmall) {
                        Text("24h", color = Color.White, fontSize = 10.sp,
                             modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            if (svc.address.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(svc.address, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%.0f m".format(svc.distanceM),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.primary)
                Text("~${svc.travelTimeMin} min",
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (svc.phone.isNotBlank()) {
                    TextButton(onClick = {
                        EmergencyDialer.open(context, svc.phone)
                    }) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Call")
                    }
                }
                TextButton(onClick = {
                    val uri = Uri.parse("geo:${svc.lat},${svc.lon}?q=${svc.lat},${svc.lon}(${svc.name})")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) {
                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
            }
        }
    }
}
