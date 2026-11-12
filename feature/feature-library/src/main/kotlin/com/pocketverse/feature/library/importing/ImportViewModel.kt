// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/** Holds the [ImportController] across rotation. An import in progress keeps running while the screen turns. */
class ImportViewModel(runner: ImportRunner, onLibraryChanged: () -> Unit) : ViewModel() {
    val controller = ImportController(viewModelScope, runner, onLibraryChanged = onLibraryChanged)

    override fun onCleared() {
        controller.cancel()
    }
}
