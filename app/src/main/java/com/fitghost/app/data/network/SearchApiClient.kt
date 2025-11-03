package com.fitghost.app.data.network

import com.fitghost.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 검색 API 클라이언트
 * 네이버/구글 검색 API를 위한 Retrofit 인스턴스
 */
object SearchApiClient {
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.PROXY_BASE_URL) // https://fitghost-proxy.vinny4920-081.workers.dev
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val naverApi: NaverApi = retrofit.create(NaverApi::class.java)
    val googleApi: GoogleCseApi = retrofit.create(GoogleCseApi::class.java)
}
