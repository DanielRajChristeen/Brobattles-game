package com.example.core.support

/**
 * Centralized configuration for Chill Games Developer Support.
 *
 * Contains configurable external links and ad unit IDs.
 */
object DeveloperSupportConfig {
    /**
     * Google Form URL for bug reports, game suggestions, and feature requests.
     */
    const val DEVELOPER_FEEDBACK_FORM_URL = "https://forms.gle/LiTDr5c58fRynA5R8"

    /**
     * Official Google AdMob test Rewarded Ad Unit ID for Android.
     */
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    /**
     * Production Rewarded Ad Unit ID.
     * Insert your production AdMob Rewarded Ad Unit ID here before release.
     */
    const val PRODUCTION_REWARDED_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"

    /**
     * Flag to toggle between official test ads and production ad units.
     */
    const val IS_DEVELOPMENT_BUILD = true

    /**
     * Active Rewarded Ad Unit ID.
     */
    val REWARDED_AD_UNIT_ID: String
        get() = if (IS_DEVELOPMENT_BUILD) TEST_REWARDED_AD_UNIT_ID else PRODUCTION_REWARDED_AD_UNIT_ID
}
