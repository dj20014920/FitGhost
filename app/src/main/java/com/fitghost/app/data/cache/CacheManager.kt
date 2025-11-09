package com.fitghost.app.data.cache

import android.content.Context
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * API 응답 캐싱 관리자
 * 
 * 기능:
 * - 메모리 캐시 (LruCache): 빠른 접근
 * - 영구 캐시 (DataStore): 앱 재시작 후에도 유지
 * - TTL (Time-To-Live): 24시간 캐시 만료
 * - 해시 기반 키 생성: 동일한 요청은 동일한 응답 반환
 */
class CacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CacheManager"
        
        // 캐시 TTL: 24시간
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
        
        // 메모리 캐시 크기: 10MB
        private const val MEMORY_CACHE_SIZE = 10 * 1024 * 1024
        
        @Volatile
        private var instance: CacheManager? = null
        
        fun getInstance(context: Context): CacheManager {
            return instance ?: synchronized(this) {
                instance ?: CacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // DataStore
    private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "api_cache_store"
    )
    
    // 메모리 캐시 (LruCache)
    private val memoryCache = object : LruCache<String, CacheEntry>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, value: CacheEntry): Int {
            return value.data.length + value.timestamp.toString().length
        }
    }
    
    private val mutex = Mutex()
    
    /**
     * 캐시 엔트리
     */
    private data class CacheEntry(
        val data: String,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS
        }
        
        fun toJson(): String {
            return JSONObject()
                .put("data", data)
                .put("timestamp", timestamp)
                .toString()
        }
        
        companion object {
            fun fromJson(json: String): CacheEntry? {
                return try {
                    val obj = JSONObject(json)
                    CacheEntry(
                        data = obj.getString("data"),
                        timestamp = obj.getLong("timestamp")
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * 파라미터로부터 해시 키 생성
     */
    fun generateKey(vararg params: Any?): String {
        val combined = params.joinToString(separator = "|") { it?.toString() ?: "null" }
        return md5(combined)
    }
    
    /**
     * MD5 해시 생성
     */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 캐시에서 데이터 가져오기
     * 
     * @param key 캐시 키
     * @return 캐시된 데이터 또는 null
     */
    suspend fun get(key: String): String? = mutex.withLock {
        // 1. 메모리 캐시 확인
        val memEntry = memoryCache.get(key)
        if (memEntry != null && !memEntry.isExpired()) {
            android.util.Log.d(TAG, "Memory cache HIT: $key")
            return memEntry.data
        }
        
        // 2. 영구 캐시 확인
        val prefs = context.cacheDataStore.data.first()
        val prefKey = stringPreferencesKey(key)
        val cached = prefs[prefKey]
        
        if (cached != null) {
            val entry = CacheEntry.fromJson(cached)
            if (entry != null && !entry.isExpired()) {
                android.util.Log.d(TAG, "Disk cache HIT: $key")
                // 메모리 캐시에도 저장
                memoryCache.put(key, entry)
                return entry.data
            } else {
                // 만료된 캐시 삭제
                android.util.Log.d(TAG, "Cache EXPIRED: $key")
                context.cacheDataStore.edit { it.remove(prefKey) }
            }
        }
        
        android.util.Log.d(TAG, "Cache MISS: $key")
        return null
    }
    
    /**
     * 캐시에 데이터 저장
     * 
     * @param key 캐시 키
     * @param data 저장할 데이터
     */
    suspend fun put(key: String, data: String) = mutex.withLock {
        val entry = CacheEntry(data, System.currentTimeMillis())
        
        // 1. 메모리 캐시에 저장
        memoryCache.put(key, entry)
        
        // 2. 영구 캐시에 저장
        val prefKey = stringPreferencesKey(key)
        context.cacheDataStore.edit { prefs ->
            prefs[prefKey] = entry.toJson()
        }
        
        android.util.Log.d(TAG, "Cache SAVED: $key")
    }
    
    /**
     * 특정 캐시 삭제
     */
    suspend fun remove(key: String) = mutex.withLock {
        memoryCache.remove(key)
        val prefKey = stringPreferencesKey(key)
        context.cacheDataStore.edit { it.remove(prefKey) }
        android.util.Log.d(TAG, "Cache REMOVED: $key")
    }
    
    /**
     * 모든 캐시 삭제
     */
    suspend fun clear() = mutex.withLock {
        memoryCache.evictAll()
        context.cacheDataStore.edit { it.clear() }
        android.util.Log.d(TAG, "All cache CLEARED")
    }
    
    /**
     * 만료된 캐시 정리
     */
    suspend fun cleanExpired() = mutex.withLock {
        val prefs = context.cacheDataStore.data.first()
        val keysToRemove = mutableListOf<Preferences.Key<String>>()
        
        prefs.asMap().forEach { (key, value) ->
            if (value is String) {
                val entry = CacheEntry.fromJson(value)
                if (entry == null || entry.isExpired()) {
                    @Suppress("UNCHECKED_CAST")
                    keysToRemove.add(key as Preferences.Key<String>)
                }
            }
        }
        
        if (keysToRemove.isNotEmpty()) {
            context.cacheDataStore.edit { prefs ->
                keysToRemove.forEach { prefs.remove(it) }
            }
            android.util.Log.d(TAG, "Cleaned ${keysToRemove.size} expired cache entries")
        }
    }
}
