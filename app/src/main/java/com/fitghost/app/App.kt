package com.fitghost.app

import android.app.Application
import com.fitghost.app.data.local.AppDatabase
import com.fitghost.app.data.repository.CartRepositoryProvider

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
    }
}
