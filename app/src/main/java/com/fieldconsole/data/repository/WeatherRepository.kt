package com.fieldconsole.data.repository

import com.fieldconsole.data.local.dao.WeatherDao
import com.fieldconsole.data.local.entity.WeatherData
import com.fieldconsole.data.remote.weather.OpenMeteoApi
import com.fieldconsole.data.remote.weather.OpenMeteoResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WeatherRepository @Inject constructor(
    private val api:  OpenMeteoApi,
    private val dao:  WeatherDao,
    private val gson: Gson
) {
    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L  // 30 minutes
    }

    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherData> =
        withContext(Dispatchers.IO) {
            val key = cacheKey(lat, lon)
            val cached = dao.getByKey(key)
            if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
                return@withContext Result.success(cached)
            }
            try {
                val response = api.getForecast(lat = lat, lon = lon)
                val data = mapToEntity(key, lat, lon, response)
                dao.upsert(data)
                Result.success(data)
            } catch (e: Exception) {
                if (cached != null) Result.success(cached)
                else Result.failure(e)
            }
        }

    private fun cacheKey(lat: Double, lon: Double) =
        "%.2f_%.2f".format(lat, lon)

    private fun mapToEntity(key: String, lat: Double, lon: Double, r: OpenMeteoResponse): WeatherData {
        val cur = r.current
        val daily = r.daily

        val sunriseEpoch = daily?.sunrise?.firstOrNull()?.let { parseIso(it) } ?: 0L
        val sunsetEpoch  = daily?.sunset?.firstOrNull()?.let  { parseIso(it) } ?: 0L
        val moonPhase    = daily?.moon_phase?.firstOrNull() ?: 0f

        val hourlyJson = gson.toJson(r.hourly)
        val dailyJson  = gson.toJson(r.daily)

        val rating = operationalRating(cur?.weather_code ?: 0, cur?.wind_speed_10m ?: 0f, cur?.visibility ?: 10000f)
        val impact = weatherImpact(cur?.weather_code ?: 0)

        return WeatherData(
            cacheKey              = key,
            latitude              = lat,
            longitude             = lon,
            fetchedAt             = System.currentTimeMillis(),
            tempC                 = cur?.temperature_2m ?: 0f,
            feelsLikeC            = cur?.apparent_temperature ?: 0f,
            humidity              = cur?.relative_humidity_2m ?: 0,
            pressureHpa           = cur?.surface_pressure ?: 0f,
            windSpeedKmh          = cur?.wind_speed_10m ?: 0f,
            windDirectionDeg      = cur?.wind_direction_10m ?: 0,
            visibilityKm          = (cur?.visibility ?: 10000f) / 1000f,
            cloudCoverPct         = cur?.cloud_cover ?: 0,
            rainProbabilityPct    = cur?.precipitation_probability ?: 0,
            uvIndex               = cur?.uv_index ?: 0f,
            weatherCode           = cur?.weather_code ?: 0,
            weatherDescription    = wmoDescription(cur?.weather_code ?: 0),
            sunriseEpoch          = sunriseEpoch,
            sunsetEpoch           = sunsetEpoch,
            moonPhase             = moonPhase,
            precipitationMm       = daily?.precipitation_sum?.firstOrNull() ?: 0f,
            operationalRating     = rating,
            weatherImpact         = impact,
            hourlyForecastJson    = hourlyJson,
            dailyForecastJson     = dailyJson
        )
    }

    private fun parseIso(s: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s)?.time ?: 0L
    } catch (e: Exception) { 0L }

    private fun operationalRating(code: Int, windKmh: Float, visM: Float): String = when {
        code in 95..99 || windKmh > 80 || visM < 200  -> "DANGEROUS"
        code in 61..82 || windKmh > 50 || visM < 1000 -> "POOR"
        code in 51..57 || windKmh > 30 || visM < 5000 -> "MODERATE"
        code in 1..3   || windKmh > 15                -> "GOOD"
        else                                           -> "EXCELLENT"
    }

    private fun weatherImpact(code: Int): String = when (code) {
        0     -> "Clear sky — optimal field conditions."
        1,2,3 -> "Partly cloudy — generally good conditions."
        in 45..48 -> "Fog — reduced visibility, exercise caution."
        in 51..57 -> "Drizzle — light precipitation, minor impact."
        in 61..67 -> "Rain — wet conditions, reduced traction."
        in 71..77 -> "Snow — hazardous conditions, limited mobility."
        in 80..82 -> "Rain showers — intermittent, plan accordingly."
        in 95..99 -> "Thunderstorm — dangerous, seek shelter immediately."
        else      -> "Variable conditions — monitor closely."
    }

    private fun wmoDescription(code: Int): String = when (code) {
        0     -> "Clear sky"
        1     -> "Mainly clear"
        2     -> "Partly cloudy"
        3     -> "Overcast"
        45,48 -> "Fog"
        51,53,55 -> "Drizzle"
        61,63,65 -> "Rain"
        71,73,75 -> "Snow"
        77    -> "Snow grains"
        80,81,82 -> "Rain showers"
        85,86 -> "Snow showers"
        95    -> "Thunderstorm"
        96,99 -> "Thunderstorm with hail"
        else  -> "Unknown ($code)"
    }
}
