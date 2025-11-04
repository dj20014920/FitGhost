package com.fitghost.app.ai.cloud

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.fitghost.app.BuildConfig
import com.fitghost.app.ai.WardrobeAutoComplete
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Cloud Auto-Tagging via Gemini 2.5 Flash-Lite (PRD 10.1)
 * - 멀티모달 지원: 텍스트, 이미지, 동영상, 오디오, PDF
 * - 입력: 1,048,576 토큰 / 출력: 65,536 토큰
 * - 함수 호출, 구조화된 출력, 캐싱 지원
 * - 지식 단절: 2025년 1월 / 최신 업데이트: 2025년 7월
 * - JSON 스키마 강제 + 실패 시 최대 2회 재시도
 * - Cloudflare 프록시(BuildConfig.PROXY_BASE_URL) 경유 필수
 */
object GeminiTagger {
    private const val TAG = "GeminiTagger"
    // Gemini 2.5 Flash-Lite (안정화 버전)
    private const val MODEL = "gemini-2.5-flash-lite"
    private const val CATEGORY_CONF_THRESHOLD = 0.55
    private const val DETAIL_CONF_THRESHOLD = 0.45

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .readTimeout(java.time.Duration.ofSeconds(60))
        .build()

    /**
     * 이미지 자동 태깅 (PRD 스키마)
     */
    suspend fun tagImage(image: Bitmap): Result<JSONObject> {
        return runCatching { requestOnce(image) }.fold(
            onSuccess = { first ->
                if (isValidSchema(first)) return Result.success(first)
                Log.w(TAG, "Schema invalid on first attempt; retrying")
                val second = requestOnce(image, strict = true)
                if (isValidSchema(second)) Result.success(second)
                else Result.failure(IllegalStateException("Schema invalid after retry"))
            },
            onFailure = { e -> Result.failure(e) }
        )
    }

    /**
     * PRD 스키마 → WardrobeAutoComplete.ClothingMetadata 매핑
     */
    fun toClothingMetadata(json: JSONObject): WardrobeAutoComplete.ClothingMetadata {
        val catTop = json.optString("category_top", "")
        val attributes = json.optJSONObject("attributes")
        val sub = json.optString("category_sub", "")
        val brandRaw = json.optString("brand", "")
        val colorPrimary = attributes?.optString("color_primary", "") ?: ""
        val colorSecondary = attributes?.optString("color_secondary", "") ?: ""
        val patternRaw = attributes?.optString("pattern_basic", "") ?: ""
        val fabricRaw = attributes?.optString("fabric_basic", "") ?: ""
        val confidence = json.optJSONObject("confidence")
        val topConfidence = confidence?.optDouble("top", Double.NaN)?.takeIf { !it.isNaN() }
        val subConfidence = confidence?.optDouble("sub", Double.NaN)?.takeIf { !it.isNaN() }

        var normalizedCategory = normalizeCategory(catTop, topConfidence)
        val normalizedDetail = normalizeDetail(sub, subConfidence)
        // 카테고리가 비어있고 상세가 신뢰 가능한 경우 상세로부터 카테고리 유추(보수적 규칙)
        if (normalizedCategory.isBlank() && normalizedDetail.isNotBlank()) {
            normalizedCategory = inferCategoryFromDetail(normalizedDetail)
        }
        val normalizedColorPrimary = normalizeColor(colorPrimary)
        val normalizedColor = if (normalizedColorPrimary.isNotBlank()) {
            normalizedColorPrimary
        } else {
            normalizeColor(colorSecondary)
        }
        val normalizedPattern = normalizePattern(patternRaw, fabricRaw)
        val displayName = buildDisplayName(normalizedColor, normalizedPattern, normalizedDetail)
        val tags = buildTags(
            normalizedCategory,
            normalizedDetail,
            normalizedColor,
            normalizedPattern,
            colorPrimary,
            patternRaw,
            fabricRaw
        )

        val normalizedBrand = normalizeBrand(brandRaw)
        
        val metadata = WardrobeAutoComplete.ClothingMetadata(
            category = normalizedCategory,
            name = displayName,
            color = normalizedColor,
            detailType = normalizedDetail,
            pattern = normalizedPattern,
            brand = normalizedBrand,
            tags = tags,
            description = ""
        )
        Log.d(
            TAG,
            "Mapped Gemini wardrobe JSON (top='$catTop', sub='$sub', brand='$brandRaw', color='$colorPrimary/$colorSecondary', pattern='$patternRaw', fabric='$fabricRaw', conf=${topConfidence ?: "?"}/${subConfidence ?: "?"}) -> $metadata"
        )
        return metadata
    }

    // ---- internal ----

    private fun requestOnce(image: Bitmap, strict: Boolean = false): JSONObject {
        val png = toPngBase64(image)
        val userPrompt = buildUserPrompt(strict)
        val body = buildRestBody(userPrompt, png)
        val url = resolveEndpoint()
        
        // 디버깅을 위한 상세 로깅
        Log.d(TAG, "=== Gemini API Request ===")
        Log.d(TAG, "Target URL: $url")
        Log.d(TAG, "Model: $MODEL")
        Log.d(TAG, "Proxy Base: ${BuildConfig.PROXY_BASE_URL}")
        Log.d(TAG, "Payload (sanitized): ${sanitizePayloadForLog(body, png.length)}")

        val reqBuilder = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            Log.d(TAG, "Response HTTP ${resp.code}")
            
            if (!resp.isSuccessful) {
                val errorMessage = extractErrorMessage(txt)
                Log.e(TAG, "=== Gemini API Error ===")
                Log.e(TAG, "HTTP Code: ${resp.code}")
                Log.e(TAG, "Error Body: $errorMessage")
                Log.e(TAG, "Requested URL: $url")
                
                // API 키 문제 진단
                if (errorMessage.contains("API key not valid", ignoreCase = true)) {
                    throw IllegalStateException(
                        "Gemini API 키가 유효하지 않습니다. Cloudflare Workers의 GEMINI_API_KEY 시크릿을 확인하세요.\n" +
                        "Google AI Studio (aistudio.google.com)에서 새 API 키를 발급받으세요.\n" +
                        "Error: $errorMessage"
                    )
                }
                
                // 지역 제한 문제 진단
                if (errorMessage.contains("location is not supported", ignoreCase = true)) {
                    throw IllegalStateException(
                        "Gemini API 지역 제한 문제입니다.\n" +
                        "1. Google AI Studio에서 발급받은 API 키인지 확인\n" +
                        "2. API 키에 올바른 권한이 있는지 확인\n" +
                        "3. Cloudflare Workers에 올바른 키가 설정되어 있는지 확인\n" +
                        "Error: $errorMessage"
                    )
                }
                
                throw IllegalStateException("Gemini 태깅 실패 (${resp.code}): $errorMessage")
            }
            
            val parsed = extractJsonObject(txt)
            Log.d(TAG, "Response JSON: $parsed")
            return parsed
        }
    }

    private fun buildUserPrompt(strict: Boolean): String {
        val base = """
            당신은 전문 의류 데이터 라벨러입니다. 목표는 의류 이미지 1장을 보고
            아래 스키마에 맞춘 단 하나의 JSON만을 생성하는 것입니다.
            - 출력은 오직 JSON만: 코드펜스/설명/주석 금지
            - 각 필드는 허용된 선택지 안에서만 값 선정
            - 불확실하면 해당 필드는 비우거나(null 허용 필드만 null) 기본 규칙 적용

            분류 원칙(중요):
            - category_top: 상의|하의|아우터|기타 중 선택
            - category_sub: 한국어 소분류(예: 티셔츠, 셔츠, 후드티, 스웨터, 가디건, 블레이저, 자켓, 코트, 청바지, 슬랙스, 스커트, 원피스, 스니커즈, 구두, 부츠)
            - brand: 브랜드명 (이미지에서 로고나 브랜드 텍스트가 명확히 보이는 경우만 채움, 불확실하면 null)
            - attributes.color_primary: 대표 색상(예: black/white/gray/navy/blue/brown/beige/red/green/yellow/pink/purple/ivory/cream/khaki/orange)
            - attributes.color_secondary: 보조 색상 또는 null
            - attributes.pattern_basic: 패턴 ONLY → solid|stripe|check|dot|graphic|null
              (주의: knit/wool/cotton/leather/denim 등 소재를 여기에 넣지 말 것)
            - attributes.fabric_basic: 소재 ONLY → cotton|denim|leather|knit|silk|wool|linen|null
            - 패턴/소재를 혼동 금지. 무지이면 pattern_basic=solid, fabric_basic은 실제 원단 추정 시만 채움

            스키마:
            {
              "image_id": "uuid",
              "category_top": "상의|하의|아우터|기타",
              "category_sub": "티셔츠|셔츠|청바지|스커트|…",
              "brand": "Nike|Adidas|Zara|…|null",
              "attributes": {
                "color_primary": "red|white|black|…",
                "color_secondary": "white|null",
                "pattern_basic": "solid|stripe|check|dot|graphic|null",
                "fabric_basic": "cotton|denim|leather|knit|silk|null"
              },
              "confidence": { "top": 0.93, "sub": 0.86 }
            }
        """.trimIndent()
        return if (!strict) base else base + "\nJSON만. 코드펜스/설명 금지."
    }

    private fun buildRestBody(userText: String, pngB64: String): JSONObject {
        val parts = org.json.JSONArray()
            .put(org.json.JSONObject().put("text", userText))
            .put(org.json.JSONObject().put("inline_data", org.json.JSONObject()
                .put("mime_type", "image/png").put("data", pngB64)))
        return JSONObject()
            .put("contents", org.json.JSONArray()
                .put(org.json.JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", org.json.JSONObject().put("temperature", 0.1).put("candidateCount", 1))
    }
    
    private fun sanitizePayloadForLog(body: JSONObject, base64Length: Int): JSONObject {
        val clone = JSONObject(body.toString())
        val contents = clone.optJSONArray("contents")
        for (i in 0 until (contents?.length() ?: 0)) {
            val parts = contents!!.optJSONObject(i)?.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val inline = part.optJSONObject("inline_data") ?: continue
                inline.put("data", "<omitted:$base64Length chars>")
            }
        }
        return clone
    }

    private fun toPngBase64(bmp: Bitmap): String {
        val bos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolveEndpoint(): String {
        val proxy = BuildConfig.PROXY_BASE_URL.trim()
        require(proxy.isNotBlank()) {
            "PROXY_BASE_URL이 설정되어 있지 않습니다. Cloudflare 프록시 통해서만 태깅이 가능합니다."
        }
        return proxy.trimEnd('/') + "/proxy/gemini/tag"
    }

    private fun extractJsonObject(response: String): JSONObject {
        // candidates[0].content.parts[*].text 에 JSON이 들어오는 형태를 우선 파싱
        return runCatching {
            val root = JSONObject(response)
            val cand = root.optJSONArray("candidates")?.optJSONObject(0)
            val content = cand?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            for (i in 0 until (parts?.length() ?: 0)) {
                val t = parts!!.optJSONObject(i)?.optString("text").orEmpty()
                val json = extractFirstJson(t)
                if (json != null) return JSONObject(json)
            }
            // 직접 텍스트가 최상위일 수 있으므로 전체에서 재탐색
            val json = extractFirstJson(response) ?: error("No JSON found")
            JSONObject(json)
        }.getOrElse { throw it }
    }

    private fun normalizeCategory(raw: String?, confidence: Double?): String {
        val key = raw.orEmpty().trim().lowercase()
        if (key.isBlank()) return ""
        val mapped = when (key) {
            "상의", "top" -> "TOP"
            "하의", "bottom" -> "BOTTOM"
            "아우터", "outer", "outerwear" -> "OUTER"
            "신발", "shoes", "footwear" -> "SHOES"
            "악세서리", "액세서리", "accessory", "accessories" -> "ACCESSORY"
            "기타", "other" -> "OTHER"
            else -> ""
        }
        if (mapped.isBlank()) return ""
        return if (confidence == null || confidence >= CATEGORY_CONF_THRESHOLD) mapped else ""
    }

    private fun normalizeDetail(raw: String?, confidence: Double?): String {
        val key = raw.orEmpty().trim().lowercase()
        if (key.isBlank()) return ""
        var mapped = when {
            key in setOf("tshirt", "t-shirt", "티셔츠", "tee") -> "티셔츠"
            key in setOf("shirt", "셔츠", "button-down", "button up") -> "셔츠"
            key in setOf("hoodie", "후드티", "hooded sweatshirt") -> "후드티"
            key in setOf("sweatshirt", "맨투맨") -> "스웨터" // 드롭다운에 스웨트셔츠가 없으므로 스웨터로 근사
            key in setOf("sweater", "knitwear", "knit", "pullover") -> "스웨터"
            // PRD: 한국어 응답 "가디건"을 신뢰. 과거 오타("카디건")도 수용
            key in setOf("cardigan", "가디건", "카디건") -> "가디건"
            key in setOf("blazer", "블레이저") -> "블레이저"
            key in setOf("jacket", "자켓", "jacket/blazer", "재킷", "jumper", "점퍼") -> "자켓"
            key in setOf("coat", "코트", "outer coat") -> "코트"
            key in setOf("jeans", "denim", "청바지") -> "청바지"
            key in setOf("pants", "슬랙스", "slacks", "trousers") -> "슬랙스"
            key in setOf("skirt", "스커트") -> "스커트"
            key in setOf("dress", "one-piece", "원피스") -> "원피스"
            key in setOf("sneakers", "스니커즈", "운동화") -> "스니커즈"
            key in setOf("boots", "부츠", "ankle boots") -> "부츠"
            key in setOf("heels", "pumps", "loafer", "oxford", "dress shoes", "구두", "로퍼") -> "구두"
            else -> ""
        }
        // 부분 일치 보정(모델이 복합 문자열을 반환하는 경우)
        if (mapped.isBlank()) {
            mapped = when {
                key.contains("cardigan") || key.contains("가디건") || key.contains("카디건") -> "가디건"
                key.contains("blazer") || key.contains("jacket") || key.contains("자켓") || key.contains("재킷") || key.contains("jumper") || key.contains("점퍼") -> "자켓"
                key.contains("hood") || key.contains("후드") -> "후드티"
                key.contains("sweater") || key.contains("knit") || key.contains("맨투맨") -> "스웨터"
                key.contains("tshirt") || key.contains("t-shirt") || key.contains("tee") || key.contains("티셔츠") -> "티셔츠"
                key.contains("shirt") || key.contains("셔츠") -> "셔츠"
                key.contains("jeans") || key.contains("denim") || key.contains("청바지") -> "청바지"
                key.contains("pants") || key.contains("slacks") || key.contains("trousers") || key.contains("슬랙스") -> "슬랙스"
                key.contains("skirt") || key.contains("스커트") -> "스커트"
                key.contains("dress") || key.contains("원피스") -> "원피스"
                key.contains("sneaker") || key.contains("운동화") || key.contains("스니커즈") -> "스니커즈"
                key.contains("boots") || key.contains("부츠") -> "부츠"
                key.contains("loafer") || key.contains("oxford") || key.contains("구두") || key.contains("로퍼") || key.contains("pumps") -> "구두"
                else -> ""
            }
        }
        if (mapped.isBlank()) return ""
        return if (confidence == null || confidence >= DETAIL_CONF_THRESHOLD) mapped else ""
    }

    private fun normalizeBrand(raw: String?): String {
        val key = raw.orEmpty().trim()
        if (key.isBlank() || key.equals("null", ignoreCase = true)) return ""
        // 브랜드명은 대소문자 보존하되 앞뒤 공백만 제거
        return key.take(50) // 최대 50자로 제한
    }

    private fun normalizeColor(raw: String?): String {
        val key = raw.orEmpty().trim()
        if (key.isBlank()) return ""
        val normalized = key.lowercase()
            .replace('-', ' ')
            .replace('_', ' ')
            .replace('/', ' ')
            .replace("  ", " ")
            .trim()

        fun has(vararg tokens: String) = tokens.any { normalized.contains(it) }

        return when {
            has("black", "블랙", "검정") -> "블랙"
            has("white", "화이트", "흰", "하양") -> "화이트"
            // "light gray", "grey", 등 복합 표기 허용
            has("gray", "grey", "그레이", "회색") -> "그레이"
            has("navy", "네이비") -> "네이비"
            has("blue", "블루") -> "블루"
            has("brown", "브라운") -> "브라운"
            has("beige", "베이지") -> "베이지"
            has("red", "레드") -> "레드"
            has("green", "그린") -> "그린"
            has("olive", "올리브") -> "카키"
            has("yellow", "옐로우") -> "옐로우"
            has("pink", "핑크") -> "핑크"
            has("purple", "퍼플") -> "퍼플"
            has("ivory", "아이보리") -> "아이보리"
            has("cream", "크림") -> "크림"
            has("khaki", "카키") -> "카키"
            has("orange", "오렌지") -> "오렌지"
            else -> ""
        }
    }

    private fun normalizePattern(pattern: String?, fabric: String?): String {
        val patternKey = pattern.orEmpty().trim().lowercase()
        val mappedPattern = when {
            patternKey in setOf("solid", "plain", "무지") -> "무지"
            patternKey in setOf("stripe", "striped", "스트라이프") -> "스트라이프"
            patternKey in setOf("check", "plaid", "gingham", "체크") -> "체크"
            patternKey in setOf("dot", "polka", "도트") -> "도트"
            patternKey in setOf("floral", "flower", "플로럴") -> "플로럴"
            patternKey in setOf("geometric", "지오메트릭") -> "지오메트릭"
            patternKey in setOf("animal", "leopard", "tiger", "zebra", "애니멀") -> "애니멀"
            patternKey.isNotBlank() -> "기타 패턴"
            else -> ""
        }
        // 패턴 필드는 패턴만. 소재는 태그로만 보존하고 필드에는 채우지 않음
        return mappedPattern
    }

    private fun buildDisplayName(color: String, pattern: String, detail: String): String {
        val components = listOfNotNull(
            color.takeIf { it.isNotBlank() },
            pattern.takeIf { it.isNotBlank() && it !in setOf("무지", "기타 패턴") },
            detail.takeIf { it.isNotBlank() }
        )
        if (components.isEmpty()) return ""
        return components.joinToString(" ").take(60)
    }

    private fun buildTags(
        category: String,
        detail: String,
        color: String,
        pattern: String,
        rawColor: String,
        rawPattern: String,
        rawFabric: String
    ): List<String> {
        val tags = linkedSetOf<String>()
        if (category.isNotBlank() && category != "OTHER") tags += category
        if (detail.isNotBlank()) tags += detail
        if (color.isNotBlank()) tags += color
        if (pattern.isNotBlank()) tags += pattern
        // 로깅/검색 보조를 위해 원문 값도 보존하되, 중복/공백 제거
        listOf(rawColor, rawPattern, rawFabric)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { tags += it }
        return tags.toList()
    }

    private fun inferCategoryFromDetail(detail: String): String {
        return when (detail) {
            "티셔츠", "셔츠", "후드티", "스웨터" -> "TOP"
            "가디건", "블레이저", "자켓", "코트" -> "OUTER"
            "청바지", "슬랙스", "스커트" -> "BOTTOM"
            "스니커즈", "구두", "부츠" -> "SHOES"
            else -> "" // 보수적으로 미추론
        }
    }

    private fun extractFirstJson(s: String): String? {
        val code = Regex("```json\\s*(.+?)```", setOf(RegexOption.DOT_MATCHES_ALL)).find(s)?.groupValues?.get(1)?.trim()
        if (!code.isNullOrBlank()) return code
        var i = s.indexOf('{')
        while (i >= 0) {
            var depth = 0; var inStr = false; var esc = false
            for (j in i until s.length) {
                val c = s[j]
                if (inStr) { if (esc) { esc = false; continue }; if (c=='\"') inStr=false; else if (c=='\\') esc=true }
                else {
                    if (c=='\"') inStr=true
                    else if (c=='{') depth++
                    else if (c=='}') { depth--; if (depth==0) return s.substring(i, j+1) }
                }
            }
            i = s.indexOf('{', i+1)
        }
        return null
    }

    private fun isValidSchema(obj: JSONObject): Boolean {
        val requiredTop = listOf("image_id","category_top","category_sub","brand","attributes","confidence")
        if (!requiredTop.all { obj.has(it) }) return false
        val attrs = obj.optJSONObject("attributes") ?: return false
        val conf = obj.optJSONObject("confidence") ?: return false
        val attrsKeys = listOf("color_primary","color_secondary","pattern_basic","fabric_basic")
        if (!attrsKeys.all { attrs.has(it) }) return false
        val confKeys = listOf("top","sub")
        if (!confKeys.all { conf.has(it) }) return false
        return true
    }

    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return "응답 본문 없음"
        return runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message").takeIf { !it.isNullOrBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: raw
        }.getOrElse { raw }.take(400)
    }
}
