package com.fitghost.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.fitghost.app.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Manages user credits for virtual fitting (NanoBanana).
 * Enforces:
 * 1. 10 Regular Credits per week (Reset Monday 00:00 KST).
 * 2. Extra Credits (from Ads) do not expire.
 * 3. Regular credits are consumed first.
 * 4. Time check via Network (NTP/Server) to prevent local manipulation.
 */
class CreditRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_credits", Context.MODE_PRIVATE)

    private val _regularCredits = MutableStateFlow(prefs.getInt(KEY_REGULAR, 10))
    val regularCredits: StateFlow<Int> = _regularCredits.asStateFlow()

    private val _extraCredits = MutableStateFlow(prefs.getInt(KEY_EXTRA, 0))
    val extraCredits: StateFlow<Int> = _extraCredits.asStateFlow()

    companion object {
        private const val KEY_REGULAR = "regular_credits"
        private const val KEY_EXTRA = "extra_credits"
        private const val KEY_LAST_RESET = "last_reset_time"
        private const val KEY_LAST_CONSUMED_TYPE = "last_consumed_type" // "regular" or "extra"
        private const val MAX_REGULAR = 10
        
        private const val TYPE_REGULAR = "regular"
        private const val TYPE_EXTRA = "extra"
    }

    // Thread-safety lock for credit operations
    private val creditLock = Any()
    
    // Track if network time verification failed (offline mode detection)
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    /**
     * Checks if it's a new week and resets regular credits if needed.
     * Must be called on app start or before usage.
     * Throws exception if network time cannot be fetched (enforcing online check).
     */
    suspend fun refreshCredits() {
        try {
            val networkTime = TimeProvider.getNetworkTime()
            _isOfflineMode.value = false // Successfully got network time
            
            val currentZoned = Instant.ofEpochMilli(networkTime).atZone(ZoneId.of("Asia/Seoul"))

            // Calculate the start of the current week (Monday 00:00:00 KST)
            var lastMonday = currentZoned.withHour(0).withMinute(0).withSecond(0).withNano(0)
            while (lastMonday.dayOfWeek != DayOfWeek.MONDAY) {
                lastMonday = lastMonday.minusDays(1)
            }

            val lastResetTime = prefs.getLong(KEY_LAST_RESET, 0)
            val lastMondayMillis = lastMonday.toInstant().toEpochMilli()

            // If the calculated Monday is newer than the stored reset time, it means we entered a new week.
            if (lastMondayMillis > lastResetTime) {
                synchronized(creditLock) {
                    // Reset Regular Credits to 10 (Fixed, not added)
                    prefs.edit()
                        .putInt(KEY_REGULAR, MAX_REGULAR)
                        .putLong(KEY_LAST_RESET, lastMondayMillis)
                        .apply()
                    _regularCredits.value = MAX_REGULAR
                }
            }
        } catch (e: Exception) {
            // Network time verification failed - enter offline protection mode
            _isOfflineMode.value = true
            e.printStackTrace()
        }
        
        // Ensure state flows are in sync with prefs
        synchronized(creditLock) {
            _regularCredits.value = prefs.getInt(KEY_REGULAR, 10)
            _extraCredits.value = prefs.getInt(KEY_EXTRA, 0)
        }
    }

    /**
     * Consumes 1 credit with thread-safety.
     * Priority: Regular -> Extra.
     * Returns true if successful, false if no credits.
     * Tracks which type was consumed for proper refund.
     */
    fun consumeCredit(): Boolean = synchronized(creditLock) {
        val regular = prefs.getInt(KEY_REGULAR, 10)
        val extra = prefs.getInt(KEY_EXTRA, 0)

        if (regular > 0) {
            val newRegular = regular - 1
            prefs.edit()
                .putInt(KEY_REGULAR, newRegular)
                .putString(KEY_LAST_CONSUMED_TYPE, TYPE_REGULAR)
                .apply()
            _regularCredits.value = newRegular
            return@synchronized true
        } else if (extra > 0) {
            val newExtra = extra - 1
            prefs.edit()
                .putInt(KEY_EXTRA, newExtra)
                .putString(KEY_LAST_CONSUMED_TYPE, TYPE_EXTRA)
                .apply()
            _extraCredits.value = newExtra
            return@synchronized true
        }
        return@synchronized false
    }
    
    /**
     * Refunds the last consumed credit (for transaction failures).
     * Uses the tracked type to ensure correct refund pool.
     */
    fun refundLastCredit() = synchronized(creditLock) {
        val lastType = prefs.getString(KEY_LAST_CONSUMED_TYPE, null)
        when (lastType) {
            TYPE_REGULAR -> {
                val current = prefs.getInt(KEY_REGULAR, 10)
                val newAmount = (current + 1).coerceAtMost(MAX_REGULAR) // Cap at MAX
                prefs.edit().putInt(KEY_REGULAR, newAmount).apply()
                _regularCredits.value = newAmount
            }
            TYPE_EXTRA -> {
                val current = prefs.getInt(KEY_EXTRA, 0)
                val newAmount = current + 1
                prefs.edit().putInt(KEY_EXTRA, newAmount).apply()
                _extraCredits.value = newAmount
            }
        }
        // Clear the tracking
        prefs.edit().remove(KEY_LAST_CONSUMED_TYPE).apply()
    }

    /**
     * Adds extra credits (e.g. from watching Ads).
     * These do not expire on Monday.
     */
    fun addExtraCredit(amount: Int) = synchronized(creditLock) {
        val current = prefs.getInt(KEY_EXTRA, 0)
        val newAmount = current + amount
        prefs.edit().putInt(KEY_EXTRA, newAmount).apply()
        _extraCredits.value = newAmount
    }
    
    fun getTotalCredits(): Int = synchronized(creditLock) {
        return prefs.getInt(KEY_REGULAR, 10) + prefs.getInt(KEY_EXTRA, 0)
    }
}

object CreditRepositoryProvider {
    private var instance: CreditRepository? = null

    fun initialize(context: Context) {
        if (instance == null) {
            instance = CreditRepository(context)
        }
    }

    fun get(): CreditRepository {
        return instance ?: throw IllegalStateException("CreditRepository not initialized")
    }
}
