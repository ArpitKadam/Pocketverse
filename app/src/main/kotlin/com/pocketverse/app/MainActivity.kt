// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketverse.feature.library.api.LibraryScreen
import com.pocketverse.feature.library.api.LibraryUiState
import com.pocketverse.feature.library.api.LibraryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PocketverseApp
        val library = app.library
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        val viewModel = library.getOrNull()?.let { lib ->
            val factory = viewModelFactory {
                initializer {
                    val stub: (suspend () -> Unit)? = if (isDebuggable) ({ DebugImportStub.add(lib) }) else null
                    LibraryViewModel(app.index, lib, stub)
                }
            }
            ViewModelProvider(this, factory)[LibraryViewModel::class.java]
        }

        setContent {
            MaterialTheme {
                Surface {
                    if (viewModel == null) {
                        LibraryScreen(
                            state = LibraryUiState(loading = false, error = library.exceptionOrNull()?.message),
                            onAddTestGame = null,
                        )
                    } else {
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        LibraryScreen(state, onAddTestGame = if (viewModel.canAddTestGame) viewModel::onAddTestGame else null)
                    }
                }
            }
        }
    }
}
