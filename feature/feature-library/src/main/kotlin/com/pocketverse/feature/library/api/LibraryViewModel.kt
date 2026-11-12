// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketverse.core.database.api.LibraryIndex
import com.pocketverse.core.storage.api.Library
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the index in line with the library folders and exposes what the screen shows.
 *
 * @param addTestGame a debug-only way to put a game in the library before the real import exists
 * (feature-import, Phase 2); null hides the button.
 */
class LibraryViewModel(
    private val index: LibraryIndex,
    private val library: Library,
    private val addTestGame: (suspend () -> Unit)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _state.asStateFlow()

    val canAddTestGame: Boolean get() = addTestGame != null

    init {
        viewModelScope.launch {
            index.games().collect { games -> _state.update { it.copy(games = games) } }
        }
        refresh()
    }

    /** Re-reads the folders. Safe to call at any time (pull to refresh, after an import). */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val report = index.sync(library)
                _state.update { it.copy(loading = false, unreadableFolders = report.problems.size, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e::class.java.simpleName) }
            }
        }
    }

    fun onAddTestGame() {
        val add = addTestGame ?: return
        viewModelScope.launch {
            try {
                add()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.java.simpleName) }
                return@launch
            }
            refresh()
        }
    }
}
