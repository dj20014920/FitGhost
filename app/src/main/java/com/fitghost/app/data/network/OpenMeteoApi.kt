package com.fitghost.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "current_weather") val current_weather: CurrentWeather?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature") val temperature: Double?,
    @Json(name = "windspeed") val windspeed: Double?,
    @Json(name = "weathercode") val weathercode: Int?
)