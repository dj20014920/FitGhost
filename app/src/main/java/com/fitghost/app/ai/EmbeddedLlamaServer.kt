package com.fitghost.app.ai

object EmbeddedLlamaServer {
    init {
        // CMake로 빌드된 JNI 라이브러리명 (libembedded_server_jni.so)
        // 빌드 플래그로 온디바이스 엔진이 비활성화된 경우 로드하지 않음
        if (com.fitghost.app.BuildConfig.ENABLE_EMBEDDED_LLAMA) {
            System.loadLibrary("embedded_server_jni")
        } else {
            throw UnsatisfiedLinkError("Embedded Llama disabled at build time (ENABLE_EMBEDDED_LLAMA=false)")
        }
    }

    @JvmStatic external fun nativeInit(
        modelPath: String,
        mmprojPath: String?,
        chatTemplate: String?,
        ctx: Int,
        nThreads: Int
    ): Boolean

    @JvmStatic external fun nativeStop()

    @JvmStatic external fun nativeAnalyze(
        systemPrompt: String,
        userText: String,
        imagePng: ByteArray,
        temperature: Double,
        maxTokens: Int
    ): String

    @JvmStatic external fun nativeIsAlive(): Boolean
}
