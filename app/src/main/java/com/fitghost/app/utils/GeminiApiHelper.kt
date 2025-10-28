package com.fitghost.app.utils

import android.util.Base64
import android.util.Log
import com.fitghost.app.engine.TryOnPromptBuilder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini API 공통 유틸리티
 *
 * DRY 원칙에 따라 중복된 Gemini API 요청/응답 로직을 통합 관리합니다.
 * - 요청 본문 생성
 * - 응답 이미지 파싱
 * - 에러 처리
 */
object GeminiApiHelper {

    private const val TAG = "GeminiApiHelper"

    /**
     * Gemini API 가상 피팅 요청 본문 생성
     *
     * 공식 스키마 준수:
     * - contents[].role = "user" (system 역할 사용 금지)
     * - parts: [텍스트, 모델 이미지, 의상 이미지들]
     * - inline_data: snake_case 사용
     *
     * @param modelPng 모델 사진 PNG 바이트
     * @param clothingPngs 의상 이미지 PNG 바이트 리스트
     * @param systemPrompt 시스템 프롬프트 (선택사항)
     * @return JSON 요청 본문 문자열
     */
    fun buildTryOnRequestJson(
            modelPng: ByteArray,
            clothingPngs: List<ByteArray>,
            systemPrompt: String? = null
    ): String {
        // 1) 시스템 지시문과 유저 지시문을 결합하여 단일 텍스트 생성
        val combinedText =
                buildString {
                            append(TryOnPromptBuilder.buildSystemText(systemPrompt))
                            append("\n\n")
                            append(
                                    TryOnPromptBuilder.buildUserInstruction(
                                            hasModel = true,
                                            clothingCount = clothingPngs.size
                                    )
                            )
                        }
                        .trim()

        // 2) 유저 컨텐츠 parts 배열 생성: 텍스트 → 모델 이미지 → 의상 이미지들
        val userParts =
                JSONArray().apply {
                    // 텍스트 프롬프트 먼저
                    put(JSONObject().put("text", combinedText))

                    // 모델 이미지 (이미지 1)
                    put(
                            JSONObject()
                                    .put(
                                            "inline_data",
                                            JSONObject()
                                                    .put("mime_type", "image/png")
                                                    .put(
                                                            "data",
                                                            Base64.encodeToString(
                                                                    modelPng,
                                                                    Base64.NO_WRAP
                                                            )
                                                    )
                                    )
                    )

                    // 의상 이미지들 (이미지 2..N)
                    clothingPngs.forEach { clothingPng ->
                        put(
                                JSONObject()
                                        .put(
                                                "inline_data",
                                                JSONObject()
                                                        .put("mime_type", "image/png")
                                                        .put(
                                                                "data",
                                                                Base64.encodeToString(
                                                                        clothingPng,
                                                                        Base64.NO_WRAP
                                                                )
                                                        )
                                        )
                        )
                    }
                }

        // 3) 유저 컨텐츠 생성 (role=user)
        val userContent = JSONObject().put("role", "user").put("parts", userParts)

        // 4) 최종 요청 본문
        val requestJson =
                JSONObject()
                        .put("contents", JSONArray().put(userContent))
                        .put(
                                "generationConfig",
                                JSONObject().put("temperature", 0.25).put("candidateCount", 1)
                        )

        Log.d(TAG, "Prepared Gemini request: model=1, clothes=${clothingPngs.size}, text=1")

        return requestJson.toString()
    }

    /**
     * Gemini API 응답에서 생성된 이미지 추출
     *
     * 응답 구조: candidates[0].content.parts[*].inline_data.data (또는 inlineData.data)
     *
     * @param responseJson Gemini API 응답 JSON 문자열
     * @return PNG 이미지 바이트 배열
     * @throws GeminiApiException 파싱 실패 시
     */
    fun extractImageFromResponse(responseJson: String): ByteArray {
        return try {
            val root = JSONObject(responseJson)

            // candidates 배열 확인
            val candidates =
                    root.optJSONArray("candidates")
                            ?: throw GeminiApiException("No candidates in response")

            if (candidates.length() == 0) {
                throw GeminiApiException("Empty candidates array")
            }

            // 첫 번째 candidate의 content.parts 탐색
            val firstCandidate = candidates.getJSONObject(0)
            val content =
                    firstCandidate.optJSONObject("content")
                            ?: throw GeminiApiException("No content in candidate")

            val parts =
                    content.optJSONArray("parts") ?: throw GeminiApiException("No parts in content")

            // parts 배열에서 이미지 데이터 찾기 (snake_case 우선, camelCase 허용)
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // snake_case (공식 스키마)
                if (part.has("inline_data")) {
                    val inline = part.getJSONObject("inline_data")
                    val base64Data = inline.optString("data")
                    if (base64Data.isNotBlank()) {
                        Log.d(TAG, "Successfully extracted image (inline_data format)")
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }

                // camelCase (일부 응답 변형 대응)
                if (part.has("inlineData")) {
                    val inline = part.getJSONObject("inlineData")
                    val base64Data = inline.optString("data")
                    if (base64Data.isNotBlank()) {
                        Log.d(TAG, "Successfully extracted image (inlineData format)")
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }

            throw GeminiApiException("No image data found in parts")
        } catch (e: GeminiApiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response", e)
            throw GeminiApiException("Response parsing failed: ${e.message}", e)
        }
    }

    /**
     * Gemini API 전용 예외 클래스
     *
     * 사용자 친화적 에러 메시지 제공을 위한 기반 클래스
     */
    class GeminiApiException(message: String, cause: Throwable? = null) :
            Exception(message, cause) {

        /** 사용자에게 표시할 친화적 메시지 생성 */
        fun getUserMessage(): String {
            return when {
                message?.contains("No candidates", ignoreCase = true) == true ->
                        "이미지 생성에 실패했어요. 다른 사진으로 다시 시도해주세요."
                message?.contains("No image data", ignoreCase = true) == true ->
                        "이미지 생성 결과를 받지 못했어요. 잠시 후 다시 시도해주세요."
                message?.contains("parsing failed", ignoreCase = true) == true ->
                        "서버 응답을 처리하는 중 문제가 발생했어요. 다시 시도해주세요."
                else -> "가상 피팅 중 문제가 발생했어요. 잠시 후 다시 시도해주세요."
            }
        }
    }
}
