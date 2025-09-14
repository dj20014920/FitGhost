package com.fitghost.app.data.nanobanana

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Gemini(나노바나나) 이미지 생성/편집 클라이언트.
 *
 * 목표
 * - 서버리스 구성: API 키는 ApiKeys 유틸리티를 통해 안전하게 저장/접근
 * - DRY: 요청/응답 모델을 단일화하여 모든 호출 경로에서 재사용
 * - KISS/YAGNI: 최소한의 러닝 파트로 MVP 완성, 추후 확장(옵션 필드/엔드포인트)은 용이하게
 *
 * 주의
 * - 실제 엔드포인트/필드는 공식 문서 기준으로 업데이트 필요(여기선 REST v1beta 스키마 사용)
 * - API 키는 ApiKeys.KEY_GEMINI에서 조회하며, 없을 경우 예외 발생
 *
 * 권장 사용 패턴
 * val client = GeminiClient(context)
 * val result = client.generateImage(
 *   prompt = "Create a picture ...",
 *   model = GeminiClient.DEFAULT_IMAGE_MODEL
 * )
 * // 결과는 base64 → Bitmap 변환 유틸 제공
 */
class GeminiClient(
    private val context: Context,
    private val baseUrl: String = DEFAULT_BASE_URL,
    httpClient: OkHttpClient? = null,
    moshi: Moshi? = null
) {

    companion object {
        /** 공식 REST 베이스 URL(Generative Language API) — 문서 변경 시 업데이트 필요 */
        const val DEFAULT_BASE_URL: String = "https://generativelanguage.googleapis.com/"

        /** 구글 예제에서 소개된 이미지 프리뷰 모델명 (변경 가능) */
        const val DEFAULT_IMAGE_MODEL: String = "gemini-2.5-flash-image-preview"

        private val JSON by lazy { "application/json; charset=utf-8".toMediaType() }
    }

    private val moshi: Moshi = moshi ?: Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            httpClient ?: OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(this.moshi))
        .build()

    private val api: GeminiApi = retrofit.create(GeminiApi::class.java)

    // region Public APIs

    /**
     * ApiKeys 유틸리티에서 Gemini API 키를 가져옵니다.
     * 키가 없으면 IllegalStateException을 발생시킵니다.
     * 
     * @return Gemini API 키
     * @throws IllegalStateException API 키가 설정되지 않은 경우
     */
    private fun requireGeminiApiKey(): String {
        val key = com.fitghost.app.util.ApiKeys.provider.geminiApiKey(context)
        if (key.isNullOrBlank()) {
            throw IllegalStateException("Gemini API 키가 설정되지 않았습니다. 설정 화면에서 API 키를 등록해 주세요.")
        }
        return key
    }

    /**
     * 텍스트 → 이미지 생성.
     * @param prompt 설명 프롬프트
     * @param model 모델명(기본: [DEFAULT_IMAGE_MODEL])
     * @param systemPrompt 시스템 프롬프트(옵션: 정책/제약/가이드라인)
     * @return 응답 내 inline_data 이미지 바이트 배열 리스트(0개일 수 있음)
     */
    suspend fun generateImage(
        prompt: String,
        model: String = DEFAULT_IMAGE_MODEL,
        systemPrompt: String? = null
    ): List<ByteArray> {
        val apiKey = requireGeminiApiKey()
        val contents = mutableListOf(
            Content(parts = listOf(Part(text = prompt)))
        )

        val body = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemPrompt?.let {
                Content(parts = listOf(Part(text = it)))
            }
        )

        val response = api.generateContent(model = model, body = body, apiKey = apiKey)
        return response.extractInlineImages()
    }

    /**
     * 이미지 편집(텍스트 + 이미지 입력).
     * @param prompt 편집 프롬프트
     * @param images 입력 이미지(최대 3장 권장 — 모델 제약 참고)
     * @param model 모델명
     * @param systemPrompt 시스템 프롬프트(옵션)
     */
    suspend fun editImage(
        prompt: String,
        images: List<ByteArray>,
        model: String = DEFAULT_IMAGE_MODEL,
        systemPrompt: String? = null
    ): List<ByteArray> {
        val apiKey = requireGeminiApiKey()
        val parts = mutableListOf<Part>()
        parts += Part(text = prompt)
        images.take(3).forEach { img ->
            parts += Part(
                inlineData = InlineData(
                    mimeType = sniffMimeType(img) ?: "image/png",
                    data = img.encodeBase64()
                )
            )
        }

        val body = GenerateContentRequest(
            contents = listOf(Content(parts = parts)),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) }
        )

        val response = api.generateContent(model = model, body = body, apiKey = apiKey)
        return response.extractInlineImages()
    }

    /**
     * 고급 합성(여러 이미지 결합/스타일 전이 등).
     * - 프롬프트로 결합 규칙/전이 스타일을 자세히 설명
     */
    suspend fun composeImages(
        prompt: String,
        images: List<ByteArray>,
        model: String = DEFAULT_IMAGE_MODEL,
        systemPrompt: String? = null
    ): List<ByteArray> = editImage(prompt = prompt, images = images, model = model, systemPrompt = systemPrompt)

    // endregion

    
    // region Helpers

    private fun ByteArray.encodeBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun sniffMimeType(bytes: ByteArray): String? {
        // 매우 단순한 시그니처 스니핑
        return when {
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            else -> null
        }
    }

    /** base64 → Bitmap */
    fun decodeBase64ImageToBitmap(b64: String): Bitmap {
        val data = Base64.decode(b64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    /** Bitmap → PNG ByteArray */
    fun bitmapToPngBytes(bm: Bitmap, quality: Int = 100): ByteArray {
        val bos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.PNG, quality, bos)
        return bos.toByteArray()
    }

    // endregion
}

/**
 * Retrofit API 스켈레톤 — 공식 문서에 따라 필드/엔드포인트 업데이트 필요.
 * - POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=API_KEY
 */
private interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body body: GenerateContentRequest,
        @Query("key") apiKey: String
    ): GenerateContentResponse
}

/* ============================
   Request/Response Models (DRY)
   ============================ */

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,

    // 최신 스펙에 맞춰 옵션 필드 확장 가능 (YAGNI: 필요 시 추가)
    @Json(name = "system_instruction")
    val systemInstruction: Content? = null,

    @Json(name = "generation_config")
    val generationConfig: GenerationConfig? = null,

    @Json(name = "safety_settings")
    val safetySettings: List<SafetySetting>? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,

    @Json(name = "inline_data")
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mime_type")
    val mimeType: String,
    val data: String // base64
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback? = null
) {
    /** 응답 내 inline_data 이미지를 모두 추출 */
    fun extractInlineImages(): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        candidates.orEmpty().forEach { cand ->
            cand.content?.parts.orEmpty().forEach { p ->
                val b64 = p.inlineData?.data ?: return@forEach
                kotlin.runCatching {
                    Base64.decode(b64, Base64.DEFAULT)
                }.onSuccess { out += it }
            }
        }
        return out
    }
}

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val index: Int? = null,
    val safetyRatings: List<SafetyRating>? = null
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val safetyRatings: List<SafetyRating>? = null
)

@JsonClass(generateAdapter = true)
data class SafetySetting(
    val category: String,
    val threshold: String
)

@JsonClass(generateAdapter = true)
data class SafetyRating(
    val category: String,
    val probability: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val candidateCount: Int? = null
)

/* ============================
   System Prompt Template (Optional)
   ============================ */

/**
 * 가상피팅에 특화된 시스템 프롬프트 템플릿(예시).
 * - 호출부에서 필요 시 전달하여 일관된 합성 품질을 유도
 */
object VtoSystemPrompt {
    fun default(): String = """
        당신은 패션 가상피팅 전문 엔진입니다.
        목표:
        1) 사용자 인물 사진(또는 매니킨) 위에 주어진 의류 이미지를 자연스럽게 합성합니다.
        2) 얼굴/헤어/피부 톤은 보존하며, 왜곡/성적 대상화/신체 변형을 금지합니다.
        3) 카테고리별 기준 정렬:
           - 상의: 어깨선/가슴/허리 정렬, 자연스러운 주름/그림자
           - 하의: 허리선/엉덩이/다리 길이 정합
           - 아우터: 상의 위 레이어, 두께감 반영
           - 신발: 발 위치/원근 보정
           - 액세서리: 얼굴/손 등 위치 정합
        출력:
        - 고해상도 합성 이미지(base64 또는 URL)
        - (가능 시) 사용된 마스크/랜드마크 메타
        제약:
        - 부적절한 컨텐츠/저작권 불명확 이미지 거부
        - 색상/소재/패턴은 원본을 보존하되 사진 조명에 최소한으로 맞춤
    """.trimIndent()
}
