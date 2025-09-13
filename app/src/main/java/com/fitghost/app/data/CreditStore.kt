package com.fitghost.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "credit")

/** 주당 무료 10회 + 보너스 크레딧 저장 */
class CreditStore(private val context: Context) {
    companion object Keys {
        val WEEK = stringPreferencesKey("week")
        val USED = intPreferencesKey("used")
        val BONUS = intPreferencesKey("bonus")
    }

    data class State(val week: String, val used: Int, val bonus: Int) {
        val remaining: Int get() = (10 - used).coerceAtLeast(0) + bonus
    }

    fun state(): Flow<State> = context.dataStore.data.map { p ->
        val weekNow = currentWeek()
        val week = p[WEEK] ?: weekNow
        val used = if (week != weekNow) 0 else p[USED] ?: 0
        val bonus = if (week != weekNow) 0 else p[BONUS] ?: 0
        State(weekNow, used, bonus)
    }

    suspend fun consumeOne(): Boolean {
        var success = false
        context.dataStore.edit { p ->
            val s = mapPrefsToState(p)
            if (s.remaining > 0) {
                if (s.used < 10) p[USED] = s.used + 1 else p[BONUS] = (s.bonus - 1).coerceAtLeast(0)
                p[WEEK] = s.week
                success = true
            }
        }
        return success
    }

    suspend fun addBonusOne() {
        context.dataStore.edit { p ->
            val s = mapPrefsToState(p)
            p[BONUS] = s.bonus + 1
            p[WEEK] = s.week
        }
    }

    private fun mapPrefsToState(p: Preferences): State {
        val weekNow = currentWeek()
        val week = p[WEEK] ?: weekNow
        val used = if (week != weekNow) 0 else p[USED] ?: 0
        val bonus = if (week != weekNow) 0 else p[BONUS] ?: 0
        return State(weekNow, used, bonus)
    }

    private fun currentWeek(): String {
        val now = LocalDate.now()
        val wf = WeekFields.of(Locale.getDefault())
        return String.format("%d-W%02d", now.get(wf.weekBasedYear()), now.get(wf.weekOfWeekBasedYear()))
    }
}
