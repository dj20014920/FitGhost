package com.fitghost.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import com.fitghost.app.data.network.GeminiFashionService

/**
 * 온디바이스 AI를 사용하여 특정 아이템과 어울리는 아이템 카테고리를 생성
 * 
 * 예시:
 * 입력: "검은색 청바지"
 * 출력: ["화이트 셔츠", "그레이 니트", "블랙 스니커즈", "브라운 가죽 재킷"]
 */
class MatchingItemsGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "MatchingItemsGen"
        private val SYSTEM_PROMPT = """
You are a Korean fashion coordination expert. Always respond with a single JSON object following this schema:
{
  "matching_items": ["추천1", "추천2", "추천3", "추천4", "추천5"]
}

Rules:
- Provide concrete, searchable Korean outfit item names.
- Avoid duplicating the input item.
- Prioritize seasonally appropriate suggestions if the item description implies seasonality.
- Output ONLY valid JSON. Never include explanations, markdown, or extra text.
""".trimIndent()
        
        // 최대 추천 카테고리 수
        private const val MAX_CATEGORIES = 5
    }
    
    private val llamaController: LlamaServerController by lazy {
        LlamaServerController.getInstance(context)
    }
    private val modelManager: ModelManager by lazy { ModelManager.getInstance(context) }
    private val cloudService: GeminiFashionService by lazy { GeminiFashionService() }
    
    /**
     * 특정 아이템과 어울리는 아이템 카테고리 생성
     * 
     * @param itemDescription 아이템 설명 (예: "검은색 청바지", "화이트 셔츠")
     * @param itemCategory 아이템 카테고리 (예: "하의", "상의")
     * @return 어울리는 아이템 카테고리 리스트
     */
    suspend fun generateMatchingCategories(
        itemDescription: String,
        itemCategory: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating matching categories for: $itemDescription ($itemCategory)")

            val prompt = buildPrompt(itemDescription, itemCategory)

            val onDevice = runCatching {
                generateWithOnDevice(prompt)
            }.getOrElse { error ->
                Log.e(TAG, "On-device generation failed", error)
                emptyList()
            }

            if (onDevice.isNotEmpty()) {
                Log.d(TAG, "On-device categories: $onDevice")
                return@withContext Result.success(onDevice.take(MAX_CATEGORIES))
            }

            val cloud = runCatching {
                generateWithCloud(prompt)
            }.getOrElse { error ->
                Log.e(TAG, "Cloud generation failed", error)
                emptyList()
            }

            if (cloud.isNotEmpty()) {
                Log.d(TAG, "Cloud categories: $cloud")
                return@withContext Result.success(cloud.take(MAX_CATEGORIES))
            }

            Log.w(TAG, "Falling back to heuristics")
            Result.success(getFallbackCategories(itemCategory))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating matching categories", e)
            // 에러 시 폴백 카테고리 반환
            Result.success(getFallbackCategories(itemCategory))
        }
    }
    
    /**
     * AI 프롬프트 생성 (최적화됨)
     * - 간결하고 명확한 지시
     * - JSON 스키마 강제
     * - 실용적인 검색어 생성
     */
    private fun buildPrompt(itemDescription: String, itemCategory: String): String {
        return """You are a fashion stylist. Given clothing item: "$itemDescription" (category: $itemCategory).

Task: Suggest 5 matching items that coordinate well. Format: "color + item name" (e.g., "white shirt").

Rules:
- Different category from input
- Specific, searchable Korean terms
- Consider season & style
- Output ONLY valid JSON

JSON schema:
{
  "matching_items": ["item1", "item2", "item3", "item4", "item5"]
}

Output:""".trimIndent()
    }
    
    /**
     * AI 응답 파싱
     */
    private fun parseResponse(response: String): List<String> {
        return try {
            // JSON 추출 (응답에서 { } 사이의 내용만)
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                Log.w(TAG, "No JSON found in response")
                return emptyList()
            }
            
            val jsonString = response.substring(jsonStart, jsonEnd)
            val jsonObject = JSONObject(jsonString)
            val itemsArray = jsonObject.getJSONArray("matching_items")
            
            val categories = mutableListOf<String>()
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getString(i).trim()
                if (item.isNotBlank()) {
                    categories.add(item)
                }
            }
            
            categories
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            emptyList()
        }
    }
    
    /**
     * 폴백 카테고리 (AI 실패 시)
     * 카테고리별로 일반적으로 잘 어울리는 아이템 반환
     */
    private fun getFallbackCategories(itemCategory: String): List<String> {
        return when (itemCategory.lowercase()) {
            "상의", "top" -> listOf(
                "블랙 청바지",
                "베이지 슬랙스",
                "화이트 스니커즈",
                "브라운 가죽 재킷"
            )
            "하의", "bottom" -> listOf(
                "화이트 셔츠",
                "그레이 니트",
                "블랙 스니커즈",
                "네이비 재킷"
            )
            "아우터", "outerwear" -> listOf(
                "화이트 티셔츠",
                "블랙 청바지",
                "화이트 스니커즈",
                "그레이 머플러"
            )
            "신발", "shoes" -> listOf(
                "블랙 청바지",
                "화이트 티셔츠",
                "네이비 재킷",
                "베이지 백팩"
            )
            else -> listOf(
                "화이트 셔츠",
                "블랙 청바지",
                "화이트 스니커즈",
                "네이비 재킷"
            )
        }
    }

    private suspend fun generateWithOnDevice(prompt: String): List<String> {
        if (!com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
            Log.i(TAG, "Embedded llama disabled; skip on-device path")
            return emptyList()
        }

        val assets = ensureOnDeviceAssets() ?: return emptyList()
        val (modelPath, mmprojPath) = assets
        val started = llamaController.ensureRunning(modelPath, mmprojPath)
        if (!started) return emptyList()

        val response = llamaController.generateJson(
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = prompt,
            temperature = 0.25f,
            maxTokens = 192
        )
        return parseResponse(response)
    }

    private suspend fun ensureOnDeviceAssets(): Pair<String, String?>? {
        return try {
            if (!modelManager.isModelReady()) {
                Log.i(TAG, "On-device model not ready. Triggering download.")
                val download = modelManager.downloadModel()
                if (download.isFailure) {
                    Log.e(TAG, "Model download failed", download.exceptionOrNull())
                    return null
                }
            }
            val modelPath = modelManager.getModelPath()
            if (modelPath.isNullOrBlank()) {
                Log.e(TAG, "Model path missing after download")
                return null
            }
            val modelFile = File(modelPath)
            if (!modelFile.exists() || modelFile.length() <= 0) {
                Log.e(TAG, "Model file invalid: $modelPath")
                return null
            }
            val mmprojPath = modelManager.getMmprojPath()
            modelPath to mmprojPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure on-device assets", e)
            null
        }
    }

    private suspend fun generateWithCloud(prompt: String): List<String> {
        val result = cloudService.generateMatchingCategories(prompt)
        return result.getOrElse {
            Log.e(TAG, "Cloud matching categories failed", it)
            emptyList()
        }
    }
}
