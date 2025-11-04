package com.fitghost.app.data.weather

import com.fitghost.app.data.network.OpenMeteoApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class WeatherRepo(private val api: OpenMeteoApi) {
    suspend fun getCurrent(lat: Double, lon: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val res = api.forecast(lat, lon, true)
        val t = res.current_weather?.temperature ?: 0.0
        val w = res.current_weather?.windspeed ?: 0.0
        WeatherSnapshot(tempC = t, windKph = w)
    }

    companion object {
        fun create(): WeatherRepo {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val api = retrofit.create(OpenMeteoApi::class.java)
            return WeatherRepo(api)
        }
    }
}

data class WeatherSnapshot(val tempC: Double, val windKph: Double)