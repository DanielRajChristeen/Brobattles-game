package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.registry.GameRegistry
import com.example.ui.theme.DarkThemeColors
import com.example.ui.theme.LightThemeColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BroBattles", appName)
  }

  @Test
  fun `launch MainActivity successfully`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    assertNotNull(controller.get())
  }

  @Test
  fun `verify all registered games exist and are configured`() {
    val games = GameRegistry.games
    assertTrue(games.isNotEmpty())
    val snakes = GameRegistry.getGameById("snakes_and_ladders")
    assertNotNull(snakes)
    assertTrue(snakes!!.isAvailable)
    assertEquals("Snakes & Ladders", snakes.title)

    val xo = GameRegistry.getGameById("xo")
    assertNotNull(xo)
    assertTrue(xo!!.isAvailable)
    assertEquals(R.drawable.ic_game_xo, xo.imageRes)

    val tug = GameRegistry.getGameById("tug_of_war")
    assertNotNull(tug)
    assertTrue(tug!!.isAvailable)

    val chess = GameRegistry.getGameById("chess")
    assertNotNull(chess)
    assertTrue(chess!!.isAvailable)
    assertEquals("Chess", chess.title)
    assertEquals("♟️", chess.emoji)

    val dotConnections = GameRegistry.getGameById("dot_connections")
    assertNotNull(dotConnections)
    assertTrue(dotConnections!!.isAvailable)
    assertEquals("Dot Connections", dotConnections.title)
    assertEquals("🔲", dotConnections.emoji)

    val archery = GameRegistry.getGameById("archery")
    assertNull(archery)
  }

  @Test
  fun `verify light and dark theme tokens provide high contrast`() {
    // Light Theme
    assertFalse(LightThemeColors.isDark)
    assertNotEquals(LightThemeColors.background, LightThemeColors.surface)
    assertNotEquals(LightThemeColors.surface, LightThemeColors.border)
    assertNotEquals(LightThemeColors.primaryText, LightThemeColors.secondaryText)
    // Dark Theme
    assertTrue(DarkThemeColors.isDark)
    assertNotEquals(DarkThemeColors.background, DarkThemeColors.surface)
    assertNotEquals(DarkThemeColors.surface, DarkThemeColors.border)
    assertNotEquals(DarkThemeColors.primaryText, DarkThemeColors.secondaryText)
  }

  @Test
  fun `verify custom username default and local database persistence`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = com.example.core.data.db.AppDatabase.getInstance(context)
    val dao = database.userSettingsDao()

    // Initial default setting
    val defaultSettings = com.example.core.data.model.UserSettings()
    assertEquals("Player", defaultSettings.username)

    // Save customized username locally
    val updatedSettings = defaultSettings.copy(username = "BroChampion")
    dao.saveSettings(updatedSettings)

    // Read back from database
    val persisted = dao.getSettings().first()
    assertNotNull(persisted)
    assertEquals("BroChampion", persisted?.username)
  }

  @Test
  fun `verify repository updateUsername persists trimmed username`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = com.example.core.data.db.AppDatabase.getInstance(context)
    val repository = com.example.core.data.repository.GameRepository(database.matchRecordDao(), database.userSettingsDao())

    repository.updateUsername("   Daniel   ")
    val persisted = repository.userSettings.first()
    assertEquals("Daniel", persisted.username)

    // Blank username should be rejected and not overwrite existing username
    repository.updateUsername("    ")
    val afterBlank = repository.userSettings.first()
    assertEquals("Daniel", afterBlank.username)
  }

  @Test
  fun `verify multiplayerManager respects custom username`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    val manager = com.example.core.multiplayer.LocalWifiMultiplayerManager(context, scope)

    val defaultName = manager.localDeviceName
    assertTrue(defaultName.isNotBlank())

    manager.customUsername = "SpeedyGamer"
    assertEquals("SpeedyGamer", manager.localDeviceName)
  }
}
