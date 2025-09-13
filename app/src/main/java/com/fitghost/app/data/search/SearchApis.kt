package com.fitghost.app.data.search

import android.content.Context
import com.fitghost.app.util.ApiKeys
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 검색 API 스켈레톤.
 *
 * 목표
 * - 지금은 "API 키를 적재하지 않음": 나중에 키만 주입하면 동작하도록 구조를 준비한다.
 * - KISS/DRY/YAGNI/SOLID 준수: 공통 결과 모델과 단순 변환, 의존성 역전(인터페이스), 중복 금지.
 *
 * 설계 포인트
 * - 키 누락 시: 예외를 던지기보다 "빈 결과"를 반환하고 호출부에서 자연스러운 안내가 가능하도록 한다.
 * - Google Programmable Search(또는 Custom Search)와 Naver 쇼핑 검색을 단일 공통 결과 모델로 정규화한다.
 * - Retrofit 인터페이스만 정의(이 파일에서는 Retrofit 인스턴스 생성/베이스URL 설정을 하지 않음).
 *
 * TODO(키 주입 예정)
 * - Google: API Key + CX(Search Engine ID)가 필요. 이 파일에서는 CX를 생성자 주입받도록 두고, API Key는
 * ApiKeys.provider.geminiApiKey(...) 등과 달리 검색 전용 키로 교체 주입받을 것을 권장. (현재 ApiKeyProvider에는
 * googleSearchApiKey()만 존재. CX는 UI/설정/빌드설정 등에서 주입)
 * - Naver: Client ID/Secret은 ApiKeys.provider.naverShoppingKeys(context) 로부터 주입
 */
class SearchRepository(
        private val context: Context,
        private val google: GoogleCSEApi? = null,
        private val naver: NaverShoppingApi? = null,
        /**
         * Google Programmable Search의 CX(Search Engine ID) // TODO: 실제 CX 값 주입 예정(설정/빌드변수/서버리스 토큰
         * 등)
         */
        private val googleCx: String? = null
) {

    /**
     * 통합 검색 함수: 가능한 공급자 순서대로 시도.
     * - 1순위 Google(키+CX 모두 있을 때)
     * - 2순위 Naver(키 세트가 있을 때)
     * - 모두 없으면 빈 리스트
     */
    suspend fun search(query: String, limit: Int = 20): List<UiSearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1) Google
        val googleKey = ApiKeys.provider.googleSearchApiKey(context)
        if (google != null && !googleKey.isNullOrBlank() && !googleCx.isNullOrBlank()) {
            val gApi = google!!
            val key = googleKey!!
            val cx = googleCx!!
            val byGoogle =
                    runCatching {
                        callGoogleSearch(
                                api = gApi,
                                apiKey = key,
                                cx = cx,
                                query = trimmed,
                                limit = limit
                        )
                    }
                            .getOrElse { emptyList() }
            if (byGoogle.isNotEmpty()) return byGoogle
        }

        // 2) Naver
        val naverKeys = ApiKeys.provider.naverShoppingKeys(context)
        if (naver != null && naverKeys != null) {
            val nApi = naver!!
            val keys = naverKeys
            val byNaver =
                    runCatching {
                        callNaverSearch(api = nApi, keys = keys, query = trimmed, limit = limit)
                    }
                            .getOrElse { emptyList() }
            if (byNaver.isNotEmpty()) return byNaver
        }

        // 3) 폴백
        return emptyList()
    }

    private suspend fun callGoogleSearch(
            api: GoogleCSEApi,
            apiKey: String,
            cx: String,
            query: String,
            limit: Int
    ): List<UiSearchItem> {
        // Google CSE는 num 최대 10. 초과 시 페이지네이션이 필요하나, YAGNI로 초기엔 10까지만 지원.
        val num = limit.coerceIn(1, 10)
        val res = api.search(key = apiKey, cx = cx, query = query, num = num)
        val items = res.items.orEmpty()
        return items.mapNotNull { it.toUi() }
    }

    private suspend fun callNaverSearch(
            api: NaverShoppingApi,
            keys: com.fitghost.app.util.NaverShoppingKeys,
            query: String,
            limit: Int
    ): List<UiSearchItem> {
        val display = limit.coerceIn(1, 100)
        val res =
                api.search(
                        clientId = keys.clientId,
                        clientSecret = keys.clientSecret,
                        query = query,
                        display = display
                )
        val items = res.items.orEmpty()
        return items.mapNotNull { it.toUi() }
    }
}

/* ============================
공통 UI 모델 (정규화)
============================ */

@JsonClass(generateAdapter = true)
data class UiSearchItem(
        val title: String,
        val price: Double?, // 없을 수 있음
        val imageUrl: String?, // 썸네일/대표 이미지
        val mallName: String?, // 쇼핑몰/브랜드
        val link: String // 상품/페이지 링크(외부 브라우저/딥링크)
)

/* ============================
Google Programmable Search
============================ */

interface GoogleCSEApi {
    /**
     * https://www.googleapis.com/customsearch/v1
     *
     * 참고: 베이스 URL은 Retrofit 빌더에서 설정해야 함. 예) baseUrl("https://www.googleapis.com/")
     */
    @GET("customsearch/v1")
    suspend fun search(
            @Query("key") key: String, // TODO: API 키 적재 예정
            @Query("cx") cx: String, // TODO: CX(검색 엔진 ID) 주입 예정
            @Query("q") query: String,
            @Query("num") num: Int? = 10, // 1~10
            @Query("searchType") searchType: String? = null, // "image" 등(옵션)
            @Query("imgSize") imgSize: String? = null, // 옵션
            @Query("safe") safe: String? = "active" // 옵션
    ): GoogleCseResponse
}

@JsonClass(generateAdapter = true) data class GoogleCseResponse(val items: List<GoogleCseItem>?)

@JsonClass(generateAdapter = true)
data class GoogleCseItem(
        val title: String?,
        val link: String?,
        val snippet: String?,
        val pagemap: GooglePageMap? = null
) {
    fun toUi(): UiSearchItem? {
        val safeLink = link ?: return null
        val thumb =
                pagemap?.cseImage?.firstOrNull()?.src ?: pagemap?.cseThumbnail?.firstOrNull()?.src
        // 가격/몰명은 응답에 없을 수 있으므로 null
        return UiSearchItem(
                title = title ?: safeLink,
                price = null,
                imageUrl = thumb,
                mallName = null,
                link = safeLink
        )
    }
}

@JsonClass(generateAdapter = true)
data class GooglePageMap(
        @Json(name = "cse_image") val cseImage: List<GoogleSrcHolder>? = null,
        @Json(name = "cse_thumbnail") val cseThumbnail: List<GoogleSrcHolder>? = null
)

@JsonClass(generateAdapter = true) data class GoogleSrcHolder(val src: String?)

/* ============================
Naver Shopping Search
============================ */

interface NaverShoppingApi {
    /**
     * https://openapi.naver.com/v1/search/shop.json
     *
     * 참고: 베이스 URL은 Retrofit 빌더에서 설정해야 함. 예) baseUrl("https://openapi.naver.com/")
     */
    @GET("v1/search/shop.json")
    suspend fun search(
            @Header("X-Naver-Client-Id") clientId: String, // TODO: API 키 적재 예정
            @Header("X-Naver-Client-Secret") clientSecret: String, // TODO: API 키 적재 예정
            @Query("query") query: String,
            @Query("display") display: Int? = 20, // 1~100
            @Query("start") start: Int? = 1,
            @Query("sort") sort: String? = "sim" // sim(유사도), date(날짜), asc/dsc(가격)
    ): NaverShopResponse
}

@JsonClass(generateAdapter = true) data class NaverShopResponse(val items: List<NaverShopItem>?)

@JsonClass(generateAdapter = true)
data class NaverShopItem(
        val title: String?,
        val link: String?,
        val image: String?,
        val lprice: String?, // 최저가(문자열)
        @Json(name = "hprice") val hprice: String?, // 최고가(문자열, 옵션)
        @Json(name = "mallName") val mallName: String?
) {
    fun toUi(): UiSearchItem? {
        val safeLink = link ?: return null
        val price = lprice?.toSafeDouble()
        val cleanTitle =
                title?.replace("<b>", "", ignoreCase = true)?.replace("</b>", "", ignoreCase = true)
        return UiSearchItem(
                title = cleanTitle ?: safeLink,
                price = price,
                imageUrl = image,
                mallName = mallName,
                link = safeLink
        )
    }

    private fun String.toSafeDouble(): Double? = this.replace(",", "").toDoubleOrNull()
}
