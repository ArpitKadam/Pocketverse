// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import android.net.Uri
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.ArchivePasswords
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportProgress
import com.pocketverse.feature.importing.api.ImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** What the user picked. */
sealed interface ImportRequest {
    data class File(val uri: Uri) : ImportRequest
    data class Folder(val uri: Uri) : ImportRequest
}

/** Does the work of one import, blocking. The Android implementation is [AndroidImportRunner]; tests use a fake. */
fun interface ImportRunner {
    fun run(
        request: ImportRequest,
        passwords: ArchivePasswords,
        preferredRom: String?,
        shouldCancel: () -> Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult
}

/** A cartridge the user can pick when a source holds several. */
data class ChoiceItem(val romPath: String, val title: String, val platform: Platform)

sealed interface ImportOutcome {
    data class Imported(val title: String, val warnings: List<String>) : ImportOutcome
    data class AlreadyInLibrary(val title: String) : ImportOutcome
    data class Rejected(val reason: Detection.Reason, val message: String) : ImportOutcome
    data class Failed(val error: ImportError) : ImportOutcome
    data object Cancelled : ImportOutcome
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val progress: ImportProgress?) : ImportUiState
    data class NeedsChoice(val candidates: List<ChoiceItem>) : ImportUiState

    /** [attempt] is 1 for the first ask; a higher number means the previous password was wrong. */
    data class NeedsPassword(val archiveName: String, val attempt: Int) : ImportUiState
    data class Finished(val outcome: ImportOutcome) : ImportUiState
}

/**
 * Drives one import at a time and tells the screen what to show. Pure Kotlin apart from [Uri] in the request,
 * so it is tested with a fake [ImportRunner].
 *
 * @param onLibraryChanged called after a game was added, so the library list can refresh.
 */
class ImportController(
    private val scope: CoroutineScope,
    private val runner: ImportRunner,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onLibraryChanged: () -> Unit = {},
) {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    @Volatile private var cancelRequested = false
    private var lastRequest: ImportRequest? = null
    private var job: Job? = null
    @Volatile private var pendingPassword: CompletableDeferred<CharArray?>? = null

    val isBusy: Boolean get() = _state.value is ImportUiState.Running || _state.value is ImportUiState.NeedsPassword

    /** Starts importing [request]; ignored while another import is running. */
    fun start(request: ImportRequest) {
        if (isBusy) return
        lastRequest = request
        launch(request, null)
    }

    /** The user chose one of the cartridges offered by [ImportUiState.NeedsChoice]. */
    fun choose(romPath: String) {
        val request = lastRequest ?: return
        if (_state.value !is ImportUiState.NeedsChoice) return
        launch(request, romPath)
    }

    fun submitPassword(password: CharArray) {
        pendingPassword?.complete(password)
    }

    /** Stops a running import, or gives up at a password or choice prompt. */
    fun cancel() {
        cancelRequested = true
        pendingPassword?.complete(null)
        if (_state.value is ImportUiState.NeedsChoice) _state.value = ImportUiState.Idle
    }

    /** Closes a result or a choice prompt. */
    fun dismiss() {
        if (!isBusy) _state.value = ImportUiState.Idle
    }

    private fun launch(request: ImportRequest, preferredRom: String?) {
        cancelRequested = false
        _state.value = ImportUiState.Running(null)
        job = scope.launch {
            val self = job
            val mapped: Mapped = try {
                withContext(io) {
                    val result = runner.run(
                        request = request,
                        passwords = ArchivePasswords { name, attempt -> askPassword(name, attempt) },
                        preferredRom = preferredRom,
                        shouldCancel = { cancelRequested || self?.isActive == false },
                        onProgress = { p -> if (_state.value is ImportUiState.Running) _state.value = ImportUiState.Running(p) },
                    )
                    map(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Mapped.Done(ImportOutcome.Failed(ImportError.Storage(e.message ?: e::class.java.simpleName)))
            }
            when (mapped) {
                is Mapped.Choice -> _state.value = ImportUiState.NeedsChoice(mapped.items)
                is Mapped.Done -> {
                    _state.value = ImportUiState.Finished(mapped.outcome)
                    if (mapped.outcome is ImportOutcome.Imported) onLibraryChanged()
                }
            }
        }
    }

    // The runner works on an IO thread and needs the password now: block that thread until the user answers.
    private fun askPassword(archiveName: String, attempt: Int): CharArray? {
        if (cancelRequested) return null
        val answer = CompletableDeferred<CharArray?>()
        pendingPassword = answer
        _state.value = ImportUiState.NeedsPassword(archiveName, attempt)
        val password = runBlocking { answer.await() }
        pendingPassword = null
        if (password != null) _state.value = ImportUiState.Running(null)
        return password
    }

    private sealed interface Mapped {
        data class Done(val outcome: ImportOutcome) : Mapped
        data class Choice(val items: List<ChoiceItem>) : Mapped
    }

    private fun map(result: ImportResult): Mapped = when (result) {
        is ImportResult.Imported -> Mapped.Done(ImportOutcome.Imported(result.game.readMeta().title, result.warnings))
        is ImportResult.AlreadyInLibrary -> Mapped.Done(ImportOutcome.AlreadyInLibrary(result.existing.readMeta().title))
        is ImportResult.Rejected -> Mapped.Done(ImportOutcome.Rejected(result.rejection.reason, result.rejection.message))
        is ImportResult.NeedsChoice -> Mapped.Choice(result.candidates.map { ChoiceItem(it.romPath, it.suggestedTitle, it.platform) })
        ImportResult.Cancelled -> Mapped.Done(ImportOutcome.Cancelled)
        is ImportResult.Failed -> Mapped.Done(ImportOutcome.Failed(result.error))
    }
}
