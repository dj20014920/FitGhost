package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.fitghost.app.BuildConfig
import com.fitghost.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * NanoBananaTryOnEngine - Google Gemini 2.5 Flash Image Preview 가상 피팅 엔진
 *
 * - Google Gemini 2.5 Flash Image Preview API를 사용한 가상 피팅 구현
 * - 모델 사진 + 의상 이미지를 합성하여 전문적인 이커머스 패션 사진 생성
 * - 공식 API 문서 기준: https://ai.google.dev/gemini-api/docs/image-generation
 *
 * API 정보:
 * - 모델명: gemini-2.5-flash-image-preview
 * - 엔드포인트: /v1beta/models/gemini-2.5-flash-image-preview:generateContent
 * - 인증: x-goog-api-key 헤더
 *
 * 요청 형식:
 * - contents 배열에 이미지들과 텍스트 프롬프트 포함
 * - 각 이미지는 inlineData 객체로 base64 인코딩
 *
 * 응답 형식:
 * - candidates[0].content.parts[].inlineData.data에서 base64 이미지 추출
 */
class NanoBananaTryOnEngine(
    private val client: OkHttpClient = createHttpClient()
) : TryOnEngine {

    override suspend fun renderPreview(
        context: Context,
        modelUri: Uri,
        clothingUris: List<Uri>,
        systemPrompt: String?
    ): Bitmap = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.NANOBANANA_API_KEY.orEmpty()
        require(apiKey.isNotBlank()) {
            "NANOBANANA_API_KEY is not set. Please provide Google AI API key in local.properties"
        }

        // Google Gemini API 엔드포인트 (공식 문서 기준)
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image-preview:generateContent"

        try {
            // 이미지들을 PNG 바이트로 변환
            val modelPng = ImageUtils.uriToPngBytes(context, modelUri)
            val clothingPngs = clothingUris.mapNotNull { uri ->
                runCatching { ImageUtils.uriToPngBytes(context, uri) }.getOrElse {
                    Log.w(TAG, "Failed to read clothing image: $uri", it)
                    null
                }
            }

            // Gemini API 요청 본문 생성 (공식 문서 형식 따라)
            val requestBody = buildGeminiVirtualTryOnRequest(modelPng, clothingPngs, systemPrompt)

            Log.d(TAG, "Calling Google Gemini API for virtual try-on (model: gemini-2.5-flash-image-preview, clothes=${clothingPngs.size})")

            val request = Request.Builder()
                .url(apiUrl)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    val errorMsg = "Google Gemini API HTTP ${response.code}: ${response.message} - $errorBody"
                    Log.e(TAG, errorMsg)
                    throw IllegalStateException(errorMsg)
                }

                val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body")
                Log.d(TAG, "Gemini API response received, parsing image...")
                
                val imageBytes = parseGeminiResponse(responseBody)
                return@withContext ImageUtils.decodePng(imageBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Virtual try-on failed", e)
            throw e
        }
    }

    /**
     * Google Gemini API용 가상 피팅 요청 본문 생성
     * 공식 문서 예제 형식을 따름
     */
    private fun buildGeminiVirtualTryOnRequest(
        modelPng: ByteArray,
        clothingPngs: List<ByteArray>,
        systemPrompt: String?
    ): okhttp3.RequestBody {
        // Google Generative Language API 표준 스키마
        // contents: [ { role: "user", parts: [ {inline_data:{mime_type,data}}, ..., {text:"..."} ] } ]
        val parts = JSONArray().apply {
            // 의상 이미지들
            clothingPngs.forEach { clothingPng ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/png")
                            .put("data", Base64.encodeToString(clothingPng, Base64.NO_WRAP))
                    )
                )
            }
            // 모델 이미지
            put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/png")
                        .put("data", Base64.encodeToString(modelPng, Base64.NO_WRAP))
                )
            )
            // 프롬프트 텍스트
            val prompt = buildVirtualTryOnPrompt(systemPrompt, clothingPngs.size)
            put(JSONObject().put("text", prompt))
        }

        val userContent = JSONObject()
            .put("role", "user")
            .put("parts", parts)

        val requestJson = JSONObject()
            .put("contents", JSONArray().put(userContent))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.25)
                    .put("candidateCount", 1)
            )
        
        return requestJson.toString().toRequestBody("application/json".toMediaType())
    }

    /**
     * 가상 피팅을 위한 프롬프트 생성
     * 전문적인 이커머스 패션 사진 스타일로 요청
     */
    private fun buildVirtualTryOnPrompt(systemPrompt: String?, clothingCount: Int): String {
        if (!systemPrompt.isNullOrBlank()) {
            return systemPrompt
        }

        return when (clothingCount) {
            1 -> "Create a professional e-commerce fashion photo. Take the clothing item from the first image and let the person from the second image wear it. Generate a realistic, full-body shot of the person wearing the clothing, with proper lighting and shadows adjusted to match a studio environment. The result should look natural and professional for online shopping."
            
            else -> "Create a professional e-commerce fashion photo. Take the clothing items from the first ${clothingCount} images and create a complete outfit for the person from the last image. Generate a realistic, full-body shot of the person wearing all the clothing items as a coordinated outfit, with proper lighting and shadows adjusted to match a studio environment. The result should look natural and professional for online shopping."
        }
    }

    /**
     * Google Gemini API 응답에서 생성된 이미지 추출
     * 표준 Gemini API 응답 구조를 파싱
     */
    private fun parseGeminiResponse(responseJson: String): ByteArray {
        return try {
            val root = JSONObject(responseJson)
            
            // candidates[0].content.parts[] 구조에서 이미지 찾기
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() == 0) {
                throw IllegalStateException("No candidates in Gemini response")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")

            // parts 배열에서 이미지 데이터가 있는 부분 찾기 (snake_case 우선, camelCase 허용)
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                // snake_case (정식 스키마)
                if (part.has("inline_data")) {
                    val inline = part.getJSONObject("inline_data")
                    val base64Data = inline.optString("data")
                    if (base64Data.isNotBlank()) {
                        Log.d(TAG, "Successfully extracted image (inline_data)")
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
                // 일부 응답 변형(camelCase)도 방어적으로 수용
                if (part.has("inlineData")) {
                    val inline = part.getJSONObject("inlineData")
                    val base64Data = inline.optString("data")
                    if (base64Data.isNotBlank()) {
                        Log.d(TAG, "Successfully extracted image (inlineData)")
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }

            throw IllegalStateException("No image data found in Gemini response parts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response for image", e)
            throw IllegalStateException("Failed to extract image from Gemini response: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "NanoBananaTryOnEngine"
        
        private fun createHttpClient(): OkHttpClient {
            return if (BuildConfig.ALLOW_INSECURE_TLS) buildInsecureClient() else buildDefaultClient()
        }

        private fun buildDefaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // Gemini API는 이미지 생성에 시간이 더 걸림
            .readTimeout(120, TimeUnit.SECONDS)    // 가상 피팅은 복잡한 작업이므로 충분한 시간 필요
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        @Suppress("CustomX509TrustManager")
        private fun buildInsecureClient(): OkHttpClient {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
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
