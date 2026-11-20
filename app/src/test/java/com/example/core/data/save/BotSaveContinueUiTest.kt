package com.example.core.data.save

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.bot.BotDifficulty
import com.example.core.data.db.AppDatabase
import com.example.core.engine.PlayerId
import com.example.core.engine.chess.ChessDifficulty
import com.example.core.engine.chess.ChessEngine
import com.example.core.engine.chess.ChessPlayerColor
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.chess.PieceColor
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.ui.MainViewModel
import com.example.ui.screens.detail.GameDetailScreen
import com.example.ui.theme.ChillGamesTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class BotSaveContinueUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(app)
        db.savedGameSessionDao().deleteAllSessions()
    }

    @After
    fun tearDown() = runBlocking {
        db.savedGameSessionDao().deleteAllSessions()
    }

    @Test
    fun `when no save exists launch screen shows standard PLAY button without Continue`() {
        val viewModel = MainViewModel(app)
        composeTestRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                GameDetailScreen(gameId = "xo", viewModel = viewModel, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("start_game_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_game_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("new_game_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("discard_save_button").assertDoesNotExist()
    }

    @Test
    fun `when Vs Bot save exists launch screen shows Continue New Game and Discard only in SOLO mode and Discard isolates game slot`() {
        // Seed saved sessions for both XO and Chess
        val xoEngine = XOGameEngine(initialTargetScore = 3)
        xoEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        val xoSave = BotGameStateSerializer.encodeXO(xoEngine.state, BotDifficulty.HARD)!!

        val chessEngine = ChessEngine()
        val openingMove = chessEngine.currentLegalMoves.first { it.from == ChessSquare(6, 4) && it.to == ChessSquare(4, 4) }
        chessEngine.makeMove(openingMove)
        val chessSave = BotGameStateSerializer.encodeChess(
            chessEngine.state,
            ChessDifficulty.MEDIUM,
            ChessPlayerColor.WHITE,
            PieceColor.WHITE,
            chessEngine.getMoveHistory()
        )!!

        runBlocking {
            db.savedGameSessionDao().upsertSession(xoSave)
            db.savedGameSessionDao().upsertSession(chessSave)
        }

        val viewModel = MainViewModel(app)
        composeTestRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                GameDetailScreen(gameId = "xo", viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("continue_game_button").assertExists()
                true
            }.getOrDefault(false)
        }

        // SOLO mode displays Continue, New Game, Banner, and Discard
        composeTestRule.onNodeWithTag("saved_session_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_game_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_game_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("discard_save_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_game_button").assertDoesNotExist()

        // Switching to LOCAL_1V1 preserves standard PLAY flow (hides Continue/New Game)
        composeTestRule.onNodeWithTag("mode_option_LOCAL_1V1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_game_button").assertDoesNotExist()

        // Switching back to SOLO brings Continue back
        composeTestRule.onNodeWithTag("mode_option_SOLO").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("continue_game_button").performScrollTo().assertIsDisplayed()

        // Tapping Discard removes XO save and restores standard PLAY button, while Chess save remains untouched
        composeTestRule.onNodeWithTag("discard_save_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("start_game_button").assertExists()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("continue_game_button").assertDoesNotExist()

        runBlocking {
            assertNull(db.savedGameSessionDao().getSession(BotGameStateSerializer.GAME_XO))
            assertNotNull(db.savedGameSessionDao().getSession(BotGameStateSerializer.GAME_CHESS))
        }
    }

    @Test
    fun `clicking Continue Game resumes saved XO match state`() {
        val xoEngine = XOGameEngine(initialTargetScore = 5)
        xoEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(1), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(8), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(2), PlayerId.PLAYER_1) // P1 wins round 1
        xoEngine.startRound()
        xoEngine.makeMove(XOMove(4), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(0), PlayerId.PLAYER_1)
        val xoSave = BotGameStateSerializer.encodeXO(xoEngine.state, BotDifficulty.HARD)!!

        runBlocking {
            db.savedGameSessionDao().upsertSession(xoSave)
        }

        val viewModel = MainViewModel(app)
        composeTestRule.setContent {
            ChillGamesTheme(themePreference = "DARK") {
                GameDetailScreen(gameId = "xo", viewModel = viewModel, onBack = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("continue_game_button").assertExists()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("continue_game_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Launch view is gone and game board is active with restored Round 2 / 5 state
        composeTestRule.onNodeWithTag("continue_game_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("xo_restart_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("ROUND 2 / 5").assertIsDisplayed()
    }
}
