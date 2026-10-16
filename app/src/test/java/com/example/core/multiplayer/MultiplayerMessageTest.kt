package com.example.core.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiplayerMessageTest {

    @Test
    fun testMultiplayerMessageSerialization() {
        val message = MultiplayerMessage(
            type = MultiplayerMessage.TYPE_PLAYER_ACTION,
            sessionId = "sess_101",
            senderId = "player_123",
            payload = "CHESS_MOVE:0,1,2,3,"
        )

        val json = message.toJson()
        val parsed = MultiplayerMessage.fromJson(json)

        assertNotNull(parsed)
        assertEquals(MultiplayerMessage.TYPE_PLAYER_ACTION, parsed?.type)
        assertEquals("sess_101", parsed?.sessionId)
        assertEquals("player_123", parsed?.senderId)
        assertEquals("CHESS_MOVE:0,1,2,3,", parsed?.payload)
    }

    @Test
    fun testMultiplayerModels() {
        val session = DiscoveredSession(
            sessionId = "sess_1",
            hostName = "HostPhone",
            gameId = "tug_of_war",
            gameTitle = "Tug of War",
            hostIp = "192.168.43.1",
            port = 8888,
            currentPlayers = 1,
            maxPlayers = 2
        )

        assertEquals("sess_1", session.sessionId)
        assertEquals("HostPhone", session.hostName)
        assertEquals("tug_of_war", session.gameId)
        assertEquals("Tug of War", session.gameTitle)
        assertEquals("192.168.43.1", session.hostIp)
        assertEquals(8888, session.port)

        val player = MultiplayerPlayer(
            id = "p1",
            name = "Player 1",
            isHost = true,
            role = "PLAYER_1",
            isReady = true
        )
        assertEquals("p1", player.id)
        assertTrue(player.isHost)
        assertTrue(player.isReady)
        assertEquals("PLAYER_1", player.role)
    }
}
