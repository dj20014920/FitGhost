package com.fitghost.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 온디바이스 llama.cpp 임베드 엔진 컨트롤러
 * - JNI를 통해 같은 프로세스에서 초기화/헬스체크/정지 수행
 */
class LlamaServerController(private val context: Context) {

    companion object { private const val TAG = "LlamaServerCtl" }

    /**
     * 서버가 실행 중인지 확인하고, 아니면 시도해서 실행한다.
     * @return true 이면 서버 사용 가능
     */
    suspend fun ensureRunning(modelPath: String, mmprojPath: String?): Boolean = withContext(Dispatchers.IO) {
        // 빌드 시 네이티브 엔진이 비활성화된 경우 바로 실패 처리하여 크래시 방지
        if (!com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
            Log.w(TAG, "Embedded Llama is disabled by build flag; skipping ensureRunning")
            return@withContext false
        }

        if (EmbeddedLlamaServer.nativeIsAlive()) return@withContext true

        // 외부 프로세스 실행 제거: JNI로 내장 서버 기동
        val chatTemplate = if (modelPath.contains("smolvlm", ignoreCase = true)) "smolvlm" else null
        val started = EmbeddedLlamaServer.nativeInit(
            modelPath = modelPath,
            mmprojPath = mmprojPath,
            chatTemplate = chatTemplate, // SmolVLM의 경우 명시적으로 smolvlm 템플릿 지정
            ctx = 2048, // reduce context for speed, still sufficient for our prompt
            nThreads = 6
        )
        if (!started) return@withContext false

        // 기동 대기 (최대 10초)
        repeat(20) {
            if (EmbeddedLlamaServer.nativeIsAlive()) return@withContext true
            delay(500)
        }
        Log.e(TAG, "llama-server did not become healthy in time")
        false
    }

    suspend fun stop() = withContext(Dispatchers.IO) { EmbeddedLlamaServer.nativeStop() }
}
