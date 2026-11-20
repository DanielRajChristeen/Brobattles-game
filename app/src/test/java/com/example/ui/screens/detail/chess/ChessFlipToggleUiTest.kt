package com.example.ui.screens.detail.chess

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.registry.GameMode
import com.example.ui.MainViewModel
import com.example.ui.theme.ChillGamesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ChessFlipToggleUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `2-player chess shows board flip toggle button that switches ON and OFF`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                ChessGameView(
                    selectedMode = GameMode.LOCAL_1V1,
                    difficulty = ChessDifficulty.MEDIUM,
                    playerColorPreference = ChessPlayerColor.WHITE,
                    viewModel = viewModel,
                    onChangeMode = {},
                    onExit = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val flipToggle = composeTestRule.onNodeWithTag("chess_flip_toggle_button")
        flipToggle.performScrollTo().assertIsDisplayed()
        flipToggle.assertIsOn()
        composeTestRule.onNodeWithText("FLIP: ON").assertIsDisplayed()

        // Tap to disable auto-flip
        flipToggle.performClick()
        composeTestRule.waitForIdle()
        flipToggle.assertIsOff()
        composeTestRule.onNodeWithText("FLIP: OFF").assertIsDisplayed()

        // Tap again to re-enable auto-flip
        flipToggle.performClick()
        composeTestRule.waitForIdle()
        flipToggle.assertIsOn()
        composeTestRule.onNodeWithText("FLIP: ON").assertIsDisplayed()
    }

    @Test
    fun `vs bot chess does not show 2-player board flip toggle button`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                ChessGameView(
                    selectedMode = GameMode.SOLO,
                    difficulty = ChessDifficulty.MEDIUM,
                    playerColorPreference = ChessPlayerColor.WHITE,
                    viewModel = viewModel,
                    onChangeMode = {},
                    onExit = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chess_flip_toggle_button").assertDoesNotExist()
    }
}
