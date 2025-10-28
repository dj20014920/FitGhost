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
 * Cloud Auto-Tagging via Gemini 2.5 Flash Lite (PRD 10.1)
 * - JSON 스키마 강제 + 실패 시 최대 2회 재시도
 * - Cloudflare 프록시(BuildConfig.PROXY_BASE_URL) 경유 필수, 직결 경로 제거
 */
object GeminiTagger {
    private const val TAG = "GeminiTagger"
    private const val MODEL = "gemini-2.5-flash-lite"

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
        val color = attributes?.optString("color_primary", "") ?: ""
        val sub = json.optString("category_sub", "")
        val pattern = attributes?.optString("pattern_basic", "") ?: ""
        return WardrobeAutoComplete.ClothingMetadata(
            category = when (catTop.lowercase()) {
                "상의","top" -> "TOP"
                "하의","bottom" -> "BOTTOM"
                "아우터","outer" -> "OUTER"
                "신발","shoes" -> "SHOES"
                "악세서리","accessory" -> "ACCESSORY"
                else -> "OTHER"
            },
            name = "",
            color = color,
            detailType = sub,
            pattern = pattern,
            brand = "",
            tags = emptyList(),
            description = ""
        )
    }

    // ---- internal ----

    private fun requestOnce(image: Bitmap, strict: Boolean = false): JSONObject {
        val png = toPngBase64(image)
        val userPrompt = buildUserPrompt(strict)
        val body = buildRestBody(userPrompt, png)
        val url = resolveEndpoint()

        val reqBuilder = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val txt = resp.body?.string() ?: error("Empty body")
            // 응답에서 텍스트를 추출(모델이 JSON-only로 따랐다면 최상위에 JSON)
            return extractJsonObject(txt)
        }
    }

    private fun buildUserPrompt(strict: Boolean): String {
        val base = """
            의류 이미지에 대해 아래 JSON 스키마만 반환하세요. 설명 금지. JSON만.
            스키마:
            {
              "image_id": "uuid",
              "category_top": "상의|하의|아우터|기타",
              "category_sub": "티셔츠|셔츠|청바지|스커트|…",
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
        val requiredTop = listOf("image_id","category_top","category_sub","attributes","confidence")
        if (!requiredTop.all { obj.has(it) }) return false
        val attrs = obj.optJSONObject("attributes") ?: return false
        val conf = obj.optJSONObject("confidence") ?: return false
        val attrsKeys = listOf("color_primary","color_secondary","pattern_basic","fabric_basic")
        if (!attrsKeys.all { attrs.has(it) }) return false
        val confKeys = listOf("top","sub")
        if (!confKeys.all { conf.has(it) }) return false
        return true
    }
}
