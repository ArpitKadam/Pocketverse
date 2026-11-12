// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketverse.core.database.api.GameEntry
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.library.R

object LibraryTags {
    const val LOADING = "library_loading"
    const val EMPTY = "library_empty"
    const val ERROR = "library_error"
    const val LIST = "library_list"
    const val ADD_TEST_GAME = "library_add_test_game"
}

/**
 * The library: a loading indicator, an error, an empty-state, or the list of games. Stateless; the caller owns the state.
 * Mode-specific looks (GBA, NDS, PC) are applied later through the theme API.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAddTestGame: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The content takes the space left over, so the unreadable-folders note below it is always visible.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> CenteredMessage(
                    title = stringResource(R.string.library_error_title),
                    hint = state.error,
                    tag = LibraryTags.ERROR,
                )
                state.loading && state.games.isEmpty() -> CenteredLoading()
                state.games.isEmpty() -> CenteredMessage(
                    title = stringResource(R.string.library_empty_title),
                    hint = stringResource(R.string.library_empty_hint),
                    tag = LibraryTags.EMPTY,
                    action = onAddTestGame?.let { stringResource(R.string.library_add_test_game) to it },
                )
                else -> GameList(state.games)
            }
        }
        if (state.unreadableFolders > 0) {
            Text(
                text = pluralStringResource(R.plurals.library_unreadable_folders, state.unreadableFolders, state.unreadableFolders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun CenteredLoading() {
    Column(Modifier.fillMaxSize().testTag(LibraryTags.LOADING), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(title: String, hint: String, tag: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (action != null) {
            Button(onClick = action.second, modifier = Modifier.padding(top = 24.dp).testTag(LibraryTags.ADD_TEST_GAME)) { Text(action.first) }
        }
    }
}

@Composable
private fun GameList(games: List<GameEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(LibraryTags.LIST),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(games, key = { it.folderName }) { game -> GameRow(game) }
    }
}

@Composable
private fun GameRow(game: GameEntry) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column {
            Text(game.title, style = MaterialTheme.typography.titleMedium)
            val minutes = (game.playTimeSeconds / 60).toInt()
            val subtitle = if (game.launchCount == 0) {
                platformLabel(game.platform)
            } else {
                platformLabel(game.platform) + " · " + pluralStringResource(R.plurals.library_minutes_played, minutes, minutes)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun platformLabel(platform: Platform): String = stringResource(
    when (platform) {
        Platform.GBA -> R.string.platform_gba
        Platform.NDS -> R.string.platform_nds
        Platform.RPGMAKER -> R.string.platform_rpgmaker
        Platform.RENPY -> R.string.platform_renpy
        Platform.RPGMAKER_MV -> R.string.platform_rpgmaker_mv
    },
)
