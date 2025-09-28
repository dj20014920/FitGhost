package com.fitghost.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo API (키 불요) */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean = true
    ): OpenMeteoResponse
}

data class OpenMeteoResponse(
    val latitude: Double?,
    val longitude: Double?,
    val current_weather: CurrentWeather?
)

data class CurrentWeather(
    val temperature: Double?,
    val windspeed: Double?,
    val weathercode: Int?
)