package com.fitghost.app.util

import android.content.Context
import androidx.room.Room
import com.fitghost.app.data.db.AppDb
import com.fitghost.app.data.weather.OpenMeteoApi
import com.fitghost.app.data.weather.WeatherRepo
import com.fitghost.app.domain.OutfitRecommender
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.fitghost.app.data.search.SearchRepository
import com.fitghost.app.data.search.GoogleCSEApi
import com.fitghost.app.data.search.NaverShoppingApi

object ServiceLocator {
    @Volatile private var moshiInstance: Moshi? = null
    private fun moshi(): Moshi = moshiInstance ?: synchronized(this) {
        moshiInstance ?: Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build().also { moshiInstance = it }
    }
    @Volatile private var db: AppDb? = null
    fun db(context: Context): AppDb = db ?: synchronized(this) {
        db ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db").build().also { db = it }
    }

    @Volatile private var retrofit: Retrofit? = null
    private fun retrofit(): Retrofit = retrofit ?: synchronized(this) {
        retrofit ?: Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(MoshiConverterFactory.create(moshi()))
                    .build().also { retrofit = it }
    }

    @Volatile private var googleRetrofit: Retrofit? = null
    private fun googleRetrofit(): Retrofit = googleRetrofit ?: synchronized(this) {
        googleRetrofit ?: Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi()))
            .build().also { googleRetrofit = it }
    }

    @Volatile private var naverRetrofit: Retrofit? = null
    private fun naverRetrofit(): Retrofit = naverRetrofit ?: synchronized(this) {
        naverRetrofit ?: Retrofit.Builder()
            .baseUrl("https://openapi.naver.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi()))
            .build().also { naverRetrofit = it }
    }

    /**
     * 통합 검색 리포지토리
     * - 현재는 API 키를 적재하지 않으므로, 호출 시 빈 결과가 반환될 수 있음
     * - // TODO: Google CX(Search Engine ID) 및 각 API 키 주입 예정
     */
    fun searchRepo(context: Context): SearchRepository {
        val googleApi = googleRetrofit().create(GoogleCSEApi::class.java)
        val naverApi = naverRetrofit().create(NaverShoppingApi::class.java)
        val googleCx: String? = null // TODO: CX 주입 예정
        return SearchRepository(
            context = context,
            google = googleApi,
            naver = naverApi,
            googleCx = googleCx
        )
    }

    fun weatherRepo(): WeatherRepo = WeatherRepo(retrofit().create(OpenMeteoApi::class.java))

    val recommender: OutfitRecommender by lazy { OutfitRecommender() }
}
