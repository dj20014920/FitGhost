package com.fitghost.app

import android.app.Application
import com.fitghost.app.data.local.AppDatabase
import com.fitghost.app.data.repository.CartRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FitGhost 애플리케이션 클래스
 * - 데이터베이스 및 Repository 초기화
 */
class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Room Database 초기화
        val database = AppDatabase.getInstance(this)
        
        // CartRepository 초기화
        CartRepositoryProvider.initialize(database.cartDao())

        // CreditRepository 초기화
        com.fitghost.app.data.repository.CreditRepositoryProvider.initialize(this)

        // AdMob 초기화 (백그라운드)
        CoroutineScope(Dispatchers.IO).launch {
            com.google.android.gms.ads.MobileAds.initialize(this@App) {}
        }

        // Credit Refresh Check (앱 시작 시 리셋 확인)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.fitghost.app.data.repository.CreditRepositoryProvider.get().refreshCredits()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
