package com.fitghost.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 온디바이스 llama.cpp 임베드 엔진 컨트롤러
 * - JNI를 통해 같은 프로세스에서 초기화/헬스체크/정지 수행
 */
class LlamaServerController(private val context: Context) {

    companion object {
        private const val TAG = "LlamaServerCtl"

        @Volatile
        private var INSTANCE: LlamaServerController? = null

        fun getInstance(context: Context): LlamaServerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlamaServerController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val ensureMutex = Mutex()

    /**
     * 서버가 실행 중인지 확인하고, 아니면 시도해서 실행한다.
     * @return true 이면 서버 사용 가능
     */
    suspend fun ensureRunning(modelPath: String, mmprojPath: String?): Boolean = withContext(Dispatchers.IO) {
        ensureMutex.withLock {
            if (!com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
                Log.w(TAG, "Embedded Llama is disabled by build flag; skipping ensureRunning")
                return@withLock false
            }

            if (EmbeddedLlamaServer.nativeIsAlive()) {
                Log.v(TAG, "ensureRunning: already alive")
                return@withLock true
            }

            val chatTemplate = if (modelPath.contains("smolvlm", ignoreCase = true)) "smolvlm" else null
            val started = EmbeddedLlamaServer.nativeInit(
                modelPath = modelPath,
                mmprojPath = mmprojPath,
                chatTemplate = chatTemplate,
                ctx = 2048,
                nThreads = 6
            )
            if (!started) {
                Log.e(TAG, "nativeInit failed")
                return@withLock false
            }

            repeat(20) {
                if (EmbeddedLlamaServer.nativeIsAlive()) {
                    Log.i(TAG, "Embedded llama server ready")
                    return@withLock true
                }
                delay(500)
            }
            Log.e(TAG, "llama-server did not become healthy in time")
            false
        }
    }

    fun isHealthy(): Boolean {
        return com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA && EmbeddedLlamaServer.nativeIsAlive()
    }

    suspend fun generateJson(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        require(com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
            "Embedded Llama disabled by build flag"
        }
        check(isHealthy()) { "Embedded llama server is not running" }
        EmbeddedLlamaServer.nativeAnalyze(
            systemPrompt,
            userPrompt,
            byteArrayOf(),
            temperature.toDouble(),
            maxTokens
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        ensureMutex.withLock {
            EmbeddedLlamaServer.nativeStop()
        }
    }
}
