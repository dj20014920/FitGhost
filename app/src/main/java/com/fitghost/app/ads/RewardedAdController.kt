package com.fitghost.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob Rewarded Ad Controller (테스트 유닛 ID)
 * 실제 배포 시 README 안내에 따라 교체할 것.
 */
class RewardedAdController(private val context: Context) {
    private var rewardedAd: RewardedAd? = null

    fun load(onLoaded: (() -> Unit)? = null, onFailed: ((LoadAdError) -> Unit)? = null) {
        val request = AdRequest.Builder().build()
        // 테스트 유닛 ID (Google): ca-app-pub-3940256099942544/5224354917
        RewardedAd.load(context, "ca-app-pub-3940256099942544/5224354917", request, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
                onFailed?.invoke(adError)
            }

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                onLoaded?.invoke()
            }
        })
    }

    fun show(activity: Activity, onReward: (RewardItem) -> Unit, onDismiss: (() -> Unit)? = null) {
        val ad = rewardedAd ?: return
        ad.show(activity) { reward ->
            onReward(reward)
        }
        ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onDismiss?.invoke() }
        }
    }
}
