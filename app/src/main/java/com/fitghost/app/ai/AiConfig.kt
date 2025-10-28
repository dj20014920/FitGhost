package com.fitghost.app.ai

/**
 * AI 공용 설정 상수
 */
object AiConfig {
    // Cloudflare R2 퍼블릭 개발 URL (기본값)
    // 주의: 실제 퍼블릭 도메인은 환경에 따라 상이할 수 있으며,
    // local.properties의 MODEL_BASE_URL(BuildConfig.MODEL_BASE_URL)로 재정의할 수 있습니다.
    const val R2_PUBLIC_BASE: String = "https://cdn.emozleep.space/models"
}
