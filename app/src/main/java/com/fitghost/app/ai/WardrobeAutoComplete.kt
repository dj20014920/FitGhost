package com.fitghost.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
// 온디바이스 VLM은 llama.cpp 서버(OpenAI 호환)로 처리
import java.io.ByteArrayOutputStream

/**
 * 옷장 아이템 자동 완성 엔진 (온디바이스)
 *
 * 책임:
 * - 온디바이스 SmolVLM 모델을 사용한 옷 이미지 분석
 * - 메타데이터 추출 (카테고리, 색상, 브랜드 등)
 * - 구조화된 응답 파싱
 *
 * 사용 예:
 * ```
 * val autoComplete = WardrobeAutoComplete(context)
 * val result = autoComplete.analyzeClothingImage(bitmap)
 * result.onSuccess { metadata ->
 *     // UI 업데이트
 * }
 * ```
 */
class WardrobeAutoComplete(private val context: Context) {

    /**
     * 기존 입력값(사용자 혹은 이전 값) - 모델 프롬프트에 함께 제공하여 정밀 자동완성
     */
    data class ExistingFields(
        val name: String = "",
        val category: com.fitghost.app.data.db.WardrobeCategory? = null,
        val brand: String = "",
        val color: String = "",
        val size: String = "",
        val detailType: String = "",
        val patternOrMaterial: String = "",
        val tags: List<String> = emptyList(),
        val memo: String = ""
    )

    companion object {
        private const val TAG = "WardrobeAutoComplete"
        @Volatile private var warmedOnce: Boolean = false
        
        // 시스템 지시문 (OpenAI 호환 Chat 시스템 역할용)
        // 출력은 오직 단일 JSON 오브젝트 하나만 허용됩니다.
        // 스키마를 반드시 준수하고, 여분의 텍스트/코드펜스/주석은 금지합니다.
        private const val ANALYSIS_PROMPT_TEMPLATE = """
You are a fashion item analysis assistant. Analyze the clothing IMAGE together with the GIVEN EXISTING FIELDS and fill a single JSON object. Output ONLY the JSON (no extra text, no markdown), start with '{' and end with '}'.

Dropdown options (must choose exactly from these; if not applicable, choose OTHER and provide a free-text string):
- category: TOP, BOTTOM, OUTER, SHOES, ACCESSORY, OTHER
- color (examples, not exhaustive): black, white, gray, navy, blue, red, green, yellow, beige, brown, ivory, cream, pink, purple, khaki, orange
- pattern/material (examples): solid, stripe, plaid, denim, leather, knit, wool, cotton, linen

Rules:
- If a field is unknown or the model is not confident, return an empty string "" for that field (NEVER use placeholder like "string" or "...").
- For tags, always return a JSON array of strings; can be [].
- Sweater/Knit guideline (Korean fashion context):
  - TOP: thin knits worn as inner layer.
  - OUTER: thick knits/cardigans worn as outerwear.
- Socks/Gloves/Scarves/Belts/Watches → ACCESSORY.
- Do NOT confuse socks with shirts. If no collar/sleeves/buttons and tubular/foot-shaped, it is likely socks.
- Keep description <= 200 chars.

The JSON schema to output:
{
  "category": "TOP|BOTTOM|OUTER|SHOES|ACCESSORY|OTHER",
  "name": "descriptive name or empty",
  "brand": "brand or empty",
  "color": "from dropdown or empty",
  "size": "free text or empty",
  "detailType": "specific type (e.g., hoodie, jeans) or empty",
  "pattern": "pattern/material from dropdown if possible, else empty",
  "tags": ["tag1", "tag2"],
  "description": "brief description or empty"
}
"""
        
        // 모델 파라미터
        private const val MAX_TOKENS = 128
        private const val TEMPERATURE = 0.15f // JSON 안정성 우선, 소폭 다양성
        private const val CONTEXT_LENGTH = 2048
    }
    
    /**
     * 옷 메타데이터 분석 결과
     */
    data class ClothingMetadata(
        val category: String,
        val name: String,
        val color: String,
        val detailType: String,
        val pattern: String,
        val brand: String,
        val tags: List<String>,
        val description: String
    ) {
        companion object {
            /**
             * JSON에서 파싱
             */
            fun fromJson(jsonStr: String): ClothingMetadata {
                val json = JSONObject(jsonStr)
                
                val tagsArray = json.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(i))
                    }
                }
                
                return ClothingMetadata(
                    category = json.optString("category", "OTHER"),
                    name = json.optString("name", ""),
                    color = json.optString("color", ""),
                    detailType = json.optString("detailType", ""),
                    pattern = json.optString("pattern", ""),
                    brand = json.optString("brand", ""),
                    tags = tags,
                    description = json.optString("description", "")
                )
            }
        }
        
        /**
         * 카테고리를 WardrobeCategory enum으로 변환
         */
        fun toCategoryEnum(): com.fitghost.app.data.db.WardrobeCategory {
            return when (category.uppercase()) {
                "TOP" -> com.fitghost.app.data.db.WardrobeCategory.TOP
                "BOTTOM" -> com.fitghost.app.data.db.WardrobeCategory.BOTTOM
                "OUTER" -> com.fitghost.app.data.db.WardrobeCategory.OUTER
                "SHOES" -> com.fitghost.app.data.db.WardrobeCategory.SHOES
                "ACCESSORY" -> com.fitghost.app.data.db.WardrobeCategory.ACCESSORY
                else -> com.fitghost.app.data.db.WardrobeCategory.OTHER
            }
        }
    }
    
    // HTTP 클라이언트 제거: 임베드 서버 JNI로 직접 호출
    
    /**
     * 옷 이미지 분석
     *
     * @param image 분석할 옷 이미지 (Bitmap)
     * @return 분석 결과 (성공/실패)
     */
    suspend fun analyzeClothingImage(image: Bitmap, existing: ExistingFields = ExistingFields()): Result<ClothingMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting clothing image analysis (cloud-first)...")
                // 1) PRD: Cloud-only (Gemini 2.5 Flash Lite, JSON 강제) — 폴백 없음
                runCatching {
                    val res = com.fitghost.app.ai.cloud.GeminiTagger.tagImage(image)
                    res.getOrThrow()
                }.onSuccess { json ->
                    val mapped = com.fitghost.app.ai.cloud.GeminiTagger.toClothingMetadata(json)
                    Log.i(TAG, "Cloud tagging success")
                    return@withContext Result.success(cleanPlaceholders(mapped))
                }.onFailure { e ->
                    Log.e(TAG, "Cloud tagging failed: ${e.message}")
                    return@withContext Result.failure(Exception("클라우드 자동 태깅 실패: ${e.message}"))
                }

                // 아래 코드는 실행되지 않음(클라우드 경로에서 반환).
                val modelManager = ModelManager.getInstance(context)
                var modelPath = modelManager.getModelPath()
                if (modelPath == null || !java.io.File(modelPath).exists()) {
                    Log.i(TAG, "Model not ready. Starting download...")
                    val dl = modelManager.downloadModel { p ->
                        Log.d(TAG, "Download: ${'$'}{p.percentage}% (${ '$'}{p.downloadedMB}/${'$'}{p.totalMB} MB)")
                    }
                    dl.onFailure { e ->
                        return@withContext Result.failure(Exception("모델 다운로드 실패: ${'$'}{e.message}"))
                    }
                    modelPath = dl.getOrNull()
                    if (modelPath.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("모델 경로 확인 실패"))
                    }
                }
                
                // 임베드 서버 JNI로 기동 (동일 프로세스)
                val mmprojPath = modelManager.getMmprojPath()
                // 빌드 시 임베디드 비활성화이면 곧장 휴리스틱으로 폴백
                if (!com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
                    Log.w(TAG, "Embedded Llama disabled by build; falling back to heuristic")
                    val rough = ClothingMetadata.fromJson(buildHeuristicJson(""))
                    return@withContext Result.success(rough)
                }

                val started = LlamaServerController(context).ensureRunning(
                    modelPath = modelPath,
                    mmprojPath = mmprojPath
                )
                if (!started) {
                    return@withContext Result.failure(Exception("내장 Llama 엔진을 시작할 수 없습니다"))
                }
                
                // 이미지를 PNG 바이트로 변환 (JNI에 직접 전달)
                val pngBytes = toPngBytes(image)
                // Vision 입력(멀티모달) 직접 추론 호출
                Log.d(TAG, "Calling embedded Llama engine (multimodal, direct)...")
                val existingText = buildString {
                    append("Existing fields (may be blank):\n")
                    append("name: ").append(existing.name).append('\n')
                    append("category: ").append(existing.category?.name ?: "").append('\n')
                    append("brand: ").append(existing.brand).append('\n')
                    append("color: ").append(existing.color).append('\n')
                    append("size: ").append(existing.size).append('\n')
                    append("detailType: ").append(existing.detailType).append('\n')
                    append("pattern/material: ").append(existing.patternOrMaterial).append('\n')
                    append("tags: ").append(existing.tags.joinToString("; ")).append('\n')
                    append("memo: ").append(existing.memo)
                }
                var response = EmbeddedLlamaServer.nativeAnalyze(
                    systemPrompt = ANALYSIS_PROMPT_TEMPLATE,
                    userText = "Fill the JSON for this clothing IMAGE using the rules above. Use dropdown options when possible; if not, set OTHER and free text.\n" + existingText,
                    imagePng = pngBytes,
                    temperature = TEMPERATURE.toDouble(),
                    maxTokens = MAX_TOKENS
                )

                Log.d(TAG, "Model response: $response")

                // JSON 파싱 (실패 시 1회 재시도: 더 엄격한 JSON 전용 지시문)
                val metadata = runCatching { parseResponse(response) }.getOrElse { firstErr ->
                    Log.w(TAG, "First parse failed, retrying with strict JSON prompt", firstErr)
                    response = EmbeddedLlamaServer.nativeAnalyze(
                        systemPrompt = ANALYSIS_PROMPT_TEMPLATE,
                        userText = "Output ONLY one JSON object. Start with '{'. IMPORTANT: Never use placeholder like 'string' or '...'. If unknown, use empty string.\n" + existingText,
                        imagePng = pngBytes,
                        temperature = 0.05,
                        maxTokens = MAX_TOKENS
                    )
                    Log.d(TAG, "Model response (retry): $response")
                    runCatching { parseResponse(response) }.getOrElse { secondErr ->
                        Log.w(TAG, "Second parse failed, applying heuristic fallback", secondErr)
                        ClothingMetadata.fromJson(buildHeuristicJson(response))
                    }
                }
                
                Log.i(TAG, "Analysis completed successfully: ${metadata.name}")
                Result.success(metadata)
                
            } catch (e: JSONException) {
                Log.e(TAG, "JSON parsing error", e)
                Result.failure(Exception("응답 파싱 실패: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing image", e)
                Result.failure(Exception("이미지 분석 실패: ${e.message}"))
            }
        }
    }
    
    /**
     * Bitmap을 Base64로 변환
     */
    private fun toPngBytes(bitmap: Bitmap): ByteArray {
        val resized = resizeForVision(bitmap, 384)
        val os = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.PNG, 100, os)
        return os.toByteArray()
    }

    private fun resizeForVision(src: Bitmap, target: Int): Bitmap {
        // 1) 긴 변 기준으로 축소 스케일 계산
        val scale = target / maxOf(src.width, src.height).toFloat()
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        // 2) 중앙 기준 정사각형 512x512 크롭(패딩 없이)
        val left = ((w - target) / 2).coerceAtLeast(0)
        val top = ((h - target) / 2).coerceAtLeast(0)
        val cw = minOf(target, scaled.width)
        val ch = minOf(target, scaled.height)
        val cropped = Bitmap.createBitmap(scaled, left, top, cw, ch)
        // 3) 만약 한 변이 target보다 작은 경우, 최종적으로 정확히 target 정사각형으로 리스케일
        return if (cw != target || ch != target) {
            Bitmap.createScaledBitmap(cropped, target, target, true)
        } else cropped
    }
    
    // OpenAI 호환 서버로 전환됨: 별도 프롬프트/콜백 기반 로컬 바인딩 로직 삭제
    
    /**
     * 모델 응답 파싱
     */
    private fun parseResponse(responseText: String): ClothingMetadata {
        // JSON 부분만 추출
        val jsonText = extractJson(responseText)

        if (jsonText.isBlank()) {
            throw JSONException("No valid JSON found in response")
        }

        val meta = ClothingMetadata.fromJson(jsonText)
        return cleanPlaceholders(meta)
    }
    
    /**
     * 응답에서 JSON 추출
     */
    private fun extractJson(text: String): String {
        // 1) ```json ... ```
        val codeBlockRegex = "```json\\s*(.+?)\\s*```".toRegex(RegexOption.DOT_MATCHES_ALL)
        codeBlockRegex.find(text)?.let { return it.groupValues[1].trim() }

        // 2) ``` ... ``` (json prefix 제거)
        val simpleCodeBlockRegex = "```\\s*(.+?)\\s*```".toRegex(RegexOption.DOT_MATCHES_ALL)
        simpleCodeBlockRegex.find(text)?.let {
            val content = it.groupValues[1].trim()
            return content.removePrefix("json").trim()
        }

        // 3) 균형 잡힌 첫 번째 JSON 오브젝트 스캔
        val s = text
        var i = s.indexOf('{')
        while (i >= 0) {
            var depth = 0
            var inStr = false
            var esc = false
            for (j in i until s.length) {
                val c = s[j]
                if (inStr) {
                    if (esc) { esc = false; continue }
                    if (c == '\\') { esc = true; continue }
                    if (c == '"') { inStr = false; continue }
                } else {
                    if (c == '"') { inStr = true; continue }
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            return s.substring(i, j + 1)
                        }
                    }
                }
            }
            i = s.indexOf('{', i + 1)
        }

        // 4) 전체가 JSON일 가능성
        val trimmed = text.trim()
        if (trimmed.startsWith('{') && trimmed.endsWith('}')) return trimmed

        // 5) 시작 중괄호만 있고 닫히지 않은 경우, 마지막 '}'까지 보정 시도
        val firstBrace = text.indexOf('{')
        if (firstBrace >= 0) {
            val upto = text.lastIndexOf('}')
            if (upto > firstBrace) {
                return text.substring(firstBrace, upto + 1)
            }
        }

        return ""
    }

    private fun cleanPlaceholders(meta: ClothingMetadata): ClothingMetadata {
        fun scrub(s: String): String {
            val v = s.trim()
            return if (v.equals("string", ignoreCase = true) || v == "...") "" else v
        }
        val cleanedTags = meta.tags.filter { it.isNotBlank() && !it.equals("string", true) && it != "..." }
        return meta.copy(
            name = scrub(meta.name),
            color = scrub(meta.color),
            detailType = scrub(meta.detailType),
            pattern = scrub(meta.pattern),
            brand = scrub(meta.brand),
            description = scrub(meta.description),
            tags = cleanedTags
        )
    }

    private fun parseTagged(text: String): ClothingMetadata {
        fun tag(name: String): String {
            val r = "<$name>(.*?)</$name>".toRegex(RegexOption.DOT_MATCHES_ALL)
            return r.find(text)?.groupValues?.get(1)?.trim() ?: ""
        }
        val category = tag("category").ifBlank { "OTHER" }
        val name = tag("name")
        val color = tag("color")
        val detailType = tag("detailType")
        val pattern = tag("pattern")
        val brand = tag("brand")
        val tagsStr = tag("tags")
        val tags = if (tagsStr.isNotBlank()) tagsStr.split(',').map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        val description = tag("description")
        if (name.isBlank() && color.isBlank() && detailType.isBlank() && tags.isEmpty() && description.isBlank()) {
            throw JSONException("No tags found")
        }
        return ClothingMetadata(
            category = category,
            name = name,
            color = color,
            detailType = detailType,
            pattern = pattern,
            brand = brand,
            tags = tags,
            description = description
        )
    }

    // -------- Heuristic Fallback (최후의 안전장치) --------
    private fun buildHeuristicJson(text: String): String {
        val lower = text.lowercase()
        val category = when {
            listOf("hoodie","sweater","t-shirt","tshirt","tee","shirt","blouse","top").any { lower.contains(it) } -> "TOP"
            listOf("jeans","pants","trousers","shorts","skirt","bottom").any { lower.contains(it) } -> "BOTTOM"
            listOf("jacket","coat","outerwear","cardigan","parka","blazer").any { lower.contains(it) } -> "OUTER"
            listOf("shoe","sneaker","sneakers","boots","loafer","heel","sandals").any { lower.contains(it) } -> "SHOES"
            listOf("bag","cap","hat","belt","scarf","gloves","watch","accessory").any { lower.contains(it) } -> "ACCESSORY"
            else -> "OTHER"
        }

        val colors = listOf(
            "black","white","gray","grey","navy","blue","denim","red","green","yellow","beige","brown","ivory","cream","pink","purple","khaki","orange"
        )
        val color = colors.firstOrNull { lower.contains(it) }?.let {
            when (it) {
                "grey" -> "Gray"
                "denim" -> "Denim Blue"
                else -> it.replaceFirstChar { c -> c.titlecase() }
            }
        } ?: ""

        val detailType = when {
            lower.contains("hoodie") -> "Hoodie"
            lower.contains("t-shirt") || lower.contains("tshirt") || lower.contains("tee") -> "T-Shirt"
            lower.contains("shirt") -> "Shirt"
            lower.contains("jeans") -> "Jeans"
            lower.contains("pants") || lower.contains("trousers") -> "Pants"
            lower.contains("skirt") -> "Skirt"
            lower.contains("jacket") -> "Jacket"
            lower.contains("coat") -> "Coat"
            lower.contains("sneakers") || lower.contains("sneaker") -> "Sneakers"
            lower.contains("boots") -> "Boots"
            lower.contains("socks") -> "Socks"
            else -> ""
        }

        val name = when {
            detailType.isNotBlank() && color.isNotBlank() -> "${color} ${detailType}"
            detailType.isNotBlank() -> detailType
            else -> ""
        }

        val tags = mutableListOf<String>()
        if (lower.contains("casual")) tags.add("Casual") else tags.add("Basic")
        if (color.isNotBlank()) tags.add(color)

        val json = JSONObject()
        json.put("category", category)
        json.put("name", name)
        json.put("color", color)
        json.put("detailType", detailType)
        json.put("pattern", if (lower.contains("stripe") || lower.contains("striped")) "Stripe" else "Solid")
        json.put("brand", "")
        json.put("tags", tags)
        json.put("description", text.take(256))
        return json.toString()
    }
    
    /**
     * 모델 리소스 해제 (서버 사용 시 별도 자원 없음)
     */
    fun release() {
        Log.d(TAG, "No local JNI resources to release (server mode)")
    }

    /**
     * 워드로브 탭 진입 시 모델을 미리 로딩(웜업)하여 응답 시간을 단축
     */
    suspend fun warmup() {
        if (warmedOnce) return
        withContext(Dispatchers.IO) {
            runCatching {
                val modelManager = ModelManager.getInstance(context)
                if (!modelManager.isModelReady()) return@withContext
                val modelPath = modelManager.getModelPath() ?: return@withContext
                val mmprojPath = modelManager.getMmprojPath()
                val ok = LlamaServerController(context).ensureRunning(modelPath, mmprojPath)
                if (ok) {
                    // Vision 파이프라인까지 예열: 매우 작은 투명 PNG로 1토큰만 생성
                    val tiny = tinyWarmupPng()
                    runCatching {
                        EmbeddedLlamaServer.nativeAnalyze(
                            systemPrompt = "Output only {}",
                            userText = "Warmup",
                            imagePng = tiny,
                            temperature = 0.0,
                            maxTokens = 1
                        )
                    }
                    warmedOnce = true
                }
            }.onFailure { e -> Log.w(TAG, "warmup failed: ${e.message}") }
        }
    }

    private fun tinyWarmupPng(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0x00000000)
        val os = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        return os.toByteArray()
    }
    
    /**
     * 빠른 검증용: 모델이 사용 가능한지 확인
     */
    suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelManager = ModelManager.getInstance(context)
                modelManager.isModelReady()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking availability", e)
                false
            }
        }
    }
}
