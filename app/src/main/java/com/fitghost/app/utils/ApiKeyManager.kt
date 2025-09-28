package com.fitghost.app.utils

import com.fitghost.app.BuildConfig

/**
 * API 키 관리를 위한 유틸리티 클래스
 * 
 * 사용법:
 * val apiKey = ApiKeyManager.getGeminiVertexApiKey()
 */
object ApiKeyManager {
    
    /**
     * Gemini Vertex AI API 키를 반환합니다.
     * 
     * @return API 키 문자열, 설정되지 않은 경우 빈 문자열
     */
    fun getGeminiVertexApiKey(): String {
        // Only explicit GEMINI key is valid for Gemini
        val primary = BuildConfig.GEMINI_VERTEX_API_KEY
        return if (primary.isNotEmpty()) primary else ""
    }
    
    /**
     * API 키가 유효한지 확인합니다.
     * 
     * @return API 키가 설정되어 있고 비어있지 않으면 true
     */
    fun isGeminiVertexApiKeyValid(): Boolean {
        return getGeminiVertexApiKey().isNotEmpty()
    }
    
    /**
     * API 키가 설정되지 않은 경우 예외를 발생시킵니다.
     * 
     * @throws IllegalStateException API 키가 설정되지 않은 경우
     */
    fun requireGeminiVertexApiKey(): String {
        val apiKey = getGeminiVertexApiKey()
        if (apiKey.isEmpty()) {
            throw IllegalStateException(
                "GEMINI_VERTEX_API_KEY가 설정되지 않았습니다. " +
                "local.properties 파일에 GEMINI_VERTEX_API_KEY를 추가해주세요. (NANOBANANA_API_KEY는 더 이상 사용하지 않습니다)"
            )
        }
        return apiKey
    }
    
    // Legacy NanoBanana key helpers removed — Gemini only

    
    /**
     * Gemini AI API 키를 반환합니다. (새로운 공식 API용)
     * GEMINI_VERTEX_API_KEY와 동일하지만 명시적 이름 제공
     * 
     * @return Gemini API 키 문자열
     */
    fun getGeminiApiKey(): String {
        return getGeminiVertexApiKey()
    }
    
    /**
     * Gemini AI API 키가 유효한지 확인합니다.
     * 
     * @return API 키가 설정되어 있고 비어있지 않으면 true
     */
    fun isGeminiApiKeyValid(): Boolean {
        return isGeminiVertexApiKeyValid()
    }
    
    /**
     * Gemini AI API 키가 설정되지 않은 경우 예외를 발생시킵니다.
     * 
     * @throws IllegalStateException API 키가 설정되지 않은 경우
     */
    fun requireGeminiApiKey(): String {
        return requireGeminiVertexApiKey()
    }
}