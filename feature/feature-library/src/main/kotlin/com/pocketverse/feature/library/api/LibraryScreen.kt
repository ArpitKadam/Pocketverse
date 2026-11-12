// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    const val IMPORT = "library_import"
    const val IMPORT_FILE = "library_import_file"
    const val IMPORT_FOLDER = "library_import_folder"
    const val DELETE = "library_delete"
    const val DELETE_CONFIRM = "library_delete_confirm"
    const val DELETE_CANCEL = "library_delete_cancel"
}

/**
 * The library: a loading indicator, an error, an empty-state, or the list of games, with an Import button and a
 * long-press menu on each game. Stateless; the caller owns the state and every action. Mode-specific looks
 * (GBA, NDS, PC) are applied later through the theme API.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAddTestGame: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onImportFile: (() -> Unit)? = null,
    onImportFolder: (() -> Unit)? = null,
    onDeleteGame: ((GameEntry) -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                    else -> GameList(state.games, onDeleteGame)
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
        if (onImportFile != null && onImportFolder != null) {
            ImportButton(onImportFile, onImportFolder, Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
    }
}

@Composable
private fun ImportButton(onImportFile: () -> Unit, onImportFolder: () -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ExtendedFloatingActionButton(onClick = { open = true }, modifier = Modifier.testTag(LibraryTags.IMPORT)) {
            Text(stringResource(R.string.library_import))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_file)) },
                onClick = { open = false; onImportFile() },
                modifier = Modifier.testTag(LibraryTags.IMPORT_FILE),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_import_folder)) },
                onClick = { open = false; onImportFolder() },
                modifier = Modifier.testTag(LibraryTags.IMPORT_FOLDER),
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
private fun GameList(games: List<GameEntry>, onDelete: ((GameEntry) -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(LibraryTags.LIST),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp), // room for the import button
    ) {
        items(games, key = { it.folderName }) { game -> GameRow(game, onDelete) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameRow(game: GameEntry, onDelete: ((GameEntry) -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { if (onDelete != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(game.title, style = MaterialTheme.typography.titleMedium)
            val minutes = (game.playTimeSeconds / 60).toInt()
            val subtitle = if (game.launchCount == 0) {
                platformLabel(game.platform)
            } else {
                platformLabel(game.platform) + " · " + pluralStringResource(R.plurals.library_minutes_played, minutes, minutes)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_game_menu_delete)) },
                onClick = { menu = false; confirm = true },
                modifier = Modifier.testTag(LibraryTags.DELETE),
            )
        }
    }
    if (confirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_body, game.title)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete(game) }, modifier = Modifier.testTag(LibraryTags.DELETE_CONFIRM)) {
                    Text(stringResource(R.string.library_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.testTag(LibraryTags.DELETE_CANCEL)) {
                    Text(stringResource(R.string.library_delete_cancel))
                }
            },
        )
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
