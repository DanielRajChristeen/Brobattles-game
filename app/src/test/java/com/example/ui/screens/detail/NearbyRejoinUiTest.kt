package com.example.ui.screens.detail

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.multiplayer.LocalWifiMultiplayerManager
import com.example.core.multiplayer.MultiplayerLobbyState
import com.example.core.multiplayer.RejoinRequestStatus
import com.example.ui.theme.ChillGamesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NearbyRejoinUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private val managers = mutableListOf<LocalWifiMultiplayerManager>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        managers.forEach { runCatching { it.disconnect(clearLastMatchSession = true) } }
        managers.clear()
        scope.cancel()
    }

    private fun createManager(id: String): LocalWifiMultiplayerManager {
        return LocalWifiMultiplayerManager(context, scope, playerIdOverride = id).also { managers.add(it) }
    }

    private fun waitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for condition", condition())
    }

    @Test
    fun `host waiting dialog displays incoming rejoin request with player name and accepts on click`() {
        val host = createManager("ui-host-1")
        val client = createManager("ui-client-2")

        host.startHosting(gameId = "xo", gameTitle = "Tic Tac Toe", hostName = "HostPlayer")
        val session = (host.lobbyState.value as MultiplayerLobbyState.Hosting).session.copy(hostIp = "127.0.0.1")

        client.connectToSession(session, playerName = "AlexRejoin")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        // Render Host's NearbyReconnectDialog
        composeRule.setContent {
            ChillGamesTheme {
                NearbyReconnectDialog(
                    manager = host,
                    onExitToLobby = {}
                )
            }
        }

        // Disconnect client and send rejoin request
        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }
        client.requestRejoin(session, playerName = "AlexRejoin")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }

        composeRule.waitForIdle()

        // Host waiting lobby immediately displays the requester's name, Accept, and Reject buttons
        composeRule.onNodeWithTag("rejoin_request_card_ui-client-2").assertIsDisplayed()
        composeRule.onNodeWithText("AlexRejoin").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_rejoin_ui-client-2").assertIsDisplayed()
        composeRule.onNodeWithTag("reject_rejoin_ui-client-2").assertIsDisplayed()

        // Tapping Accept restores both host and client into InGame
        composeRule.onNodeWithTag("accept_rejoin_ui-client-2").performClick()

        waitUntil { host.pendingRejoinRequests.value.isEmpty() }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }
        assertTrue(host.lobbyState.value is MultiplayerLobbyState.InGame)
    }

    @Test
    fun `host waiting dialog rejects rejoin request on click and client receives rejection`() {
        val host = createManager("ui-host-rej-1")
        val client = createManager("ui-client-rej-2")

        host.startHosting(gameId = "chess", gameTitle = "Chess", hostName = "HostPlayer")
        val session = (host.lobbyState.value as MultiplayerLobbyState.Hosting).session.copy(hostIp = "127.0.0.1")

        client.connectToSession(session, playerName = "SamGuest")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        composeRule.setContent {
            ChillGamesTheme {
                NearbyReconnectDialog(
                    manager = host,
                    onExitToLobby = {}
                )
            }
        }

        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }
        client.requestRejoin(session, playerName = "SamGuest")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reject_rejoin_ui-client-rej-2").performClick()

        waitUntil { host.pendingRejoinRequests.value.isEmpty() }
        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Rejected }
    }
}
