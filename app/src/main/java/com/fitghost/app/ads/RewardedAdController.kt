package com.fitghost.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * 리워드 광고 로딩/표시 컨트롤러 (테스트 단위)
 * - 테스트 보상형 광고 단위 ID: ca-app-pub-3940256099942544/5224354917
 * - 완전한 FullScreenContentCallback 구현
 * - 안전한 상태 관리 및 에러 처리
 */
class RewardedAdController {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var isShowing = false

    companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val TAG = "RewardedAdController"
    }

    /**
     * AdMob SDK 초기화 및 첫 광고 로드
     */
    fun initialize(activity: Activity, onInitialized: (() -> Unit)? = null) {
        MobileAds.initialize(activity) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
            loadAd(activity, onLoaded = { onInitialized?.invoke() })
        }
    }

    /**
     * 리워드 광고 로드
     */
    fun loadAd(
        activity: Activity, 
        onLoaded: (() -> Unit)? = null, 
        onFailed: ((LoadAdError) -> Unit)? = null
    ) {
        if (isLoading || rewardedAd != null) {
            Log.d(TAG, "Ad already loading or loaded")
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            activity,
            TEST_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to load rewarded ad: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                    onFailed?.invoke(error)
                }
            }
        )
    }

    /**
     * 리워드 광고 표시
     */
    fun showAd(
        activity: Activity,
        onReward: (RewardItem) -> Unit,
        onAdClosed: (() -> Unit)? = null,
        onAdFailed: ((AdError) -> Unit)? = null
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad is not ready")
            onAdFailed?.invoke(AdError(0, "Ad not loaded", ""))
            return
        }

        if (isShowing) {
            Log.w(TAG, "Ad is already showing")
            return
        }

        // FullScreenContentCallback 설정
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                isShowing = false
                rewardedAd = null
                onAdClosed?.invoke()
                // 다음 광고를 위해 미리 로드
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Failed to show rewarded ad: ${adError.message}")
                isShowing = false
                rewardedAd = null
                onAdFailed?.invoke(adError)
                // 실패 시에도 다음 광고 로드
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed fullscreen content")
                isShowing = true
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad recorded an impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad was clicked")
            }
        }

        // 광고 표시 및 리워드 처리
        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onReward(rewardItem)
        }
    }

    /**
     * 광고 준비 상태 확인
     */
    fun isAdReady(): Boolean = rewardedAd != null && !isShowing

    /**
     * 로딩 상태 확인
     */
    fun isAdLoading(): Boolean = isLoading

    /**
     * 광고 표시 상태 확인
     */
    fun isAdShowing(): Boolean = isShowing

    /**
     * 리소스 정리
     */
    fun destroy() {
        rewardedAd = null
        isLoading = false
        isShowing = false
    }
}