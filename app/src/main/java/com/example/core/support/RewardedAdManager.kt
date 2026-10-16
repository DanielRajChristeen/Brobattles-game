package com.example.core.support

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Dedicated manager for voluntary developer-support rewarded ads.
 *
 * Rules:
 * - NO automatic/forced ads anywhere in Chill Games.
 * - Only runs when voluntarily triggered from the "Buy me a Tea" button.
 * - Reward callback is only delivered upon full completion of the advertisement.
 * - Safe error handling: never crashes if offline, unavailable, or dismissed early.
 */
object RewardedAdManager {
    private const val TAG = "RewardedAdManager"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isLoading = false

    private var rewardedAd: RewardedAd? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val reqConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(reqConfig)

            MobileAds.initialize(context.applicationContext) {
                isInitialized = true
                Log.d(TAG, "Mobile Ads SDK initialized")
                loadAd(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds SDK", e)
        }
    }

    fun isAdAvailable(): Boolean = rewardedAd != null

    fun loadAd(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        if (rewardedAd != null) {
            onResult?.invoke(true)
            return
        }
        if (isLoading) {
            onResult?.invoke(false)
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        try {
            RewardedAd.load(
                context.applicationContext,
                DeveloperSupportConfig.REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                        rewardedAd = null
                        isLoading = false
                        onResult?.invoke(false)
                    }

                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d(TAG, "Rewarded ad loaded successfully")
                        rewardedAd = ad
                        isLoading = false
                        onResult?.invoke(true)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during RewardedAd.load", e)
            isLoading = false
            onResult?.invoke(false)
        }
    }

    /**
     * Shows voluntary rewarded ad for "Buy me a Tea".
     *
     * @param activity The host Activity
     * @param onRewardEarned Invoked strictly after full ad completion
     * @param onAdDismissedWithoutReward Invoked if user closes before completion
     * @param onAdUnavailable Invoked if ad is unavailable or fails to show
     */
    fun showDeveloperSupportRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdDismissedWithoutReward: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        if (!isInitialized) {
            initialize(activity)
            onAdUnavailable()
            return
        }

        val currentAd = rewardedAd
        if (currentAd == null) {
            loadAd(activity)
            onAdUnavailable()
            return
        }

        var rewardGranted = false

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad displayed full screen")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                rewardedAd = null
                loadAd(activity)
                onAdUnavailable()
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed, rewardGranted=$rewardGranted")
                rewardedAd = null
                // Preload for next time
                loadAd(activity)
                if (!rewardGranted) {
                    onAdDismissedWithoutReward()
                }
            }
        }

        currentAd.show(activity) { rewardItem ->
            Log.d(TAG, "User earned voluntary support reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardGranted = true
            onRewardEarned()
        }
    }
}
