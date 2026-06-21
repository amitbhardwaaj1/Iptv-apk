package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.Channel
import com.example.ui.screens.SquareChannelCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun grid_item_screenshot() {
    val mockChannel = Channel(
        id = 1,
        playlistId = 1,
        name = "HBO Movies HD",
        streamUrl = "http://test.url",
        logoUrl = null,
        category = "Movies",
        isFavorite = true
    )
    composeTestRule.setContent {
      MyApplicationTheme {
         SquareChannelCard(
             channel = mockChannel,
             isPlaying = true,
             onChannelClick = {}
         )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
