package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.fitghost.app.BuildConfig
import com.fitghost.app.utils.GeminiApiHelper
import com.fitghost.app.utils.ImageUtils
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * NanoBananaTryOnEngine - Google Gemini 2.5 Flash Image Preview 가상 피팅 엔진
 *
 * Google Gemini API를 사용한 가상 피팅 구현
 *
 * 주요 특징:
 * - 공식 API 엔드포인트 사용: gemini-2.5-flash-image-preview
 * - 오프라인 대응: IOException 감지 및 친화적 에러 메시지
 * - DRY 원칙: GeminiApiHelper로 공통 로직 통합
 * - TLS 설정 유연화: ALLOW_INSECURE_TLS 플래그 지원 (디버그 전용)
 *
 * @param client OkHttp 클라이언트 (기본값: 자동 생성)
 */
class NanoBananaTryOnEngine(private val client: OkHttpClient = createHttpClient()) : TryOnEngine {

    override suspend fun renderPreview(
            context: Context,
            modelUri: Uri,
            clothingUris: List<Uri>,
            systemPrompt: String?
    ): Bitmap =
            withContext(Dispatchers.IO) {
                val apiKey = BuildConfig.NANOBANANA_API_KEY.orEmpty()
                require(apiKey.isNotBlank()) {
                    "NANOBANANA_API_KEY is not set. Please provide Google AI API key in local.properties"
                }

                // Google Gemini API 엔드포인트 (공식 문서 기준)
                val apiUrl =
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image-preview:generateContent"

                try {
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

                    // 2) Gemini API 요청 본문 생성 (GeminiApiHelper 사용 - DRY)
                    val requestBodyJson =
                            GeminiApiHelper.buildTryOnRequestJson(
                                    modelPng,
                                    clothingPngs,
                                    systemPrompt
                            )

                    Log.d(
                            TAG,
                            "Calling Google Gemini API (model=gemini-2.5-flash-image-preview, model=1, clothes=${clothingPngs.size})"
                    )

                    // 3) API 요청 생성
                    val request =
                            Request.Builder()
                                    .url(apiUrl)
                                    .header("x-goog-api-key", apiKey)
                                    .header("Content-Type", "application/json")
                                    .post(
                                            requestBodyJson.toRequestBody(
                                                    "application/json".toMediaType()
                                            )
                                    )
                                    .build()

                    // 4) API 호출 및 응답 처리
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
                } catch (e: IOException) {
                    // 네트워크 에러 (오프라인 등)
                    Log.e(TAG, "Network error during virtual try-on", e)
                    throw GeminiApiHelper.GeminiApiException("인터넷 연결을 확인해주세요. 네트워크 오류가 발생했습니다.", e)
                } catch (e: GeminiApiHelper.GeminiApiException) {
                    // Gemini API 에러
                    Log.e(TAG, "Gemini API error: ${e.message}", e)
                    throw e
                } catch (e: Exception) {
                    // 기타 예상치 못한 에러
                    Log.e(TAG, "Unexpected error during virtual try-on", e)
                    throw GeminiApiHelper.GeminiApiException("가상 피팅 중 문제가 발생했습니다. 다시 시도해주세요.", e)
                }
            }

    companion object {
        private const val TAG = "NanoBananaTryOnEngine"

        private fun createHttpClient(): OkHttpClient {
            return if (BuildConfig.ALLOW_INSECURE_TLS) buildInsecureClient()
            else buildDefaultClient()
        }

        private fun buildDefaultClient(): OkHttpClient =
                OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS) // Gemini API는 이미지 생성에 시간이 더 걸림
                        .readTimeout(120, TimeUnit.SECONDS) // 가상 피팅은 복잡한 작업이므로 충분한 시간 필요
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build()

        @Suppress("CustomX509TrustManager")
        private fun buildInsecureClient(): OkHttpClient {
            val trustAllCerts =
                    arrayOf<TrustManager>(
                            object : X509TrustManager {
                                override fun checkClientTrusted(
                                        chain: Array<X509Certificate>,
                                        authType: String
                                ) {}
                                override fun checkServerTrusted(
                                        chain: Array<X509Certificate>,
                                        authType: String
                                ) {}
                                override fun getAcceptedIssuers(): Array<X509Certificate> =
                                        arrayOf()
                            }
                    )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier(HostnameVerifier { _, _ -> true })
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
        }
    }
}
