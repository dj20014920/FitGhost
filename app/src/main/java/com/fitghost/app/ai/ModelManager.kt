package com.fitghost.app.ai

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * AI 모델 관리자
 *
 * 책임:
 * - LiquidAI LFM2 모델을 Cloudflare R2/Workers 프록시를 통해 다운로드
 * - 모델 상태 및 경로 관리
 * - DataStore를 통한 영속화
 *
 * 모델 정보:
 * - 이름: LFM2-1.2B-Q4_0.gguf
 * - 크기: 664 MB (663.52 MB)
 * - 출처: CDN(emozleep) + Cloudflare Workers presign
 */
class ModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val DATASTORE_NAME = "ai_model_preferences"
        
        // DataStore
        private val Context.modelDataStore by preferencesDataStore(name = DATASTORE_NAME)
        private val MODEL_STATE_KEY = stringPreferencesKey("model_state")
        private val MODEL_PATH_KEY = stringPreferencesKey("model_path")
        private val MODEL_VERSION_KEY = stringPreferencesKey("model_version")
        
        // Cloudflare R2 설정
        // 주의: R2 퍼블릭 개발 URL은 pub-<account-id>.r2.dev 형태입니다.
        // 버킷명이 URL 경로에 포함되지 않고, 루트에 직접 파일이 위치합니다.
        // 예) https://pub-<account-id>.r2.dev/<file-key>
        // 공용 설정으로 이동: AiConfig.R2_PUBLIC_BASE
        private const val R2_ACCOUNT_HOST = "081a9810680543ee912eb54ae15876a3.r2.cloudflarestorage.com"
        private const val R2_BUCKET = "ghostfit-models"
        private const val R2_PUBLIC_HOST = "r2.dev" // 사용하지 않음 (pub-* 베이스로 대체)
        // R2 오브젝트 키 (버킷/퍼블릭 도메인 내부 경로)
        // 예: <base>/models/LFM2-1.2B-Q4_0.gguf
        private const val MODEL_KEY = "models/LFM2-1.2B-Q4_0.gguf"
        
        // 모델 정보 (LiquidAI LFM2로 전환)
        private const val MODEL_FILENAME = "LFM2-1.2B-Q4_0.gguf"
        private const val CURRENT_MODEL_VERSION = "lfm2-1.2b-q4_0"
        private const val MODEL_SIZE_MB = 664L // 실제: 663.52 MB (695,749,568 bytes)

        // LFM2는 텍스트 모델(기본)로, 멀티모달 projector가 필요하지 않습니다.
        // 필요 시 mmproj 파일명을 지정하여 vision 경로를 활성화하세요(기본 비활성).
        private const val MMPROJ_FILENAME = ""
        private const val MMPROJ_KEY = ""

        
        @Volatile
        private var INSTANCE: ModelManager? = null
        
        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * AI 모델 상태
     */
    enum class ModelState {
        /** 모델이 다운로드되지 않음 */
        NOT_READY,
        
        /** 모델 다운로드 중 */
        DOWNLOADING,
        
        /** 모델 다운로드 완료 및 사용 가능 */
        READY,
        
        /** 다운로드 실패 */
        ERROR
    }
    
    /**
     * 다운로드 진행 상태
     */
    data class DownloadProgress(
        val downloadedMB: Float,
        val totalMB: Float,
        val percentage: Int
    ) {
        val isComplete: Boolean get() = percentage >= 100
    }
    
    /**
     * 현재 모델 상태 관찰
     */
    fun observeModelState(): Flow<ModelState> {
        return context.modelDataStore.data.map { prefs ->
            val stateStr = prefs[MODEL_STATE_KEY] ?: ModelState.NOT_READY.name
            try {
                ModelState.valueOf(stateStr)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid model state: $stateStr, defaulting to NOT_READY")
                ModelState.NOT_READY
            }
        }
    }
    
    /**
     * 현재 모델 상태 가져오기
     */
    suspend fun getModelState(): ModelState {
        val state = observeModelState().first()
        Log.d(TAG, "getModelState() -> $state")
        return state
    }
    
    /**
     * 모델이 준비되었는지 확인
     */
    suspend fun isModelReady(): Boolean {
        if (getModelState() != ModelState.READY) return false
        // 메인 모델 체크
        val mainOk = getModelFile().exists() && getModelFile().length() > 0
        // mmproj 설정이 있을 경우 함께 체크 (없으면 스킵)
        val mmprojConfigured = MMPROJ_KEY.isNotBlank() && MMPROJ_FILENAME.isNotBlank()
        val mmprojOk = if (mmprojConfigured) (getMmprojFile().exists() && getMmprojFile().length() > 0) else true
        return mainOk && mmprojOk
    }
    
    /**
     * 저장된 모델 경로 가져오기
     */
    suspend fun getModelPath(): String? {
        return context.modelDataStore.data.map { prefs ->
            prefs[MODEL_PATH_KEY]
        }.first()
    }

    /**
     * (선택) mmproj 경로 가져오기 (다운로드되어 있을 경우)
     */
    fun getMmprojPath(): String? {
        val f = getMmprojFile()
        return if (f.exists() && f.length() > 0L) f.absolutePath else null
    }
    
    /**
     * R2에서 모델 다운로드
     * 
     * @param onProgress 진행률 콜백
     * @return 성공 여부 및 모델 경로
     */
    suspend fun downloadModel(
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting model download from R2...")
                
                // 상태를 다운로드 중으로 변경
                updateModelState(ModelState.DOWNLOADING)
                
                // 모델 저장 디렉토리 생성
                val modelDir = getModelDirectory()
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }
                
                val modelFile = getModelFile()
                val tempFile = getTempFile()
                val lockFile = getLockFile()
                val mmprojFile = getMmprojFile()

                // 과거 중단 흔적 있으면 정리 (안정적 UX 보장)
                if (tempFile.exists()) tempFile.delete()
                if (lockFile.exists()) lockFile.delete()
                
                // 이미 파일이 있고 크기가 맞다면 메인 모델 재다운로드 스킵 플래그
                var skipMainDownload = false
                if (modelFile.exists()) {
                    val fileSizeMB = modelFile.length() / (1024 * 1024)
                    if (fileSizeMB >= MODEL_SIZE_MB - 10) { // 10MB 오차 허용
                        Log.i(TAG, "Model file already exists and size matches: ${fileSizeMB}MB (skip re-download)")
                        skipMainDownload = true
                    } else {
                        Log.w(TAG, "Existing model file size mismatch: ${fileSizeMB}MB, will re-download")
                        modelFile.delete()
                    }
                }

                // HTTP 클라이언트 - 커스텀 DNS로 에뮬레이터 DNS 문제 해결
                val client = OkHttpClient.Builder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .readTimeout(java.time.Duration.ofMinutes(30))
                    .writeTimeout(java.time.Duration.ofMinutes(30))
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String): List<java.net.InetAddress> {
                            return if (hostname == "cdn.emozleep.space") {
                                // 안드로이드 에뮬레이터 DNS 문제 해결: 직접 IP 반환
                                Log.d(TAG, "Custom DNS: $hostname -> ${AiConfig.CDN_IP_ADDRESS}")
                                listOf(java.net.InetAddress.getByName(AiConfig.CDN_IP_ADDRESS))
                            } else {
                                // 다른 도메인은 시스템 DNS 사용
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    })
                    .addInterceptor { chain ->
                        val request = chain.request()
                        Log.d(TAG, "HTTP Request: ${request.method} ${request.url}")
                        try {
                            val response = chain.proceed(request)
                            Log.d(TAG, "HTTP Response: ${response.code} ${response.message}, Content-Length: ${response.body?.contentLength()}")
                            response
                        } catch (e: Exception) {
                            Log.e(TAG, "HTTP Request failed: ${e.javaClass.simpleName}: ${e.message}", e)
                            throw e
                        }
                    }
                    .build()
                
                if (!skipMainDownload) {
                    val primaryUrl = resolveDownloadUrl(client, MODEL_KEY)
                    val request = Request.Builder()
                        .url(primaryUrl)
                        .build()
                    Log.d(TAG, "Downloading main model from: $primaryUrl")

                    try {
                        client.newCall(request).execute().use { response ->
                        Log.d(TAG, "Response code: ${response.code}, message: ${response.message}")
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                        val body = response.body ?: throw Exception("Empty response body")
                        val totalBytesRaw = body.contentLength()
                        val totalBytes = if (totalBytesRaw > 0L) totalBytesRaw else -1L
                        val totalMB = if (totalBytes > 0) totalBytes / (1024f * 1024f) else -1f

                        var downloadedBytes = 0L
                        val buffer = ByteArray(8192)

                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                var bytes: Int
                                var lastReportedPercentage = -1
                                while (input.read(buffer).also { bytes = it } != -1) {
                                    output.write(buffer, 0, bytes)
                                    downloadedBytes += bytes
                                    val downloadedMB = downloadedBytes / (1024f * 1024f)
                                    val percentage = if (totalBytes > 0) ((downloadedBytes.toFloat() / totalBytes) * 100).toInt() else -1
                                    if (percentage == -1 || percentage > lastReportedPercentage) {
                                        lastReportedPercentage = percentage
                                        withContext(Dispatchers.Main) {
                                            onProgress(DownloadProgress(downloadedMB, totalMB, if (percentage >= 0) percentage else 0))
                                        }
                                    }
                                }
                            }
                        }
                        if (modelFile.exists()) modelFile.delete()
                        val renamed = tempFile.renameTo(modelFile)
                        if (!renamed) {
                            throw Exception("Failed to rename temp file to final model file")
                        }
                        Log.d(TAG, "Model download completed successfully: ${modelFile.absolutePath}")
                    }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download execution failed: ${e.javaClass.simpleName}: ${e.message}", e)
                        throw e
                    }
                } else {
                    // 메인 모델 스킵 시에도 진행률 UI가 멈춘 것처럼 보이지 않도록 100%로 보정
                    withContext(Dispatchers.Main) {
                        onProgress(DownloadProgress(MODEL_SIZE_MB.toFloat(), MODEL_SIZE_MB.toFloat(), 100))
                    }
                }

                // (선택) mmproj 다운로드: 설정이 있는 경우에만
                if (MMPROJ_KEY.isNotBlank() && MMPROJ_FILENAME.isNotBlank()) {
                    val base = com.fitghost.app.BuildConfig.MODEL_BASE_URL.takeIf { it.isNotEmpty() } ?: AiConfig.R2_PUBLIC_BASE
                    val mmUrl = "$base/$MMPROJ_KEY"
                    Log.d(TAG, "Downloading mmproj from: $mmUrl")
                    runCatching {
                        val mmReq = Request.Builder().url(mmUrl).build()
                        client.newCall(mmReq).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                            val body = response.body ?: throw Exception("Empty response body")
                            mmprojFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "mmproj download failed (optional): ${e.message}")
                    }
                }

                // 상태 업데이트 (mmproj가 구성된 경우 필수 확인)
                val mmprojConfigured = MMPROJ_KEY.isNotBlank() && MMPROJ_FILENAME.isNotBlank()
                val mmOkFinal = if (mmprojConfigured) (mmprojFile.exists() && mmprojFile.length() > 0L) else true
                val mainOkFinal = modelFile.exists() && modelFile.length() > 0L

                return@withContext if (mainOkFinal && mmOkFinal) {
                    Log.i(TAG, "Download completed successfully! Setting state to READY")
                    updateModelState(ModelState.READY)
                    updateModelPath(modelFile.absolutePath)
                    updateModelVersion(CURRENT_MODEL_VERSION)
                    Log.i(TAG, "Model state updated: READY, Path: ${modelFile.absolutePath}")
                    Result.success(modelFile.absolutePath)
                } else {
                    Log.e(TAG, "Download failed: Model components missing (mainOk=$mainOkFinal, mmprojOk=$mmOkFinal)")
                    updateModelState(ModelState.ERROR)
                    Result.failure(Exception("Model components missing: mainOk=$mainOkFinal, mmprojOk=$mmOkFinal"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during model download: ${e.javaClass.simpleName}: ${e.message}", e)
                e.printStackTrace()
                updateModelState(ModelState.ERROR)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 모델 초기화 확인
     */
    suspend fun initializeModel(): Result<String> {
        return try {
            val state = getModelState()
            val path = getModelPath()
            
            when (state) {
                ModelState.READY -> {
                    if (path != null && File(path).exists()) {
                        Log.d(TAG, "Model already ready at: $path")
                        Result.success(path)
                    } else {
                        Log.w(TAG, "Model state is READY but file not found, re-download required")
                        Result.failure(Exception("Model download required"))
                    }
                }
                ModelState.NOT_READY, ModelState.ERROR -> {
                    Log.d(TAG, "Model not ready, download required")
                    Result.failure(Exception("Model download required"))
                }
                ModelState.DOWNLOADING -> {
                    Log.d(TAG, "Model download in progress")
                    Result.failure(Exception("Model download in progress"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            Result.failure(e)
        }
    }

    /**
     * 서버 실행 커맨드 추천(로컬 디바이스에서 llama-server 실행 시)
     * - 앱 외부에서 서버를 구동하는 경우에 참고용
     */
    suspend fun getRecommendedServerCommand(): String {
        val model = getModelPath() ?: "<path/to/model.gguf>"
        val mm = getMmprojPath()
        return buildString {
            append("embedded: model=").append(model)
            if (mm != null) append(" mmproj=").append(mm)
            append(" chat-template=smolvlm ctx=8192")
        }
    }
    
    /**
     * 모델 삭제 (사용자 요청)
     * 
     * @return 삭제 성공 여부
     */
    suspend fun deleteModel(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Deleting AI model...")
                
                // 상태를 NOT_READY로 변경
                updateModelState(ModelState.NOT_READY)
                
                // DataStore 정보 삭제
                context.modelDataStore.edit { prefs ->
                    prefs.remove(MODEL_PATH_KEY)
                    prefs.remove(MODEL_VERSION_KEY)
                }
                
                // 다운로드된 파일 삭제
                val modelDir = getModelDirectory()
                if (modelDir.exists()) {
                    val deleted = modelDir.deleteRecursively()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete model directory completely")
                    }
                }
                
                Log.d(TAG, "Model deleted successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting model", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 모델 정보 가져오기
     */
    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = "LiquidAI LFM2",
            version = CURRENT_MODEL_VERSION,
            sizeMB = MODEL_SIZE_MB,
            filename = MODEL_FILENAME
        )
    }
    
    /**
     * 모델 정보 데이터 클래스
     */
    data class ModelInfo(
        val name: String,
        val version: String,
        val sizeMB: Long,
        val filename: String
    )
    
    /**
     * 모델 상태 초기화 (테스트/디버그용)
     */
    suspend fun resetModel() {
        deleteModel()
        Log.d(TAG, "Model reset completed")
    }
    
    // -------- Private Methods --------
    
    /**
     * 모델 상태 업데이트
     */
    private suspend fun updateModelState(state: ModelState) {
        context.modelDataStore.edit { prefs ->
            prefs[MODEL_STATE_KEY] = state.name
        }
    }
    
    /**
     * 모델 경로 업데이트
     */
    private suspend fun updateModelPath(path: String) {
        context.modelDataStore.edit { prefs ->
            prefs[MODEL_PATH_KEY] = path
        }
    }
    
    /**
     * 모델 버전 업데이트
     */
    private suspend fun updateModelVersion(version: String) {
        context.modelDataStore.edit { prefs ->
            prefs[MODEL_VERSION_KEY] = version
        }
    }
    
    /**
     * 모델 저장 디렉토리 가져오기
     */
    private fun getModelDirectory(): File {
        return File(context.filesDir, "ai_models")
    }

    /** 최종 모델 파일 */
    private fun getModelFile(): File = File(getModelDirectory(), MODEL_FILENAME)

    /** (선택) 멀티모달 projector 파일 */
    private fun getMmprojFile(): File = File(getModelDirectory(), MMPROJ_FILENAME)

    /** 부분 다운로드 임시 파일 (.part) */
    private fun getTempFile(): File = File(getModelDirectory(), "$MODEL_FILENAME.part")

    /** 다운로드 중 상태를 나타내는 잠금 파일 */
    private fun getLockFile(): File = File(getModelDirectory(), "$MODEL_FILENAME.lock")

    private fun resolveDownloadUrl(client: OkHttpClient, key: String): String {
        // 도메인 이름 사용 (커스텀 DNS가 IP로 해결)
        val directUrl = AiConfig.R2_PUBLIC_BASE.trimEnd('/') + "/" + key
        Log.d(TAG, "Using CDN URL: $directUrl")
        return directUrl
    }

    /**
     * 데이터스토어의 상태와 실제 파일 상태를 동기화한다.
     * - DOWNLOADING인데 실제로 다운로드 중이 아닐 가능성이 크므로 NOT_READY로 복구하고 임시파일 제거
     * - READY인데 파일이 없거나 불완전하면 NOT_READY로 복구
     */
    suspend fun reconcileState(): ModelState = withContext(Dispatchers.IO) {
        val state = getModelState()
        val modelFile = getModelFile()
        val tempFile = getTempFile()
        val lockFile = getLockFile()
        val mmprojFile = getMmprojFile()

        Log.d(TAG, "reconcileState() - Current state: $state, Model file exists: ${modelFile.exists()}, Size: ${if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0}MB")

        when (state) {
            ModelState.DOWNLOADING -> {
                if (tempFile.exists()) tempFile.delete()
                if (lockFile.exists()) lockFile.delete()
                updateModelState(ModelState.NOT_READY)
                ModelState.NOT_READY
            }
            ModelState.READY -> {
                val mainOk = modelFile.exists() && (modelFile.length() / (1024 * 1024)) >= (MODEL_SIZE_MB - 10)
                val mmprojConfigured = MMPROJ_KEY.isNotBlank() && MMPROJ_FILENAME.isNotBlank()
                val mmOk = if (mmprojConfigured) (mmprojFile.exists() && mmprojFile.length() > 0L) else true
                if (!(mainOk && mmOk)) {
                    // 모델 파일은 보존하고, 미완성 상태이므로 NOT_READY로만 전환
                    if (tempFile.exists()) tempFile.delete()
                    if (lockFile.exists()) lockFile.delete()
                    updateModelState(ModelState.NOT_READY)
                    Log.w(TAG, "Model files incomplete, changed to NOT_READY")
                    ModelState.NOT_READY
                } else {
                    // 파일이 정상이면 READY 상태 유지 (이미 DataStore에 저장되어 있음)
                    Log.d(TAG, "Model files verified, keeping READY state")
                    ModelState.READY
                }
            }
            else -> {
                if (tempFile.exists()) tempFile.delete()
                if (lockFile.exists()) lockFile.delete()
                state
            }
        }
    }
}
