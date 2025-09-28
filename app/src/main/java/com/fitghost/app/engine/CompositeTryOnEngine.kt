package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.fitghost.app.BuildConfig
import com.fitghost.app.utils.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Try-On 엔진 조합: 설정/환경에 따라 우선 엔진을 선택하고 실패 시 안전 폴백
 * 
 * 엔진 우선순위 (2024.09 업데이트):
 * 1. NanoBananaTryOnEngine (Google Gemini 2.5 Flash Image Preview) - 기본
 * 2. CloudTryOnEngine (Vertex AI Gemini) - CLOUD_TRYON_ENABLED=true인 경우
 * 3. FakeTryOnEngine - 모든 엔진 실패 시 폴백
 *
 * 변경 사항:
 * - NanoBanana가 이제 실제 Google API이므로 신뢰성이 크게 향상됨
 * - Cloud보다 NanoBanana를 우선으로 사용 (Image Preview 모델이 더 적합)
 */
class CompositeTryOnEngine : TryOnEngine {

    private val cloudEnabled = BuildConfig.CLOUD_TRYON_ENABLED && ApiKeyManager.isGeminiVertexApiKeyValid()
    private val nanoApiKeyValid = BuildConfig.NANOBANANA_API_KEY.isNotBlank()

    private val cloud by lazy { CloudTryOnEngine() }
    private val nano by lazy { NanoBananaTryOnEngine() }
    private val fake by lazy { FakeTryOnEngine() }

    override suspend fun renderPreview(
        context: Context,
        modelUri: Uri,
        clothingUris: List<Uri>,
        systemPrompt: String?
    ): Bitmap = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "Starting virtual try-on - nano API valid: $nanoApiKeyValid, cloud enabled: $cloudEnabled")

        // 1차: NanoBanana (Google Gemini 2.5 Flash Image Preview) 우선 시도
        if (nanoApiKeyValid) {
            try {
                Log.d(TAG, "Trying NanoBananaTryOnEngine (Google Gemini 2.5 Flash Image Preview)")
                return@withContext nano.renderPreview(context, modelUri, clothingUris, systemPrompt)
            } catch (e: Exception) {
                Log.w(TAG, "NanoBananaTryOnEngine failed: ${e.message}", e)
            }
        }

        // 2차: Cloud 엔진 시도 (활성화된 경우)
        if (cloudEnabled) {
            try {
                Log.d(TAG, "Trying CloudTryOnEngine (Vertex AI Gemini)")
                return@withContext cloud.renderPreview(context, modelUri, clothingUris, systemPrompt)
            } catch (e: Exception) {
                Log.w(TAG, "CloudTryOnEngine failed: ${e.message}", e)
            }
        }

        // 3차: 최종 폴백 - FakeTryOnEngine
        Log.w(TAG, "All engines failed, falling back to FakeTryOnEngine")
        return@withContext fake.renderPreview(context, modelUri, clothingUris, systemPrompt)
    }

    companion object {
        private const val TAG = "CompositeTryOnEngine"
    }
}
