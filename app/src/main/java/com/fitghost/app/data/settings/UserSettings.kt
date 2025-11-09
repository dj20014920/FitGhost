package com.fitghost.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/**
 * 사용자 환경설정(성별/온보딩 여부) 영속 저장
 * - DataStore 기반
 * - 전역적으로 성별 기반 추천/검색에 활용
 */
object UserSettings {

    enum class Gender(val tagKo: String) { MALE("남성"), FEMALE("여성"); }

    private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

    private val KEY_GENDER: Preferences.Key<String> = stringPreferencesKey("gender")
    private val KEY_ONBOARDING_DONE: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_completed")

    fun genderFlow(context: Context): Flow<Gender?> =
        context.userSettingsDataStore.data.map { prefs ->
            when (prefs[KEY_GENDER]?.lowercase()) {
                "male" -> Gender.MALE
                "female" -> Gender.FEMALE
                else -> null
            }
        }

    fun onboardingCompletedFlow(context: Context): Flow<Boolean> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[KEY_ONBOARDING_DONE] ?: false
        }

    suspend fun setGender(context: Context, gender: Gender) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[KEY_GENDER] = when (gender) {
                Gender.MALE -> "male"
                Gender.FEMALE -> "female"
            }
        }
    }

    suspend fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = completed
        }
    }

    /**
     * suspend 환경에서 단발적으로 성별 태그(ko)를 얻기 위한 헬퍼
     */
    suspend fun getGenderKoTag(context: Context): String? {
        val g = try { genderFlow(context).firstCompat() } catch (_: Throwable) { null }
        return g?.tagKo
    }
}

// 확장: first를 try/catch로 감싸서 null 허용으로 변환
private suspend fun <T> Flow<T>.firstCompat(): T? = try {
    this.first()
} catch (_: Throwable) { null }
