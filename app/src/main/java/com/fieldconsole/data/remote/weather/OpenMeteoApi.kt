package com.fieldconsole.data.remote.weather

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo API — free weather API, no API key required.
 * Docs: https://open-meteo.com/en/docs
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude")              lat: Double,
        @Query("longitude")             lon: Double,
        @Query("current")               current: String = CURRENT_VARS,
        @Query("hourly")                hourly: String  = HOURLY_VARS,
        @Query("daily")                 daily: String   = DAILY_VARS,
        @Query("timezone")              timezone: String = "auto",
        @Query("forecast_days")         forecastDays: Int = 7,
        @Query("wind_speed_unit")       windUnit: String = "kmh",
        @Query("temperature_unit")      tempUnit: String = "celsius"
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        const val CURRENT_VARS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "weather_code,surface_pressure,wind_speed_10m,wind_direction_10m," +
            "visibility,cloud_cover,precipitation_probability,uv_index"

        const val HOURLY_VARS = "temperature_2m,precipitation_probability,weather_code," +
            "wind_speed_10m,visibility"

        const val DAILY_VARS = "weather_code,temperature_2m_max,temperature_2m_min," +
            "sunrise,sunset,precipitation_sum,precipitation_probability_max,uv_index_max," +
            "wind_speed_10m_max,moon_phase"
    }
}

// ── Response models ───────────────────────────────────────────────────────────

data class OpenMeteoResponse(
    val latitude:  Double,
    val longitude: Double,
    val timezone:  String,
    val current:   CurrentWeather?,
    val hourly:    HourlyWeather?,
    val daily:     DailyWeather?
)

data class CurrentWeather(
    val time:                     String,
    val temperature_2m:           Float,
    val relative_humidity_2m:     Int,
    val apparent_temperature:     Float,
    val weather_code:             Int,
    val surface_pressure:         Float,
    val wind_speed_10m:           Float,
    val wind_direction_10m:       Int,
    val visibility:               Float,
    val cloud_cover:              Int,
    val precipitation_probability:Int,
    val uv_index:                 Float
)

data class HourlyWeather(
    val time:                     List<String>,
    val temperature_2m:           List<Float>,
    val precipitation_probability:List<Int>,
    val weather_code:             List<Int>,
    val wind_speed_10m:           List<Float>,
    val visibility:               List<Float>
)

data class DailyWeather(
    val time:                          List<String>,
    val weather_code:                  List<Int>,
    val temperature_2m_max:            List<Float>,
    val temperature_2m_min:            List<Float>,
    val sunrise:                       List<String>,
    val sunset:                        List<String>,
    val precipitation_sum:             List<Float>,
    val precipitation_probability_max: List<Int>,
    val uv_index_max:                  List<Float>,
    val wind_speed_10m_max:            List<Float>,
    val moon_phase:                    List<Float>
)
