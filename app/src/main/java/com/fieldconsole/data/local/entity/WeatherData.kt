package com.fieldconsole.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * WeatherData — cached weather snapshot for a given location.
 *
 * Populated from Open-Meteo API (free, no API key required).
 * Cached per coordinate rounded to 2 decimal places to avoid redundant calls.
 */
@Entity(tableName = "weather_cache")
data class WeatherData(
    @PrimaryKey
    val cacheKey:          String  = "",   // "lat2dp_lon2dp"
    val latitude:          Double  = 0.0,
    val longitude:         Double  = 0.0,
    val fetchedAt:         Long    = System.currentTimeMillis(),

    // Current conditions
    val tempC:             Float   = 0f,
    val feelsLikeC:        Float   = 0f,
    val humidity:          Int     = 0,    // %
    val pressureHpa:       Float   = 0f,
    val windSpeedKmh:      Float   = 0f,
    val windDirectionDeg:  Int     = 0,
    val visibilityKm:      Float   = 0f,
    val cloudCoverPct:     Int     = 0,
    val rainProbabilityPct:Int     = 0,
    val uvIndex:           Float   = 0f,
    val weatherCode:       Int     = 0,    // WMO weather code
    val weatherDescription:String  = "",

    // Daily
    val sunriseEpoch:      Long    = 0L,
    val sunsetEpoch:       Long    = 0L,
    val moonPhase:         Float   = 0f,   // 0.0–1.0
    val precipitationMm:   Float   = 0f,

    // Air quality (Open-Meteo air quality API)
    val airQualityIndex:   Int     = -1,   // -1 = not available
    val pm25:              Float   = 0f,
    val pm10:              Float   = 0f,

    // Operational assessment
    val operationalRating: String  = "",   // EXCELLENT | GOOD | MODERATE | POOR | DANGEROUS
    val weatherImpact:     String  = "",   // Human-readable impact summary

    // Hourly forecast JSON (serialized list)
    val hourlyForecastJson:String  = "",
    // 7-day forecast JSON (serialized list)
    val dailyForecastJson: String  = ""
)
