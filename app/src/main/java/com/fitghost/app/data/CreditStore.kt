package com.fitghost.app.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import java.util.Calendar

/**
 * 주 10회 무료 + 리워드 광고 시 +1
 * DataStore(keys: week, used, bonus)
 */
class CreditStore(private val context: Context) { // BuildConfig.UNLIMITED_CREDITS respected
    companion object {
        private val Context.dataStore by preferencesDataStore(name = "credit_store")
        private val KEY_WEEK = intPreferencesKey("week")
        private val KEY_USED = intPreferencesKey("used")
        private val KEY_BONUS = intPreferencesKey("bonus")
        private const val WEEKLY_FREE = 10
    }

    private fun currentWeek(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.WEEK_OF_YEAR)
    }

    val creditFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) Int.MAX_VALUE else {
            val week = prefs[KEY_WEEK] ?: 0
            val used = prefs[KEY_USED] ?: 0
            val bonus = prefs[KEY_BONUS] ?: 0
            val base = if (week == currentWeek()) WEEKLY_FREE else WEEKLY_FREE
            base + bonus - if (week == currentWeek()) used else 0
        }
    }

    suspend fun consumeOne(): Boolean {
        if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) return true
        var success = false
        context.dataStore.edit { prefs ->
            val week = prefs[KEY_WEEK] ?: currentWeek()
            val used = prefs[KEY_USED] ?: 0
            val bonus = prefs[KEY_BONUS] ?: 0
            val base = if (week == currentWeek()) WEEKLY_FREE else WEEKLY_FREE
            val remain = base + bonus - if (week == currentWeek()) used else 0
            if (remain > 0) {
                prefs[KEY_WEEK] = currentWeek()
                prefs[KEY_USED] = if (week == currentWeek()) used + 1 else 1
                success = true
            } else {
                success = false
            }
        }
        return success
    }

    suspend fun addBonusOne() {
        if (com.fitghost.app.BuildConfig.UNLIMITED_CREDITS) return
        context.dataStore.edit { prefs ->
            val week = prefs[KEY_WEEK] ?: currentWeek()
            val bonus = prefs[KEY_BONUS] ?: 0
            if (week != currentWeek()) {
                prefs[KEY_WEEK] = currentWeek()
                prefs[KEY_USED] = 0
                prefs[KEY_BONUS] = 1
            } else {
                prefs[KEY_BONUS] = bonus + 1
            }
        }
    }
}
