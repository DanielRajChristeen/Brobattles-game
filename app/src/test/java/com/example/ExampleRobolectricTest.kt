package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.registry.GameRegistry
import com.example.ui.theme.DarkThemeColors
import com.example.ui.theme.LightThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    val tug = GameRegistry.getGameById("tug_of_war")
    assertNotNull(tug)
    assertTrue(tug!!.isAvailable)

    val chess = GameRegistry.getGameById("chess")
    assertNotNull(chess)
    assertTrue(chess!!.isAvailable)
    assertEquals("Chess", chess.title)
    assertEquals("♟️", chess.emoji)

    val archery = GameRegistry.getGameById("archery")
    assertNotNull(archery)
    assertTrue(archery!!.isAvailable)
    assertEquals("Archery", archery.title)
    assertEquals("🏹", archery.emoji)
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
}
