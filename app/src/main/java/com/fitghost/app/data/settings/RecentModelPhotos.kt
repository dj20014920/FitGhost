package com.fitghost.app.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 최근 사용한 모델 사진 관리
 * - 최대 3개까지 저장
 * - 새로운 사진 선택 시 맨 앞으로 이동
 * - DataStore 기반 영속성 저장
 * - 이미지를 앱 내부 저장소에 복사하여 URI 권한 문제 해결
 */
object RecentModelPhotos {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_model_photos")
    private val RECENT_PHOTOS_KEY = stringPreferencesKey("recent_photos")
    private const val MAX_RECENT_PHOTOS = 3
    private const val DELIMITER = "|||"
    private const val TAG = "RecentModelPhotos"

    /**
     * 최근 사진 목록 가져오기
     * - 파일이 존재하는 것만 반환
     */
    fun getRecentPhotos(context: Context): Flow<List<Uri>> {
        return context.dataStore.data.map { preferences ->
            val photosString = preferences[RECENT_PHOTOS_KEY] ?: ""
            if (photosString.isBlank()) {
                emptyList()
            } else {
                photosString.split(DELIMITER)
                    .filter { it.isNotBlank() }
                    .mapNotNull { path ->
                        val file = File(path)
                        if (file.exists()) {
                            Uri.fromFile(file)
                        } else {
                            Log.w(TAG, "Recent photo file not found: $path")
                            null
                        }
                    }
            }
        }
    }

    /**
     * 새로운 사진 추가
     * - 이미지를 앱 내부 저장소에 복사
     * - 이미 존재하면 맨 앞으로 이동
     * - 최대 3개까지만 유지
     */
    suspend fun addRecentPhoto(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            // 1. 이미지를 앱 내부 저장소에 복사
            val savedFile = copyImageToInternalStorage(context, uri)
            
            // 2. DataStore에 파일 경로 저장
            context.dataStore.edit { preferences ->
                val currentPhotos = preferences[RECENT_PHOTOS_KEY]
                    ?.split(DELIMITER)
                    ?.filter { it.isNotBlank() }
                    ?.toMutableList()
                    ?: mutableListOf()

                val filePath = savedFile.absolutePath
                
                // 이미 존재하면 제거 (맨 앞으로 이동하기 위해)
                currentPhotos.remove(filePath)
                
                // 맨 앞에 추가
                currentPhotos.add(0, filePath)
                
                // 최대 3개까지만 유지 (오래된 파일 삭제)
                if (currentPhotos.size > MAX_RECENT_PHOTOS) {
                    val toRemove = currentPhotos.drop(MAX_RECENT_PHOTOS)
                    toRemove.forEach { path ->
                        File(path).delete()
                    }
                }
                
                val limitedPhotos = currentPhotos.take(MAX_RECENT_PHOTOS)
                
                // 저장
                preferences[RECENT_PHOTOS_KEY] = limitedPhotos.joinToString(DELIMITER)
            }
            
            Log.d(TAG, "Recent photo added: ${savedFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add recent photo", e)
        }
    }

    /**
     * 이미지를 앱 내부 저장소에 복사
     */
    private fun copyImageToInternalStorage(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "recent_model_photos").apply {
            if (!exists()) mkdirs()
        }
        
        // 타임스탬프 기반 파일명
        val timestamp = System.currentTimeMillis()
        val file = File(dir, "model_$timestamp.jpg")
        
        // 이미지 복사 (압축하여 저장)
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            bitmap.recycle()
        } ?: throw IllegalStateException("Failed to open input stream for URI: $uri")
        
        return file
    }

    /**
     * 모든 최근 사진 삭제
     */
    suspend fun clearRecentPhotos(context: Context) = withContext(Dispatchers.IO) {
        context.dataStore.edit { preferences ->
            // 파일 삭제
            val photosString = preferences[RECENT_PHOTOS_KEY] ?: ""
            if (photosString.isNotBlank()) {
                photosString.split(DELIMITER)
                    .filter { it.isNotBlank() }
                    .forEach { path ->
                        File(path).delete()
                    }
            }
            
            // DataStore에서 제거
            preferences.remove(RECENT_PHOTOS_KEY)
        }
        
        Log.d(TAG, "All recent photos cleared")
    }
}
