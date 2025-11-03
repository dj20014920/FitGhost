package com.fitghost.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 네이버 쇼핑 검색 API
 * 프록시 서버 경유: https://fitghost-proxy.vinny4920-081.workers.dev/proxy/naver/shop
 */
interface NaverApi {
    @GET("/proxy/naver/shop")
    suspend fun searchShop(
        @Query("query") query: String,
        @Query("display") display: Int = 20,
        @Query("start") start: Int = 1,
        @Query("sort") sort: String = "sim" // sim=유사도, date=날짜, asc=가격오름차순, dsc=가격내림차순
    ): NaverSearchResponse
}

/**
 * 네이버 검색 응답
 */
data class NaverSearchResponse(
    val lastBuildDate: String,
    val total: Int,
    val start: Int,
    val display: Int,
    val items: List<NaverShopItem>
)

/**
 * 네이버 상품 아이템
 */
data class NaverShopItem(
    val title: String,           // HTML 태그 포함 (예: "상품명 <b>검색어</b>")
    val link: String,
    val image: String,
    val lprice: String,          // 최저가 (문자열)
    val hprice: String,          // 최고가 (문자열)
    val mallName: String,
    val productId: String,
    val productType: String,
    val brand: String,
    val maker: String,
    val category1: String,
    val category2: String,
    val category3: String,
    val category4: String
)
