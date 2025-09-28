package com.fitghost.app.data.network

import android.graphics.Bitmap
import android.util.Log
import com.fitghost.app.data.model.*
import com.fitghost.app.utils.ApiKeyManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import android.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini API를 사용한 패션 AI 서비스
 * 
 * 기존 NanoBanana API를 대체하여 SSL 문제 해결 및 안정성 향상
 * - 텍스트 기반 패션 추천
 * - 이미지 생성 및 편집 (Nano Banana 기능)
 * - 스타일 분석
 */
class GeminiFashionService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiFashionService"
        
        // Gemini 모델 설정
        private const val TEXT_MODEL = "gemini-2.5-flash"
        private const val IMAGE_MODEL = "gemini-2.5-flash-image-preview"
        
        // 응답 설정
        private const val MAX_OUTPUT_TOKENS = 512
        private const val TEMPERATURE = 0.7f
    }

    // 텍스트 전용 모델
    private val textModel by lazy {
        GenerativeModel(
            modelName = TEXT_MODEL,
            apiKey = ApiKeyManager.requireGeminiApiKey(),
            generationConfig = generationConfig {
                temperature = TEMPERATURE
                maxOutputTokens = MAX_OUTPUT_TOKENS
            }
        )
    }
    
    // 이미지 생성 모델
    private val imageModel by lazy {
        GenerativeModel(
            modelName = IMAGE_MODEL,
            apiKey = ApiKeyManager.requireGeminiApiKey(),
            generationConfig = generationConfig {
                temperature = TEMPERATURE
                maxOutputTokens = MAX_OUTPUT_TOKENS
            }
        )
    }

    /**
     * 패션 추천 요청 (텍스트 기반)
     * 
     * @param prompt 추천 요청 프롬프트
     * @param context 컨텍스트 정보
     * @return 패션 추천 결과
     */
    suspend fun getFashionRecommendation(
        prompt: String,
        context: com.fitghost.app.data.model.NanoBananaContext? = null
    ): Result<com.fitghost.app.data.model.FashionRecommendation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "패션 추천 요청: $prompt")
            
            val enhancedPrompt = buildFashionRecommendationPrompt(prompt, context)
            val responseText = generateTextViaRest(enhancedPrompt, maxTokens = 512)
            
            val recommendation = parseFashionRecommendation(responseText)
            Log.d(TAG, "패션 추천 완료")
            
            Result.success(recommendation)
        } catch (e: Exception) {
            Log.e(TAG, "패션 추천 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 스타일 분석 요청
     * 
     * @param prompt 분석 요청 프롬프트
     * @param context 컨텍스트 정보
     * @return 스타일 분석 결과
     */
    suspend fun analyzeStyle(
        prompt: String,
        context: com.fitghost.app.data.model.NanoBananaContext? = null
    ): Result<com.fitghost.app.data.model.FashionRecommendation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "스타일 분석 요청: $prompt")
            
            val enhancedPrompt = buildStyleAnalysisPrompt(prompt, context)
            val responseText = generateTextViaRest(enhancedPrompt, maxTokens = 512)
            
            val analysis = parseStyleAnalysis(responseText)
            Log.d(TAG, "스타일 분석 완료")
            
            Result.success(analysis)
        } catch (e: Exception) {
            Log.e(TAG, "스타일 분석 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 코디 매칭 요청
     * 
     * @param prompt 매칭 요청 프롬프트
     * @param context 컨텍스트 정보
     * @return 코디 매칭 결과
     */
    suspend fun matchOutfit(
        prompt: String,
        context: com.fitghost.app.data.model.NanoBananaContext? = null
    ): Result<com.fitghost.app.data.model.FashionRecommendation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "코디 매칭 요청: $prompt")
            
            val enhancedPrompt = buildOutfitMatchingPrompt(prompt, context)
            val responseText = generateTextViaRest(enhancedPrompt, maxTokens = 512)
            
            val matching = parseOutfitMatching(responseText)
            Log.d(TAG, "코디 매칭 완료")
            
            Result.success(matching)
        } catch (e: Exception) {
            Log.e(TAG, "코디 매칭 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 이미지 생성 요청 (Nano Banana 기능)
     * 
     * @param prompt 이미지 생성 프롬프트
     * @param context 컨텍스트 정보
     * @return 생성된 이미지 데이터
     */
    suspend fun generateFashionImage(
        prompt: String,
        context: NanoBananaContext? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        Log.d(TAG, "패션 이미지 생성 요청: $prompt")
        val enhancedPrompt = buildImageGenerationPrompt(prompt, context)
        val apiKey = ApiKeyManager.requireGeminiApiKey()
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("role","user").put("parts", JSONArray().put(
                        JSONObject().put("text", enhancedPrompt)
                    ))
                ))
                .put("generationConfig", JSONObject()
                    .put("temperature", TEMPERATURE)
                    .put("candidateCount", 1)
                )
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$IMAGE_MODEL:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${'$'}{resp.code}")
                val json = resp.body?.string() ?: error("Empty body")
                val bytes = extractInlinePng(json)
                Log.d(TAG, "패션 이미지 생성 완료")
                return@withContext Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "패션 이미지 생성 실패", e)
            return@withContext Result.failure(e)
        }
    }

    // 이미지 생성 프롬프트(REST)
    private fun buildImageGenerationPrompt(
        prompt: String,
        context: NanoBananaContext?
    ): String = """
        패션 전문 스타일리스트의 관점에서 세련되고 트렌디한 패션 이미지를 생성해주세요.
        
        요구사항:
        - 고품질의 패션 사진 스타일
        - 자연스러운 조명과 구도
        - 현실적이고 착용 가능한 스타일
        - 깔끔하고 전문적인 느낌
        
        생성 요청: $prompt
    """.trimIndent()

    private fun extractTextFromCandidates(responseJson: String): String {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until candidates.length()) {
            val cand = candidates.optJSONObject(i) ?: continue
            val content = cand.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val txt = part.optString("text")
                if (txt.isNotBlank()) sb.append(txt)
            }
            if (sb.isNotEmpty()) break
        }
        return sb.toString()
    }

    /**
     * REST JSON에서 inlineData PNG(Base64)를 추출하는 공통 함수
     */
    private fun extractInlinePng(responseJson: String): ByteArray {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates") ?: error("No candidates in response")
        for (i in 0 until candidates.length()) {
            val cand = candidates.optJSONObject(i) ?: continue
            val content = cand.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val inline = part.optJSONObject("inlineData")
                if (inline != null) {
                    val mime = inline.optString("mimeType")
                    val data = inline.optString("data")
                    if (mime.equals("image/png", ignoreCase = true) && data.isNotBlank()) {
                        return Base64.decode(data, Base64.DEFAULT)
                    }
                }
                if (part.has("data") && part.has("mimeType") &&
                    part.optString("mimeType").equals("image/png", ignoreCase = true)) {
                    val data = part.optString("data")
                    if (data.isNotBlank()) return Base64.decode(data, Base64.DEFAULT)
                }
            }
        }
        error("PNG data not found in response")
    }

    // ============= 텍스트 프롬프트 생성 =============

    private fun buildContextWardrobeInfo(context: NanoBananaContext?): String {
        val wardrobeInfo = context?.wardrobeItems?.takeIf { it.isNotEmpty() }?.joinToString("\n") { item ->
            "- ${item.category} ${item.brand ?: ""} ${item.style ?: ""}".trim()
        } ?: ""
        return if (wardrobeInfo.isNotEmpty()) "\n\n현재 보유 아이템:\n$wardrobeInfo" else ""
    }

    private fun buildFashionRecommendationPrompt(
        prompt: String,
        context: NanoBananaContext?
    ): String {
        val systemPrompt = """
        당신은 전문적인 패션 스타일리스트입니다. 사용자의 요청에 따라 개인화된 패션 추천을 제공해주세요.
        
        응답 형식:
        제목: [추천 제목]
        설명: [상세 설명]
        추천 아이템:
        1. [아이템 1] - [색상] [스타일] [설명]
        2. [아이템 2] - [색상] [스타일] [설명]
        3. [아이템 3] - [색상] [스타일] [설명]
        이유: [추천 근거와 스타일링 팁]
        상황: [적합한 상황/장소]
        신뢰도: [80-95]%
        """.trimIndent()
        val contextInfo = buildContextWardrobeInfo(context)
        return "$systemPrompt\n\n사용자 요청: $prompt$contextInfo"
    }

    private fun buildStyleAnalysisPrompt(
        prompt: String,
        context: NanoBananaContext?
    ): String {
        val systemPrompt = """
        패션 스타일 전문 분석가로서 사용자의 스타일을 분석하고 개선점을 제안해주세요.
        
        응답 형식:
        제목: 스타일 분석 결과
        설명: [전체적인 스타일 평가]
        분석 결과:
        1. 주요 스타일: [분석된 스타일]
        2. 강점: [스타일의 장점]
        3. 개선점: [개선 가능한 부분]
        4. 추천 방향: [발전 방향]
        이유: [분석 근거와 전문적 의견]
        상황: [현재 스타일에 적합한 상황]
        신뢰도: [85-95]%
        """.trimIndent()
        return "$systemPrompt\n\n분석 요청: $prompt"
    }

    private fun buildOutfitMatchingPrompt(
        prompt: String,
        context: NanoBananaContext?
    ): String {
        val systemPrompt = """
        패션 코디네이터로서 완벽한 아웃핏 조합을 만들어주세요.
        
        응답 형식:
        제목: 완벽 코디 매칭
        설명: [코디 컨셉과 전체적인 느낌]
        매칭 아이템:
        1. 상의: [아이템] - [색상] [소재] [핏]
        2. 하의: [아이템] - [색상] [소재] [핏]  
        3. 아우터: [아이템] - [색상] [소재] [스타일]
        4. 신발: [아이템] - [색상] [스타일]
        5. 액세서리: [아이템] - [색상] [스타일]
        이유: [매칭 근거와 색상/소재 조화 설명]
        상황: [이 코디에 적합한 장소와 상황]
        신뢰도: [90-95]%
        """.trimIndent()
        return "$systemPrompt\n\n매칭 요청: $prompt"
    }

    // ============= 텍스트 생성(REST 단일화) =============

    private suspend fun generateTextViaRest(
        enhancedPrompt: String,
        maxTokens: Int = 512
    ): String {
        val apiKey = ApiKeyManager.requireGeminiApiKey()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$TEXT_MODEL:generateContent?key=$apiKey"

        var attempt = 0
        var tokens = maxTokens
        var lastError: Exception? = null

        while (attempt < 3) {
            try {
                val body = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("role","user").put("parts", JSONArray().put(
                            JSONObject().put("text", enhancedPrompt)
                        ))
                    ))
                    .put("generationConfig", JSONObject()
                        .put("temperature", TEMPERATURE)
                        .put("maxOutputTokens", tokens)
                        .put("candidateCount", 1)
                    )

                val req = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${'$'}{resp.code}")
                    val json = resp.body?.string()?.trim().orEmpty()
                    return extractTextFromCandidates(json)
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastError = e
                attempt += 1
                tokens = (tokens / 2).coerceAtLeast(128)
                val backoffMs = 500L * attempt
                Log.w(TAG, "REST timeout (attempt=$attempt), retrying with maxTokens=$tokens in ${'$'}backoffMs ms", e)
                kotlinx.coroutines.delay(backoffMs)
            } catch (e: Exception) {
                lastError = e
                attempt += 1
                val backoffMs = 500L * attempt
                Log.w(TAG, "REST error (attempt=$attempt), retrying in ${'$'}backoffMs ms", e)
                kotlinx.coroutines.delay(backoffMs)
            }
        }
        Log.e(TAG, "REST text generation failed after retries", lastError)
        throw lastError ?: RuntimeException("Unknown REST failure")
    }

    // ============= 이미지 편집(REST) =============

    suspend fun editFashionImage(
        prompt: String,
        inputImage: android.graphics.Bitmap,
        context: NanoBananaContext? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        Log.d(TAG, "패션 이미지 편집 요청: $prompt")
        val apiKey = ApiKeyManager.requireGeminiApiKey()
        val enhancedPrompt = buildImageEditingPrompt(prompt, context)
        val bos = java.io.ByteArrayOutputStream()
        inputImage.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
        val pngB64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("text", enhancedPrompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/png")
                        .put("data", pngB64)
                )
            )
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role","user").put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("temperature", TEMPERATURE)
                .put("candidateCount", 1)
            )
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$IMAGE_MODEL:generateContent?key=$apiKey"
        try {
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${'$'}{resp.code}")
                val json = resp.body?.string() ?: error("Empty body")
                val bytes = extractInlinePng(json)
                Log.d(TAG, "패션 이미지 편집 완료")
                return@withContext Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "패션 이미지 편집 실패", e)
            return@withContext Result.failure(e)
        }
    }

    // 이미지 편집 프롬프트
    private fun buildImageEditingPrompt(
        prompt: String,
        context: NanoBananaContext?
    ): String = """
        제공된 패션 이미지를 전문적으로 편집해주세요.
        
        편집 지침:
        - 원본 이미지의 전체적인 스타일과 조화 유지
        - 자연스럽고 현실적인 편집
        - 패션 전문성을 고려한 색상과 스타일 매칭
        - 고품질 결과물 보장
        
        편집 요청: $prompt
    """.trimIndent()

    private fun parseFashionRecommendation(responseText: String): FashionRecommendation {
        return FashionRecommendation(
            id = UUID.randomUUID().toString(),
            title = extractTitle(responseText) ?: "AI 패션 추천",
            description = extractDescription(responseText) ?: responseText.take(200),
            recommendedItems = extractRecommendedItems(responseText),
            reasoning = extractReasoning(responseText) ?: responseText,
            confidence = extractConfidence(responseText) ?: 0.85f,
            occasion = extractOccasion(responseText)
        )
    }
    
    private fun parseStyleAnalysis(responseText: String): FashionRecommendation {
        return FashionRecommendation(
            id = UUID.randomUUID().toString(),
            title = "스타일 분석 결과",
            description = extractDescription(responseText) ?: responseText.take(200),
            recommendedItems = extractRecommendedItems(responseText),
            reasoning = extractReasoning(responseText) ?: responseText,
            confidence = extractConfidence(responseText) ?: 0.90f,
            occasion = extractOccasion(responseText)
        )
    }
    
    private fun parseOutfitMatching(responseText: String): FashionRecommendation {
        return FashionRecommendation(
            id = UUID.randomUUID().toString(),
            title = "코디 매칭 결과", 
            description = extractDescription(responseText) ?: responseText.take(200),
            recommendedItems = extractRecommendedItems(responseText),
            reasoning = extractReasoning(responseText) ?: responseText,
            confidence = extractConfidence(responseText) ?: 0.92f,
            occasion = extractOccasion(responseText)
        )
    }
    
    // ============= 텍스트 추출 헬퍼 메소드들 =============
    
    private fun extractTitle(content: String): String? {
        val titleRegex = Regex("제목[:：]\\s*(.+)")
        return titleRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractDescription(content: String): String? {
        val descRegex = Regex("설명[:：]\\s*(.+?)(?=추천 아이템|분석 결과|매칭 아이템|이유|$)", RegexOption.DOT_MATCHES_ALL)
        return descRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractRecommendedItems(content: String): List<RecommendedItem> {
        val items = mutableListOf<RecommendedItem>()
        
        val itemSections = listOf(
            "추천 아이템[:：]\\s*([\\s\\S]*?)(?=이유|상황|신뢰도|$)",
            "분석 결과[:：]\\s*([\\s\\S]*?)(?=이유|상황|신뢰도|$)",
            "매칭 아이템[:：]\\s*([\\s\\S]*?)(?=이유|상황|신뢰도|$)"
        )
        
        for (sectionPattern in itemSections) {
            val sectionMatch = Regex(sectionPattern).find(content)
            if (sectionMatch != null) {
                val sectionContent = sectionMatch.groupValues[1]
                val itemRegex = Regex("\\d+\\. (.+?)(?=\\d+\\.|$)", RegexOption.DOT_MATCHES_ALL)
                
                itemRegex.findAll(sectionContent).forEach { match ->
                    val description = match.groupValues[1].trim()
                    if (description.isNotEmpty()) {
                        items.add(
                            RecommendedItem(
                                category = extractCategory(description),
                                description = description,
                                color = extractColor(description),
                                style = extractStyle(description),
                                priceRange = null,
                                searchKeywords = extractKeywords(description)
                            )
                        )
                    }
                }
                break
            }
        }
        
        return items
    }

    private fun extractReasoning(content: String): String? {
        val reasoningRegex = Regex("이유[:：]\\s*(.+?)(?=상황|신뢰도|$)", RegexOption.DOT_MATCHES_ALL)
        return reasoningRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractConfidence(content: String): Float? {
        val confidenceRegex = Regex("신뢰도[:：]\\s*(\\d+(?:\\.\\d+)?)%?")
        val match = confidenceRegex.find(content)
        return match?.groupValues?.get(1)?.toFloatOrNull()?.div(100f)
    }

    private fun extractOccasion(content: String): String? {
        val occasionRegex = Regex("상황[:：]\\s*(.+?)(?=신뢰도|$)", RegexOption.DOT_MATCHES_ALL)
        return occasionRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractCategory(text: String): String {
        val categories = mapOf(
            "상의" to listOf("티셔츠", "셔츠", "블라우스", "니트", "후드", "맨투맨", "탱크톱"),
            "하의" to listOf("청바지", "바지", "치마", "반바지", "슬랙스", "레깅스"),
            "아우터" to listOf("코트", "재킷", "가디건", "점퍼", "패딩", "블레이저"),
            "신발" to listOf("운동화", "구두", "부츠", "샌들", "슬리퍼", "로퍼"),
            "가방" to listOf("핸드백", "백팩", "크로스백", "토트백", "클러치"),
            "액세서리" to listOf("시계", "목걸이", "귀걸이", "반지", "모자", "선글라스", "스카프")
        )
        
        for ((category, keywords) in categories) {
            if (keywords.any { text.contains(it, ignoreCase = true) }) {
                return category
            }
        }
        return "기타"
    }

    private fun extractColor(text: String): String? {
        val colors = listOf(
            "빨간", "빨강", "red", "파란", "파랑", "blue", "노란", "노랑", "yellow",
            "초록", "green", "검은", "검정", "black", "흰", "하얀", "white",
            "회색", "gray", "grey", "갈색", "brown", "분홍", "pink", "보라", "purple",
            "주황", "orange", "베이지", "beige", "네이비", "navy", "카키", "khaki"
        )
        return colors.find { text.contains(it, ignoreCase = true) }
    }

    private fun extractStyle(text: String): String? {
        val styles = listOf(
            "캐주얼", "casual", "포멀", "formal", "스포티", "sporty",
            "빈티지", "vintage", "모던", "modern", "클래식", "classic",
            "미니멀", "minimal", "로맨틱", "romantic", "시크", "chic"
        )
        return styles.find { text.contains(it, ignoreCase = true) }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.split(" ", ",", ".", "!", "-", ":")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .take(5)
    }
}
