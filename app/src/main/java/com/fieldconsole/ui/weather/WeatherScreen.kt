package com.fieldconsole.ui.weather

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.data.local.entity.WeatherData
import com.fieldconsole.location.GeoContext
import com.fieldconsole.viewmodel.WeatherUiState
import com.fieldconsole.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    geo:    GeoContext,
    onBack: () -> Unit,
    vm:     WeatherViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(geo) { vm.load(geo.latitude, geo.longitude) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WEATHER", style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold)
                        Text(geo.shortLabel, style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(geo.latitude, geo.longitude) }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is WeatherUiState.Idle    -> {}
                is WeatherUiState.Loading -> WeatherSkeleton()
                is WeatherUiState.Success -> WeatherContent(s.data)
                is WeatherUiState.Error   -> WeatherError(s.message) { vm.load(geo.latitude, geo.longitude) }
            }
        }
    }
}

@Composable
private fun WeatherContent(w: WeatherData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Current conditions hero card ──────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text       = "${w.tempC.toInt()}°C",
                    fontSize   = 64.sp,
                    fontWeight = FontWeight.Black,
                    color      = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text  = w.weatherDescription,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Feels like ${w.feelsLikeC.toInt()}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                // Operational rating badge
                val ratingColor = when (w.operationalRating) {
                    "EXCELLENT" -> Color(0xFF2E7D32)
                    "GOOD"      -> Color(0xFF388E3C)
                    "MODERATE"  -> Color(0xFFF57F17)
                    "POOR"      -> Color(0xFFE65100)
                    else        -> Color(0xFFB71C1C)
                }
                Surface(
                    color  = ratingColor,
                    shape  = MaterialTheme.shapes.small
                ) {
                    Text(
                        text     = "OPERATIONAL: ${w.operationalRating}",
                        color    = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style    = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = w.weatherImpact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        // ── Details grid ──────────────────────────────────────────────────────
        Text("Current Conditions", style = MaterialTheme.typography.titleSmall,
             fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        val details = listOf(
            Triple(Icons.Default.WaterDrop,   "Humidity",     "${w.humidity}%"),
            Triple(Icons.Default.Speed,        "Pressure",     "${w.pressureHpa.toInt()} hPa"),
            Triple(Icons.Default.Air,          "Wind",         "${w.windSpeedKmh.toInt()} km/h ${bearingLabel(w.windDirectionDeg)}"),
            Triple(Icons.Default.Visibility,   "Visibility",   "%.1f km".format(w.visibilityKm)),
            Triple(Icons.Default.Cloud,        "Cloud Cover",  "${w.cloudCoverPct}%"),
            Triple(Icons.Default.Umbrella,     "Rain Prob.",   "${w.rainProbabilityPct}%"),
            Triple(Icons.Default.WbSunny,      "UV Index",     "%.1f".format(w.uvIndex)),
            Triple(Icons.Default.Grain,        "Precip.",      "%.1f mm".format(w.precipitationMm))
        )

        details.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, value) ->
                    WeatherDetailCard(icon, label, value, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ── Sun / Moon ────────────────────────────────────────────────────────
        Text("Sun & Moon", style = MaterialTheme.typography.titleSmall,
             fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherDetailCard(Icons.Default.WbTwilight, "Sunrise",
                formatTime(w.sunriseEpoch), Modifier.weight(1f))
            WeatherDetailCard(Icons.Default.NightsStay, "Sunset",
                formatTime(w.sunsetEpoch), Modifier.weight(1f))
            WeatherDetailCard(Icons.Default.Brightness3, "Moon Phase",
                moonPhaseLabel(w.moonPhase), Modifier.weight(1f))
        }

        // ── Air quality ───────────────────────────────────────────────────────
        if (w.airQualityIndex >= 0) {
            Text("Air Quality", style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherDetailCard(Icons.Default.Air, "AQI",
                    "${w.airQualityIndex} — ${aqiLabel(w.airQualityIndex)}", Modifier.weight(1f))
                WeatherDetailCard(Icons.Default.Grain, "PM2.5",
                    "%.1f µg/m³".format(w.pm25), Modifier.weight(1f))
                WeatherDetailCard(Icons.Default.Grain, "PM10",
                    "%.1f µg/m³".format(w.pm10), Modifier.weight(1f))
            }
        }

        // ── Data source note ──────────────────────────────────────────────────
        Text(
            text  = "Source: Open-Meteo (open-meteo.com) — No API key required",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun WeatherDetailCard(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    label:    String,
    value:    String,
    modifier: Modifier = Modifier
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp),
                     tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeatherSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Card(
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            ) {}
        }
    }
}

@Composable
private fun WeatherError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
                 modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatTime(epochMs: Long): String {
    if (epochMs == 0L) return "—"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun bearingLabel(deg: Int): String {
    val dirs = listOf("N","NE","E","SE","S","SW","W","NW")
    return dirs[((deg + 22.5) / 45).toInt() % 8]
}

private fun moonPhaseLabel(phase: Float): String = when {
    phase < 0.03f || phase > 0.97f -> "New Moon"
    phase < 0.22f -> "Waxing Crescent"
    phase < 0.28f -> "First Quarter"
    phase < 0.47f -> "Waxing Gibbous"
    phase < 0.53f -> "Full Moon"
    phase < 0.72f -> "Waning Gibbous"
    phase < 0.78f -> "Last Quarter"
    else          -> "Waning Crescent"
}

private fun aqiLabel(aqi: Int): String = when {
    aqi <= 50  -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy (Sensitive)"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very Unhealthy"
    else       -> "Hazardous"
}
