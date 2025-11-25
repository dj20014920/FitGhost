package com.fitghost.app.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private var rewardedAd: RewardedAd? = null
    // Using the specific Ad Unit ID provided by the user
    private const val AD_UNIT_ID = "ca-app-pub-5319827978116991/3465926696" 
    private const val TAG = "AdManager"

    /**
     * Loads a Rewarded Ad. Should be called early (e.g., in onCreate or after an ad is shown).
     */
    fun loadRewardedAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
            }
        })
    }

    /**
     * Shows the Rewarded Ad if ready.
     * @param activity The current activity.
     * @param onUserEarnedReward Callback when the user successfully watches the ad.
     * @param onAdNotReady Callback if the ad is not ready yet (optional).
     */
    fun showRewardedAd(
        activity: Activity, 
        onUserEarnedReward: () -> Unit,
        onAdNotReady: (() -> Unit)? = null
    ) {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed fullscreen content.")
                rewardedAd = null
                // Preload the next ad immediately
                loadRewardedAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show fullscreen content.")
                rewardedAd = null
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }

        if (rewardedAd != null) {
            rewardedAd?.show(activity) { rewardItem ->
                // Handle the reward.
                Log.d(TAG, "User earned the reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward()
            }
        } else {
            Log.d(TAG, "The rewarded ad wasn't ready yet.")
            onAdNotReady?.invoke()
            // Try to load again for next time
            loadRewardedAd(activity)
        }
    }
    
    fun isAdReady(): Boolean {
        return rewardedAd != null
    }
}
