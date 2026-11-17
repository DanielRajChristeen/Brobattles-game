package com.example.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.registry.GameRegistry
import com.example.core.support.DeveloperSupportConfig
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.ChillGamesTheme
import com.example.ui.theme.DarkThemeColors
import com.example.ui.theme.LightThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class RedesignUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_displaysAllEightGamesAndNavigatesCorrectly() {
        var navigatedGameId: String? = null

        composeRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                HomeScreen(
                    username = "AceGamer",
                    onNavigateToGame = { navigatedGameId = it },
                    onNavigateToSettings = {},
                    onNavigateToStats = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("BROBATTLE").assertIsDisplayed()
        composeRule.onAllNodesWithText("No Internet. Just Rivalry.").onFirst().assertIsDisplayed()

        // Verify all 8 game cards exist and route to the right game ID
        GameRegistry.games.forEach { game ->
            composeRule.onNodeWithTag("home_game_grid")
                .performScrollToNode(hasTestTag("game_card_${game.id}"))
            composeRule.onNodeWithTag("game_card_${game.id}").assertIsDisplayed().performClick()
            assertEquals(game.id, navigatedGameId)
        }

        composeRule.onNodeWithTag("home_game_grid").performScrollToIndex(0)
        composeRule.onNodeWithTag("home_stats_button").assertDoesNotExist()
        composeRule.onNodeWithTag("home_settings_button").assertDoesNotExist()
        composeRule.onNodeWithTag("home_menu_button").assertIsDisplayed()
    }

    @Test
    fun homeScreen_drawerOpensAndShowsMenuItemsAndAboutDialog() {
        var navigatedToStats = false
        var navigatedToSettings = false

        composeRule.setContent {
            ChillGamesTheme(themePreference = "LIGHT") {
                HomeScreen(
                    username = "RivalOne",
                    onNavigateToGame = {},
                    onNavigateToSettings = { navigatedToSettings = true },
                    onNavigateToStats = { navigatedToStats = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("drawer_header").assertIsDisplayed()
        composeRule.onNodeWithText("RivalOne").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_buy_coffee_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enjoying BroBattle? Support our development!").assertIsDisplayed()
        composeRule.onNodeWithText("Made with ❤️ by Two Men Works").performScrollTo().assertIsDisplayed()

        // Test About BroBattle dialog
        composeRule.onNodeWithTag("drawer_item_about").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_brobattle_dialog").assertIsDisplayed()

        // Re-open drawer and test Scoreboard navigation
        composeRule.onNodeWithText("Back to Arena").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_item_scoreboard").performScrollTo().performClick()
        assertTrue(navigatedToStats)

        // Re-open drawer and test Settings navigation
        composeRule.onNodeWithTag("home_menu_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
        assertTrue(navigatedToSettings)
    }

    @Test
    fun settingsScreen_themeSwitchUsernameAndTogglesWork() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        composeRule.setContent {
            val settings by viewModel.settings.collectAsState()
            ChillGamesTheme(themePreference = settings.themeMode) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()

        // Theme options: Light, Dark, Auto
        composeRule.onNodeWithTag("theme_option_DARK").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.themeMode == "DARK"
        }

        composeRule.onNodeWithTag("theme_option_LIGHT").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.themeMode == "LIGHT"
        }

        composeRule.onNodeWithTag("theme_option_SYSTEM").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.themeMode == "SYSTEM"
        }

        // Sound & vibration toggles
        val initialSound = viewModel.settings.value.soundEnabled
        composeRule.onNodeWithTag("setting_sound_toggle").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.soundEnabled == !initialSound
        }

        val initialVibration = viewModel.settings.value.vibrationEnabled
        composeRule.onNodeWithTag("setting_haptic_toggle").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.vibrationEnabled == !initialVibration
        }

        // Username update
        composeRule.onNodeWithTag("setting_username_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("setting_username_input").performTextInput("Champ99")
        composeRule.onNodeWithTag("save_username_button").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            viewModel.settings.value.username == "Champ99"
        }

        // Reset dialog opens and confirms
        composeRule.onNodeWithTag("reset_stats_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_reset_button").performClick()
        composeRule.waitForIdle()

        // Settings Developer Support shows Buy Me a Coffee and removes Buy me a Tea
        composeRule.onNodeWithTag("setting_buy_coffee_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setting_buy_tea_row").assertDoesNotExist()
    }

    @Test
    fun themePalettesAndBuyMeACoffeeConfig_matchRequirements() {
        assertEquals(0xFF070B18.toInt(), DarkThemeColors.background.value.toLong().ushr(32).toInt())
        assertEquals(0xFF142238.toInt(), DarkThemeColors.surface.value.toLong().ushr(32).toInt())
        assertEquals(0xFF7C4DFF.toInt(), DarkThemeColors.accent.value.toLong().ushr(32).toInt())
        assertEquals(0xFF00CFFF.toInt(), DarkThemeColors.secondaryAccent.value.toLong().ushr(32).toInt())

        assertEquals(0xFFF5F7FC.toInt(), LightThemeColors.background.value.toLong().ushr(32).toInt())
        assertEquals(0xFFFFFFFF.toInt(), LightThemeColors.surface.value.toLong().ushr(32).toInt())
        assertEquals(0xFF6941C6.toInt(), LightThemeColors.accent.value.toLong().ushr(32).toInt())
        assertEquals(0xFF0891B2.toInt(), LightThemeColors.secondaryAccent.value.toLong().ushr(32).toInt())

        assertEquals("https://buymeacoffee.com/twomenworks", DeveloperSupportConfig.BUY_ME_A_COFFEE_URL)
    }
}
