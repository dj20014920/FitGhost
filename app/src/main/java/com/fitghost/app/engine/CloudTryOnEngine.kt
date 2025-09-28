package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.fitghost.app.utils.ApiKeyManager
import com.fitghost.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * CloudTryOnEngine
 * - Gemini 2.5 이미지 합성(미리보기)으로 가상 피팅 프리뷰 생성
 * - PRD의 개인정보 원칙을 준수하기 위해 기본 비활성(Feature Flag)이며, 사용 시 명시적 옵트인 필요
 * - 실패 시 FakeTryOnEngine으로 폴백
 */
class CloudTryOnEngine(
    private val client: OkHttpClient = OkHttpClient()
) : TryOnEngine {

    override suspend fun renderPreview(
        context: Context,
        modelUri: Uri,
        clothingUris: List<Uri>,
        systemPrompt: String?
    ): Bitmap = withContext(Dispatchers.IO) {
        val apiKey = ApiKeyManager.requireGeminiVertexApiKey()

        // 1) 입력 이미지 로드 → PNG로 인코딩 → base64
        val modelB64 = Base64.encodeToString(ImageUtils.uriToPngBytes(context, modelUri), Base64.NO_WRAP)
        val maxTotal = com.fitghost.app.BuildConfig.MAX_TRYON_TOTAL_IMAGES.coerceAtLeast(2)
        val maxClothes = (maxTotal - 1).coerceAtLeast(1)
        val clothingB64s = clothingUris.take(maxClothes).mapNotNull {
            runCatching { Base64.encodeToString(ImageUtils.uriToPngBytes(context, it), Base64.NO_WRAP) }.getOrNull()
        }

        // 2) 시스템 프롬프트(고정 제약 + 사용자 프롬프트를 system으로 합침)
        val finalSystem = TryOnPromptBuilder.buildSystemText(systemPrompt)

        // 3) REST 요청 페이로드 구성 (Generative Language API)
        val userParts = JSONArray().apply {
            // 텍스트 먼저(시스템 가이드를 결합하여 단일 텍스트로 전달)
            val combinedText = (finalSystem + "\n\n" + TryOnPromptBuilder.buildUserInstruction(hasModel = true, clothingCount = clothingB64s.size)).trim()
            put(JSONObject().put("text", combinedText))
            // 모델 이미지
            put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/png")
                        .put("data", modelB64)
                )
            )
            // 의상 이미지들
            clothingB64s.forEach { b64 ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/png")
                            .put("data", b64)
                    )
                )
            }
        }

        val userContent = JSONObject().put("role", "user").put("parts", userParts)

        val body = JSONObject()
            .put("contents", JSONArray().put(userContent))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.25)
                .put("candidateCount", 1)
            )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.5-flash-image-preview:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 4) API 호출 → 이미지 파싱. 실패 시 로컬 합성 폴백
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${'$'}{resp.code}")
                val text = resp.body?.string() ?: error("Empty body")
                val png = extractInlineImage(text)
                return@withContext ImageUtils.decodePng(png)
            }
        }.getOrElse {
            // 폴백: 로컬 FakeTryOnEngine 사용 (워터마크 프리뷰)
            FakeTryOnEngine().renderPreview(context, modelUri, clothingUris, systemPrompt)
        }
    }

    /**
     * candidates[0].content.parts[*].inlineData.data (또는 inline_data.data)에서 PNG base64 추출
     */
    private fun extractInlineImage(json: String): ByteArray {
        val root = JSONObject(json)
        val candidates = root.optJSONArray("candidates") ?: error("No candidates")
        if (candidates.length() == 0) error("Empty candidates")
        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: error("No content")
        val parts = content.optJSONArray("parts") ?: error("No parts")
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            // camelCase 우선
            if (p.has("inlineData")) {
                val inline = p.getJSONObject("inlineData")
                val dataB64 = inline.getString("data")
                return Base64.decode(dataB64, Base64.DEFAULT)
            }
            // 일부 응답 변형(snake_case) 수용
            if (p.has("inline_data")) {
                val inline = p.getJSONObject("inline_data")
                val dataB64 = inline.getString("data")
                return Base64.decode(dataB64, Base64.DEFAULT)
            }
        }
        error("No inline image in parts")
    }
}
