package com.fitghost.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 구글 커스텀 검색 API
 * 프록시 서버 경유: https://fitghost-proxy.vinny4920-081.workers.dev/proxy/google/cse
 */
interface GoogleCseApi {
    @GET("/proxy/google/cse")
    suspend fun search(
        @Query("q") query: String,
        @Query("num") num: Int = 10,
        @Query("start") start: Int = 1
    ): GoogleSearchResponse
}

/**
 * 구글 검색 응답
 */
data class GoogleSearchResponse(
    val kind: String,
    val searchInformation: SearchInformation,
    val items: List<GoogleSearchItem>?
)

/**
 * 검색 정보
 */
data class SearchInformation(
    val searchTime: Double,
    val totalResults: String
)

/**
 * 구글 검색 아이템
 */
data class GoogleSearchItem(
    val title: String,
    val link: String,
    val snippet: String,
    val displayLink: String,
    val pagemap: GooglePageMap?
)

/**
 * 페이지 메타데이터
 */
data class GooglePageMap(
    val cse_image: List<GoogleImage>?,
    val metatags: List<Map<String, String>>?
)

/**
 * 이미지 정보
 */
data class GoogleImage(
    val src: String
)
