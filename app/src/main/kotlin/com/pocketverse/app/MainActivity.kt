// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.app

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.pocketverse.feature.library.importing.AndroidImportRunner
import com.pocketverse.feature.library.importing.ImportDialogs
import com.pocketverse.feature.library.importing.ImportRequest
import com.pocketverse.feature.library.importing.ImportViewModel

class MainActivity : ComponentActivity() {
    private var importViewModel: ImportViewModel? = null

    // The system pickers. Android returns a content URI we may read once; the import copies from it right away.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importViewModel?.controller?.start(ImportRequest.File(it)) }
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { importViewModel?.controller?.start(ImportRequest.Folder(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PocketverseApp
        val library = app.library
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"

        val libraryViewModel = library.getOrNull()?.let { lib ->
            val factory = viewModelFactory {
                initializer {
                    val stub: (suspend () -> Unit)? = if (isDebuggable) ({ DebugImportStub.add(lib) }) else null
                    LibraryViewModel(app.index, lib, stub)
                }
            }
            ViewModelProvider(this, factory)[LibraryViewModel::class.java]
        }
        val importVm = library.getOrNull()?.let { lib ->
            val factory = viewModelFactory {
                initializer { ImportViewModel(AndroidImportRunner(app, lib, appVersion)) { libraryViewModel?.refresh() } }
            }
            ViewModelProvider(this, factory)[ImportViewModel::class.java]
        }
        importViewModel = importVm

        setContent {
            MaterialTheme {
                Surface {
                    if (libraryViewModel == null || importVm == null) {
                        LibraryScreen(
                            state = LibraryUiState(loading = false, error = library.exceptionOrNull()?.message),
                            onAddTestGame = null,
                        )
                    } else {
                        val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
                        val importState by importVm.controller.state.collectAsStateWithLifecycle()
                        LibraryScreen(
                            state = state,
                            onAddTestGame = if (libraryViewModel.canAddTestGame) libraryViewModel::onAddTestGame else null,
                            onImportFile = { pickFile.launch(arrayOf("*/*")) },
                            onImportFolder = { pickFolder.launch(null) },
                            onDeleteGame = libraryViewModel::deleteGame,
                        )
                        ImportDialogs(
                            state = importState,
                            onCancel = importVm.controller::cancel,
                            onChoose = importVm.controller::choose,
                            onPassword = importVm.controller::submitPassword,
                            onDismiss = importVm.controller::dismiss,
                        )
                    }
                }
            }
        }
    }
}
