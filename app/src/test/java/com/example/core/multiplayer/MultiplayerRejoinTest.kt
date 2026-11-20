package com.example.core.multiplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.engine.PlayerId
import com.example.core.engine.battleship.BattleshipEngine
import com.example.core.engine.battleship.BattleshipPhase
import com.example.core.engine.battleship.BattleshipPlayer
import com.example.core.engine.battleship.CellCoordinate
import com.example.core.engine.checkers.CheckersEngine
import com.example.core.engine.chess.ChessEngine
import com.example.core.engine.chess.ChessMove
import com.example.core.engine.chess.ChessSquare
import com.example.core.engine.dotconnections.DotBoardSize
import com.example.core.engine.dotconnections.DotConnectionsEngine
import com.example.core.engine.dotconnections.DotEdge
import com.example.core.engine.dotconnections.DotPlayer
import com.example.core.engine.dotconnections.EdgeOrientation
import com.example.core.engine.ludo.LudoGameEngine
import com.example.core.engine.snakesandladders.SnakesAndLaddersGameEngine
import com.example.core.engine.tugofwar.TugOfWarGameEngine
import com.example.core.engine.tugofwar.TugOfWarMove
import com.example.core.engine.xo.XOGameEngine
import com.example.core.engine.xo.XOMove
import com.example.core.registry.GameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiplayerRejoinTest {

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
    private val managers = mutableListOf<LocalWifiMultiplayerManager>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        managers.forEach { runCatching { it.disconnect(clearLastMatchSession = true) } }
        managers.clear()
        testScope.cancel()
    }

    private fun createManager(playerId: String): LocalWifiMultiplayerManager {
        val m = LocalWifiMultiplayerManager(
            context = context,
            scope = testScope,
            playerIdOverride = playerId
        )
        managers.add(m)
        return m
    }

    private fun waitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for condition after ${timeoutMs}ms", condition())
    }

    private fun loopbackSession(hostSession: DiscoveredSession): DiscoveredSession {
        return hostSession.copy(hostIp = "127.0.0.1")
    }

    @Test
    fun `host receives rejoin request in real time, accepts it, restores identity and syncs live match state`() {
        val host = createManager("host-id-1")
        val client = createManager("client-id-2")

        // 1. Host creates room & Client joins normally
        host.startHosting(gameId = "xo", gameTitle = "Tic Tac Toe", hostName = "HostAlpha")
        val hostingState = host.lobbyState.value as MultiplayerLobbyState.Hosting
        val session = loopbackSession(hostingState.session)

        client.connectToSession(session, playerName = "GuestBeta")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }

        // 2. Host launches match & advances live XO game state
        host.launchGame("""{"targetScore":5}""")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.InGame }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        val xoEngine = XOGameEngine(initialTargetScore = 5)
        xoEngine.makeMove(XOMove(toIndex = 4), PlayerId.PLAYER_1)
        xoEngine.makeMove(XOMove(toIndex = 0), PlayerId.PLAYER_2)
        xoEngine.makeMove(XOMove(toIndex = 2), PlayerId.PLAYER_1)
        val liveSnapshot = MultiplayerStateSnapshot.encodeXO(xoEngine.state)
        host.registerHostStateSnapshotProvider { "xo" to liveSnapshot }

        // Track incoming actions on client (including REJOIN_STATE_SYNC and live gameplay afterward)
        val clientActions = mutableListOf<PlayerAction>()
        val hostActions = mutableListOf<PlayerAction>()
        val cJob = testScope.launch { client.incomingActions.collect { clientActions.add(it) } }
        val hJob = testScope.launch { host.incomingActions.collect { hostActions.add(it) } }

        // 3. Client disconnects unexpectedly
        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }
        assertEquals(session.sessionId, client.lastMatchSession.value?.sessionId)

        // 4. Client sends rejoin request to Host
        client.requestRejoin(session, playerName = "GuestBeta")

        // Host must immediately see the incoming rejoin request without manual refresh
        waitUntil { host.pendingRejoinRequests.value.size == 1 }
        val incoming = host.pendingRejoinRequests.value.first()
        assertEquals("client-id-2", incoming.playerId)
        assertEquals("GuestBeta", incoming.playerName)
        assertEquals("PLAYER_2", incoming.role)

        // Client sees Pending status while waiting for host approval
        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Pending }

        // 5. Host accepts rejoin request
        host.acceptRejoinRequest(incoming.playerId)

        // Host's pending queue clears and match resumes immediately
        waitUntil { host.pendingRejoinRequests.value.isEmpty() }
        assertFalse(host.isConnectionPaused.value)
        val hostInGame = host.lobbyState.value as MultiplayerLobbyState.InGame
        assertEquals(session.sessionId, hostInGame.session.sessionId)
        assertTrue(hostInGame.isHost)

        // Client reconnects to InGame with original identity, role, room ID, and live state snapshot
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }
        val clientInGame = client.lobbyState.value as MultiplayerLobbyState.InGame
        assertEquals(session.sessionId, clientInGame.session.sessionId)
        assertEquals("client-id-2", clientInGame.localPlayer.id)
        assertEquals("PLAYER_2", clientInGame.localPlayer.role)
        assertFalse(clientInGame.isHost)
        assertTrue(client.rejoinRequestStatus.value is RejoinRequestStatus.Accepted)

        // Verify live match state snapshot was delivered & matches exact board
        val syncedRaw = client.consumePendingRejoinState("xo")
        assertNotNull(syncedRaw)
        val syncedState = MultiplayerStateSnapshot.decodeXO(syncedRaw!!)
        assertNotNull(syncedState)
        assertEquals(PlayerId.PLAYER_1, syncedState!!.board[4])
        assertEquals(PlayerId.PLAYER_2, syncedState.board[0])
        assertEquals(PlayerId.PLAYER_1, syncedState.board[2])
        assertEquals(PlayerId.PLAYER_2, syncedState.activePlayer)

        // 6. Verify bidirectional gameplay actions continue smoothly over the rejoined connection
        client.sendAction("XO_MOVE", "6")
        waitUntil { hostActions.any { it.actionType == "XO_MOVE" && it.payload == "6" } }
        host.sendAction("XO_SYNC", liveSnapshot)
        waitUntil { clientActions.any { it.actionType == "XO_SYNC" } }

        cJob.cancel()
        hJob.cancel()
    }

    @Test
    fun `host rejects rejoin request - requester stays outside match and host match state is unaffected`() {
        val host = createManager("host-rej-1")
        val client = createManager("client-rej-2")

        host.startHosting(gameId = "chess", gameTitle = "Chess", hostName = "HostKing")
        val session = loopbackSession((host.lobbyState.value as MultiplayerLobbyState.Hosting).session)

        client.connectToSession(session, playerName = "GuestKnight")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }

        host.launchGame("")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.InGame }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }

        client.requestRejoin(session, playerName = "GuestKnight")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }

        // Host rejects the rejoin request
        host.rejectRejoinRequest("client-rej-2", reason = "Host declined your rejoin request")

        waitUntil { host.pendingRejoinRequests.value.isEmpty() }
        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Rejected }

        val rejected = client.rejoinRequestStatus.value as RejoinRequestStatus.Rejected
        assertEquals("Host declined your rejoin request", rejected.reason)
        assertFalse(client.lobbyState.value is MultiplayerLobbyState.InGame)
        assertNull(client.consumePendingRejoinState("chess"))
        // Host stays in its ongoing match untouched
        assertTrue(host.isSessionHost.value)
        assertTrue(host.isConnectionPaused.value)
    }

    @Test
    fun `repeated rejoin requests from same player do not create duplicate entries on host`() {
        val host = createManager("host-dedup-1")
        val client = createManager("client-dedup-2")

        host.startHosting(gameId = "xo", gameTitle = "Tic Tac Toe", hostName = "Host")
        val session = loopbackSession((host.lobbyState.value as MultiplayerLobbyState.Hosting).session)

        client.connectToSession(session, playerName = "Repeater")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }

        // Fire 3 consecutive rejoin requests
        client.requestRejoin(session, playerName = "Repeater")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }
        client.requestRejoin(session, playerName = "Repeater")
        Thread.sleep(100)
        client.requestRejoin(session, playerName = "Repeater")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }
        Thread.sleep(150)

        // Still exactly 1 deduplicated entry
        assertEquals(1, host.pendingRejoinRequests.value.size)
        assertEquals("client-dedup-2", host.pendingRejoinRequests.value.first().playerId)

        // Accepting the single deduplicated entry still reconnects the latest socket cleanly
        host.acceptRejoinRequest("client-dedup-2")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }
        assertTrue(host.pendingRejoinRequests.value.isEmpty())
    }

    @Test
    fun `unauthorized third party and wrong session ID are rejected without appearing in host requests`() {
        val host = createManager("host-auth-1")
        val client = createManager("client-auth-2")
        val intruder = createManager("intruder-99")

        host.startHosting(gameId = "xo", gameTitle = "Tic Tac Toe", hostName = "Host")
        val session = loopbackSession((host.lobbyState.value as MultiplayerLobbyState.Hosting).session)

        client.connectToSession(session, playerName = "LegitPlayer")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        // 1. Intruder tries to rejoin an active match
        intruder.requestRejoin(session, playerName = "Intruder")
        waitUntil { intruder.rejoinRequestStatus.value is RejoinRequestStatus.Rejected }
        assertTrue(host.pendingRejoinRequests.value.isEmpty())
        assertFalse(intruder.lobbyState.value is MultiplayerLobbyState.InGame)

        // 2. Original client disconnects and sends wrong sessionId
        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }

        val wrongSession = session.copy(sessionId = "wrong-room-id-xyz")
        client.requestRejoin(wrongSession, playerName = "LegitPlayer")
        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Rejected }
        val rejected = client.rejoinRequestStatus.value as RejoinRequestStatus.Rejected
        assertTrue(rejected.reason.contains("Invalid room session", ignoreCase = true))
        assertTrue(host.pendingRejoinRequests.value.isEmpty())
    }

    @Test
    fun `cancelled pending rejoin request is automatically removed from host lobby`() {
        val host = createManager("host-cancel-1")
        val client = createManager("client-cancel-2")

        host.startHosting(gameId = "ludo", gameTitle = "Ludo", hostName = "Host")
        val session = loopbackSession((host.lobbyState.value as MultiplayerLobbyState.Hosting).session)

        client.connectToSession(session, playerName = "Guest")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }

        client.requestRejoin(session, playerName = "Guest")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }

        // Client cancels/closes before Host acts
        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.pendingRejoinRequests.value.isEmpty() }
    }

    @Test
    fun `host disconnect notifies waiting rejoin client and clears expired session cleanly`() {
        val host = createManager("host-close-1")
        val client = createManager("client-close-2")

        host.startHosting(gameId = "checkers", gameTitle = "Checkers", hostName = "Host")
        val session = loopbackSession((host.lobbyState.value as MultiplayerLobbyState.Hosting).session)

        client.connectToSession(session, playerName = "Guest")
        waitUntil { host.lobbyState.value is MultiplayerLobbyState.Connected }
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.Connected }
        host.launchGame("")
        waitUntil { client.lobbyState.value is MultiplayerLobbyState.InGame }

        client.disconnect(clearLastMatchSession = false)
        waitUntil { host.isConnectionPaused.value }

        client.requestRejoin(session, playerName = "Guest")
        waitUntil { host.pendingRejoinRequests.value.size == 1 }
        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Pending }

        // Host closes room while client is waiting
        host.disconnect(clearLastMatchSession = true)

        waitUntil { client.rejoinRequestStatus.value is RejoinRequestStatus.Rejected }
        assertNull(client.lastMatchSession.value)
        assertTrue(host.pendingRejoinRequests.value.isEmpty())
    }

    @Test
    fun `all 8 games round-trip cleanly through MultiplayerStateSnapshot`() {
        // 1. XO
        val xo = XOGameEngine(initialTargetScore = 5)
        xo.makeMove(XOMove(toIndex = 4), PlayerId.PLAYER_1)
        val xoDecoded = MultiplayerStateSnapshot.decodeXO(MultiplayerStateSnapshot.encodeXO(xo.state))
        assertEquals(xo.state.board, xoDecoded?.board)
        assertEquals(xo.state.activePlayer, xoDecoded?.activePlayer)

        // 2. Tug of War
        val tug = TugOfWarGameEngine(initialTargetScore = 3)
        tug.makeMove(TugOfWarMove(force = 1.0f), PlayerId.PLAYER_1)
        val tugDecoded = MultiplayerStateSnapshot.decodeTugOfWar(MultiplayerStateSnapshot.encodeTugOfWar(tug.state))
        assertEquals(tug.state.p1Taps, tugDecoded?.p1Taps)
        assertEquals(tug.state.ropePosition, tugDecoded?.ropePosition)

        // 3. Snakes & Ladders
        val snakes = SnakesAndLaddersGameEngine(initialPlayerCount = 2)
        val snakesDecoded = MultiplayerStateSnapshot.decodeSnakes(MultiplayerStateSnapshot.encodeSnakes(snakes.state))
        assertEquals(snakes.state.playerPositions, snakesDecoded?.playerPositions)

        // 4. Chess
        val chess = ChessEngine()
        chess.makeMove(ChessMove(ChessSquare(6, 4), ChessSquare(4, 4)))
        val chessDecoded = MultiplayerStateSnapshot.decodeChess(
            MultiplayerStateSnapshot.encodeChess(chess.state, chess.getMoveHistory())
        )
        assertNotNull(chessDecoded)
        assertEquals(chess.state.activeColor, chessDecoded!!.state.activeColor)
        assertEquals(1, chessDecoded.moveHistory.size)

        // 5. Checkers
        val checkers = CheckersEngine()
        val checkersDecoded = MultiplayerStateSnapshot.decodeCheckers(
            MultiplayerStateSnapshot.encodeCheckers(checkers.state)
        )
        assertEquals(checkers.state.board.size, checkersDecoded?.board?.size)

        // 6. Dot Connections
        val dots = DotConnectionsEngine(DotBoardSize.SMALL)
        dots.makeMove(DotEdge(EdgeOrientation.HORIZONTAL, 0, 0), DotPlayer.PLAYER_1)
        val dotsDecoded = MultiplayerStateSnapshot.decodeDotConnections(
            MultiplayerStateSnapshot.encodeDotConnections(dots.state)
        )
        assertEquals(dots.state.placedEdges.size, dotsDecoded?.placedEdges?.size)
        assertEquals(dots.state.activePlayer, dotsDecoded?.activePlayer)

        // 7. Ludo
        val ludo = LudoGameEngine(GameMode.BLUETOOTH_1V1, initialPlayerCount = 2)
        ludo.rollDice()
        val ludoDecoded = MultiplayerStateSnapshot.decodeLudo(MultiplayerStateSnapshot.encodeLudo(ludo.state))
        assertEquals(ludo.state.diceRoll, ludoDecoded?.diceRoll)
        assertEquals(ludo.state.players.size, ludoDecoded?.players?.size)

        // 8. Battleship
        val battleship = BattleshipEngine()
        val p1Fleet = BattleshipEngine.generateRandomFleet()
        val p2Fleet = BattleshipEngine.generateRandomFleet()
        battleship.setFleet(BattleshipPlayer.PLAYER_1, p1Fleet)
        battleship.setFleet(BattleshipPlayer.PLAYER_2, p2Fleet)
        battleship.setPhase(BattleshipPhase.BATTLE)
        battleship.fireShot(BattleshipPlayer.PLAYER_1, CellCoordinate(0, 0))
        val bsDecoded = MultiplayerStateSnapshot.decodeBattleship(
            MultiplayerStateSnapshot.encodeBattleship(battleship.state)
        )
        assertEquals(BattleshipPhase.BATTLE, bsDecoded?.phase)
        assertEquals(battleship.state.p1Radar.shots.size, bsDecoded?.p1Radar?.shots?.size)
    }
}
