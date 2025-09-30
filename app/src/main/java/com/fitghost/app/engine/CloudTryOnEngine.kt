package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.fitghost.app.BuildConfig
import com.fitghost.app.utils.ApiKeyManager
import com.fitghost.app.utils.GeminiApiHelper
import com.fitghost.app.utils.ImageUtils
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * CloudTryOnEngine - Gemini 2.5 Flash Image Preview 가상 피팅 엔진
 *
 * Google Gemini API를 사용한 클라우드 기반 가상 피팅 구현
 *
 * 주요 특징:
 * - PRD 개인정보 원칙 준수: 기본 비활성 (CLOUD_TRYON_ENABLED=false)
 * - 실패 시 자동 폴백: FakeTryOnEngine으로 로컬 합성 제공
 * - 오프라인 대응: IOException 감지 시 친화적 에러 메시지
 * - DRY 원칙: GeminiApiHelper로 공통 로직 통합
 *
 * @param client OkHttp 클라이언트 (테스트용 주입 가능)
 */
class CloudTryOnEngine(private val client: OkHttpClient = OkHttpClient()) : TryOnEngine {

    companion object {
        private const val TAG = "CloudTryOnEngine"
    }

    override suspend fun renderPreview(
            context: Context,
            modelUri: Uri,
            clothingUris: List<Uri>,
            systemPrompt: String?
    ): Bitmap =
            withContext(Dispatchers.IO) {
                val apiKey = ApiKeyManager.requireGeminiVertexApiKey()

                // 1) 이미지 로드 및 변환 (과금 최적화: 해상도 제한)
                val modelPng =
                        ImageUtils.uriToPngBytesCapped(
                                context,
                                modelUri,
                                BuildConfig.TRYON_MAX_SIDE_PX
                        )

                val maxTotal = BuildConfig.MAX_TRYON_TOTAL_IMAGES.coerceAtLeast(2)
                val maxClothes = (maxTotal - 1).coerceAtLeast(1)

                val clothingPngs =
                        clothingUris.take(maxClothes).mapNotNull { uri ->
                            runCatching {
                                ImageUtils.uriToPngBytesCapped(
                                        context,
                                        uri,
                                        BuildConfig.TRYON_MAX_SIDE_PX
                                )
                            }
                                    .getOrElse {
                                        Log.w(TAG, "Failed to load clothing image: $uri", it)
                                        null
                                    }
                        }

                if (clothingUris.size > clothingPngs.size) {
                    Log.w(TAG, "Some clothing images failed to load or were truncated")
                }

                // 2) Gemini API 요청 본문 생성 (GeminiApiHelper 사용)
                val requestBodyJson =
                        GeminiApiHelper.buildTryOnRequestJson(modelPng, clothingPngs, systemPrompt)

                val body = requestBodyJson.toRequestBody("application/json".toMediaType())

                // 3) API 엔드포인트 및 요청 생성
                val url =
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                                "gemini-2.5-flash-image-preview:generateContent?key=$apiKey"

                val request = Request.Builder().url(url).post(body).build()

                Log.d(TAG, "Calling Gemini API (model=1, clothes=${clothingPngs.size})")

                // 4) API 호출 및 응답 처리
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            throw GeminiApiHelper.GeminiApiException(
                                    "HTTP ${response.code}: ${response.message} - $errorBody"
                            )
                        }

                        val responseText =
                                response.body?.string()
                                        ?: throw GeminiApiHelper.GeminiApiException(
                                                "Empty response body"
                                        )

                        Log.d(TAG, "Gemini API response received, parsing image...")

                        // GeminiApiHelper로 이미지 추출 (DRY)
                        val imageBytes = GeminiApiHelper.extractImageFromResponse(responseText)
                        return@withContext ImageUtils.decodePng(imageBytes)
                    }
                }
                        .getOrElse { exception ->
                            // 에러 로깅
                            when (exception) {
                                is IOException -> {
                                    Log.e(TAG, "Network error during virtual try-on", exception)
                                }
                                is GeminiApiHelper.GeminiApiException -> {
                                    Log.e(TAG, "Gemini API error: ${exception.message}", exception)
                                }
                                else -> {
                                    Log.e(TAG, "Unexpected error during virtual try-on", exception)
                                }
                            }

                            // 폴백: 로컬 FakeTryOnEngine 사용 (워터마크 프리뷰)
                            Log.d(TAG, "Falling back to local FakeTryOnEngine")
                            FakeTryOnEngine()
                                    .renderPreview(context, modelUri, clothingUris, systemPrompt)
                        }
            }
}
