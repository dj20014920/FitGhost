package com.fitghost.app

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.fitghost.app.ads.ConsentManager
import com.fitghost.app.ads.RewardedAdController

/**
 * 앱 초기화: UMP 동의 플로우 + MobileAds 초기화/리워드 컨트롤러 프리로드
 */
class App : Application(), Application.ActivityLifecycleCallbacks {
    lateinit var rewardedAdController: RewardedAdController
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        rewardedAdController = RewardedAdController()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // 첫 액티비티 생성 시 UMP → Ads 초기화
        ConsentManager.requestConsent(activity) {
            rewardedAdController.initialize(activity)
        }
    }

    // 나머지 콜백은 미사용
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}