package com.fitghost.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 주 10회 무료 + 리워드 광고 시 +1
 *
 * DataStore 키:
 * - weekId: "YYYY-Www" 형식의 주차 식별자 (연도 경계 버그 수정)
 * - used: 현재 주차 사용 횟수
 * - bonus: 광고 시청으로 획득한 보너스 크레딧
 */
class CreditStore(private val context: Context) {
    companion object {
        private val Context.dataStore by preferencesDataStore(name = "credit_store")
        private val KEY_WEEK_ID = stringPreferencesKey("week_id") // "YYYY-Www" 형식
        private val KEY_USED = intPreferencesKey("used")
        private val KEY_BONUS = intPreferencesKey("bonus")
        private const val WEEKLY_FREE = 10
    }

    /**
     * 현재 주차 식별자 생성 (연도 포함)
     *
     * 형식: "YYYY-Www" (예: "2025-W01", "2024-W52") 이를 통해 연도 경계에서 주차가 리셋되는 버그 방지
     */
    private fun currentWeekId(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        return String.format("%04d-W%02d", year, week)
    }

    /**
     * 사용 가능한 크레딧 Flow
     *
     * 디버그 빌드에서 UNLIMITED_CREDITS=true면 무제한 반환
     */
    val creditFlow: Flow<Int> =
            context.dataStore.data.map { prefs ->
                if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) {
                    Int.MAX_VALUE
                } else {
                    val storedWeekId = prefs[KEY_WEEK_ID] ?: ""
                    val currentWeekId = currentWeekId()
                    val used = prefs[KEY_USED] ?: 0
                    val bonus = prefs[KEY_BONUS] ?: 0

                    // 현재 주차가 아니면 사용량 리셋 (새로운 주차)
                    val actualUsed = if (storedWeekId == currentWeekId) used else 0

                    // 총 크레딧 = 주 10회 기본 + 보너스 - 사용량
                    WEEKLY_FREE + bonus - actualUsed
                }
            }

    /**
     * 크레딧 1회 소비
     *
     * @return 성공 여부 (크레딧이 부족하면 false)
     */
    suspend fun consumeOne(): Boolean {
        if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) return true

        var success = false
        context.dataStore.edit { prefs ->
            val storedWeekId = prefs[KEY_WEEK_ID] ?: ""
            val currentWeekId = currentWeekId()
            val used = prefs[KEY_USED] ?: 0
            val bonus = prefs[KEY_BONUS] ?: 0

            // 주차가 변경되었으면 사용량 리셋
            val actualUsed = if (storedWeekId == currentWeekId) used else 0

            // 남은 크레딧 계산
            val remaining = WEEKLY_FREE + bonus - actualUsed

            if (remaining > 0) {
                // 크레딧 차감
                prefs[KEY_WEEK_ID] = currentWeekId
                prefs[KEY_USED] = actualUsed + 1
                success = true
            } else {
                // 크레딧 부족
                success = false
            }
        }
        return success
    }

    /** 리워드 광고 시청으로 보너스 크레딧 1회 추가 */
    suspend fun addBonusOne() {
        if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) return

        context.dataStore.edit { prefs ->
            val storedWeekId = prefs[KEY_WEEK_ID] ?: ""
            val currentWeekId = currentWeekId()
            val bonus = prefs[KEY_BONUS] ?: 0

            // 주차가 변경되었으면 사용량과 보너스 리셋
            if (storedWeekId != currentWeekId) {
                prefs[KEY_WEEK_ID] = currentWeekId
                prefs[KEY_USED] = 0
                prefs[KEY_BONUS] = 1 // 새 주차 첫 보너스
            } else {
                prefs[KEY_BONUS] = bonus + 1 // 기존 보너스에 추가
            }
        }
    }
}
