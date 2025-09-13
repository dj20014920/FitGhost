package com.fitghost.app.util

import android.content.Context

/**
 * API 키 제공자 (서버리스 + 로컬 저장소 전략)
 *
 * 현재 단계:
 * - 요구사항에 따라 "API 키를 아직 적재하지 않음".
 * - 모든 키 관련 함수는 null 을 반환하며, 호출부는 키 미설정 상태를 자연스럽게 처리해야 함.
 *
 * 목표:
 * - 나중에 키만 주입하면 되도록(// TODO) 공용 인터페이스/주입 경로를 마련한다.
 * - KISS/DRY/YAGNI 원칙: 불필요한 복잡성/중복 로직 금지, 간단하게 교체 가능하도록 설계.
 *
 * 권장 주입 시나리오(향후):
 * 1) 안전한 로컬 보관:
 *    - EncryptedSharedPreferences / Android Keystore 활용
 *    - 앱 내 "설정" 화면에서 사용자가 직접 키 입력 → 암호화 저장
 * 2) 에페메럴 토큰(초박형 서버리스 프록시):
 *    - 상용 배포 시 앱에 영구 키를 내장하지 않고, 짧은 생명 주기의 토큰을 발급받아 사용
 * 3) 빌드타임 주입(개인/테스트 용도):
 *    - BuildConfig 필드 사용(단, 보안 이슈로 상용 권장 X)
 *
 * 사용 예(키가 필요한 호출부에서):
 * val key = ApiKeys.provider.geminiApiKey(context)
 * if (key == null) {
 *   // 키 미설정 경로 처리: 설정 유도, 안내 메시지, 기능 제한 등
 * } else {
 *   // 키 사용 로직 진행
 * }
 */
interface ApiKeyProvider {
    /**
     * Gemini(Nano Banana) 이미지 생성/편집용 API 키
     *
     * 현재는 null.
     * // TODO: API 키 적재 예정
     * 예) EncryptedSharedPreferences 또는 서버리스 토큰 릴레이로 대체
     */
    fun geminiApiKey(context: Context): String?

    /**
     * 날씨 API 키
     *
     * 주의: Open-Meteo는 키가 필요 없으므로 null 이 정상일 수 있다.
     * 추후 OpenWeatherMap/WeatherKit 등으로 교체 시 사용.
     * // TODO: API 키 적재 예정
     */
    fun weatherApiKey(context: Context): String?

    /**
     * Google Programmable Search(또는 Custom Search) API 키
     * // TODO: API 키 적재 예정
     */
    fun googleSearchApiKey(context: Context): String?

    /**
     * 네이버 쇼핑/검색용 키: Client ID + Client Secret 세트
     * // TODO: API 키 적재 예정
     */
    fun naverShoppingKeys(context: Context): NaverShoppingKeys?
}

/**
 * 네이버 오픈API는 Client ID/Secret 쌍을 요구하므로 전용 데이터 클래스로 관리
 */
data class NaverShoppingKeys(
    val clientId: String,
    val clientSecret: String
)

/**
 * 전역 접근 지점.
 * - 기본값은 비어있는 구현(DefaultApiKeyProvider).
 * - 추후 앱 시작 시 또는 설정 화면에서 주입 가능:
 *   ApiKeys.provider = MySecureApiKeyProvider(...)
 */
object ApiKeys {
    @Volatile
    var provider: ApiKeyProvider = DefaultApiKeyProvider
}

/**
 * 현재는 모든 키가 비어있는 기본 구현.
 * - 나중에 주입만 하면 되도록 반환값은 null 고정 + TODO 주석 유지
 */
object DefaultApiKeyProvider : ApiKeyProvider {
    override fun geminiApiKey(context: Context): String? {
        // TODO: API 키 적재 예정 (예: EncryptedSharedPreferences / 서버리스 토큰 릴레이)
        return null
    }

    override fun weatherApiKey(context: Context): String? {
        // Open-Meteo 사용 시 키 불필요 → null 정상
        // TODO: OpenWeatherMap/WeatherKit 사용 시 키 적재 예정
        return null
    }

    override fun googleSearchApiKey(context: Context): String? {
        // TODO: API 키 적재 예정
        return null
    }

    override fun naverShoppingKeys(context: Context): NaverShoppingKeys? {
        // TODO: Client ID/Secret 적재 예정
        return null
    }
}
