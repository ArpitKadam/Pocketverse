// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pocketverse.core.database.api.GameEntry
import com.pocketverse.core.model.api.Platform
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun entry(title: String, platform: Platform = Platform.GBA, launches: Int = 0, seconds: Long = 0) = GameEntry(
        folderName = "$title [00000000]",
        id = "00000000",
        title = title,
        platform = platform,
        engineId = "e",
        importedAt = Instant.EPOCH,
        lastPlayedAt = null,
        playTimeSeconds = seconds,
        launchCount = launches,
        coverPath = null,
    )

    @Test
    fun `while loading with no games a progress indicator is shown, not the empty message`() {
        compose.setContent { LibraryScreen(LibraryUiState(loading = true), onAddTestGame = null) }
        compose.onNodeWithTag(LibraryTags.LOADING).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun `an empty library says so and offers no test button when none is provided`() {
        compose.setContent { LibraryScreen(LibraryUiState(loading = false), onAddTestGame = null) }
        compose.onNodeWithText("No games yet").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.ADD_TEST_GAME).assertDoesNotExist()
    }

    @Test
    fun `the test button appears when provided and calls back`() {
        var clicks = 0
        compose.setContent { LibraryScreen(LibraryUiState(loading = false), onAddTestGame = { clicks++ }) }
        compose.onNodeWithTag(LibraryTags.ADD_TEST_GAME).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `games are listed with platform and play time`() {
        val games = listOf(entry("Alpha"), entry("Beta", Platform.NDS, launches = 3, seconds = 7200))
        compose.setContent { LibraryScreen(LibraryUiState(loading = false, games = games), onAddTestGame = null) }
        compose.onNodeWithTag(LibraryTags.LIST).assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("GBA").assertIsDisplayed()
        compose.onNodeWithText("NDS · 120 minutes played").assertIsDisplayed()
    }

    @Test
    fun `an error replaces the list and shows its message`() {
        compose.setContent { LibraryScreen(LibraryUiState(loading = false, error = "disk full"), onAddTestGame = null) }
        compose.onNodeWithText("The library is not available").assertIsDisplayed()
        compose.onNodeWithText("disk full").assertIsDisplayed()
    }

    @Test
    fun `unreadable folders are reported with the right plural`() {
        compose.setContent { LibraryScreen(LibraryUiState(loading = false, unreadableFolders = 1), onAddTestGame = null) }
        compose.onNodeWithText("1 folder could not be read").assertIsDisplayed()
    }
}
