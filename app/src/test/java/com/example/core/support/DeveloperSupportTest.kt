package com.example.core.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSupportTest {

    @Test
    fun `developer support configuration has valid feedback form url and ad unit ids`() {
        assertNotNull(DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL)
        assertTrue(DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL.startsWith("https://"))
        assertTrue(
            DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL.contains("forms.gle") ||
            DeveloperSupportConfig.DEVELOPER_FEEDBACK_FORM_URL.contains("forms.google.com")
        )

        assertEquals("ca-app-pub-3940256099942544/5224354917", DeveloperSupportConfig.TEST_REWARDED_AD_UNIT_ID)
        assertNotNull(DeveloperSupportConfig.PRODUCTION_REWARDED_AD_UNIT_ID)
        assertTrue(DeveloperSupportConfig.IS_DEVELOPMENT_BUILD)
        assertEquals(DeveloperSupportConfig.TEST_REWARDED_AD_UNIT_ID, DeveloperSupportConfig.REWARDED_AD_UNIT_ID)
    }

    @Test
    fun `rewarded ad manager handles unready state safely without throwing exceptions`() {
        // Without an active ad loaded, isAdAvailable should be false
        assertFalse(RewardedAdManager.isAdAvailable())
    }
}
