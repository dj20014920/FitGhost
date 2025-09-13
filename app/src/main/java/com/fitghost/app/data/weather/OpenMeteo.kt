package com.fitghost.app.data.weather

import com.fitghost.app.data.model.WeatherSnapshot
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {
    @GET("v1/forecast?current=temperature_2m,precipitation,wind_speed_10m")
    suspend fun current(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double
    ): OpenMeteoResponse
}

data class OpenMeteoResponse(val current: Current?) {
    data class Current(
        val temperature_2m: Double?,
        val precipitation: Double?,
        val wind_speed_10m: Double?
    )
}

class WeatherRepo(private val api: OpenMeteoApi) {
    suspend fun today(lat: Double, lon: Double): WeatherSnapshot? {
        val r = api.current(lat, lon)
        val c = r.current ?: return null
        return WeatherSnapshot(
            temperatureC = c.temperature_2m ?: 20.0,
            precipitationMm = c.precipitation ?: 0.0,
            windSpeed = c.wind_speed_10m ?: 0.0
        )
    }
}
