// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportProgress
import com.pocketverse.feature.library.R

object ImportTags {
    const val PROGRESS = "import_progress"
    const val CANCEL = "import_cancel"
    const val CHOICE = "import_choice"
    const val PASSWORD_FIELD = "import_password"
    const val PASSWORD_OK = "import_password_ok"
    const val RESULT = "import_result"
    const val RESULT_OK = "import_result_ok"
}

/** Shows whatever the import needs from the user right now: progress, a choice, a password, or the result. */
@Composable
fun ImportDialogs(
    state: ImportUiState,
    onCancel: () -> Unit,
    onChoose: (String) -> Unit,
    onPassword: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        ImportUiState.Idle -> Unit
        is ImportUiState.Running -> RunningDialog(state.progress, onCancel)
        is ImportUiState.NeedsChoice -> ChoiceDialog(state.candidates, onChoose, onCancel)
        is ImportUiState.NeedsPassword -> PasswordDialog(state, onPassword, onCancel)
        is ImportUiState.Finished -> ResultDialog(state.outcome, onDismiss)
    }
}

@Composable
private fun RunningDialog(progress: ImportProgress?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(ImportTags.PROGRESS),
        title = { Text(stringResource(R.string.import_running_title)) },
        text = {
            Column {
                Text(phaseText(progress))
                if (progress != null && progress.bytesTotal > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.bytesDone.toFloat() / progress.bytesTotal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    val context = LocalContext.current
                    Text(
                        stringResource(R.string.import_bytes_of, Formatter.formatShortFileSize(context, progress.bytesDone), Formatter.formatShortFileSize(context, progress.bytesTotal)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag(ImportTags.CANCEL)) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun phaseText(progress: ImportProgress?): String = when (progress?.phase) {
    null, ImportPhase.CHECKING_SPACE -> stringResource(R.string.import_phase_checking)
    ImportPhase.READING_SOURCE -> stringResource(R.string.import_phase_reading)
    ImportPhase.EXTRACTING -> stringResource(R.string.import_phase_extracting)
    ImportPhase.COPYING -> stringResource(R.string.import_phase_copying)
    ImportPhase.FINALIZING -> stringResource(R.string.import_phase_finalizing)
}

@Composable
private fun ChoiceDialog(items: List<ChoiceItem>, onChoose: (String) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(ImportTags.CHOICE),
        title = { Text(stringResource(R.string.import_choice_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_choice_hint))
                items.forEach { item ->
                    Column(Modifier.fillMaxWidth().clickable { onChoose(item.romPath) }.padding(vertical = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.romPath + " · " + platformName(item.platform), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun PasswordDialog(state: ImportUiState.NeedsPassword, onPassword: (CharArray) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_password_hint, state.archiveName))
                if (state.attempt > 1) Text(stringResource(R.string.import_password_wrong), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(ImportTags.PASSWORD_FIELD),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPassword(text.toCharArray()) }, modifier = Modifier.testTag(ImportTags.PASSWORD_OK)) { Text(stringResource(R.string.import_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel)) } },
    )
}

@Composable
private fun ResultDialog(outcome: ImportOutcome, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (title, body) = when (outcome) {
        is ImportOutcome.Imported -> stringResource(R.string.import_done_title) to buildString {
            append(stringResource(R.string.import_done_body, outcome.title))
            outcome.warnings.forEach { append("\n\n").append(it) }
        }
        is ImportOutcome.AlreadyInLibrary -> stringResource(R.string.import_already_title) to stringResource(R.string.import_already_body, outcome.title)
        is ImportOutcome.Rejected -> stringResource(R.string.import_rejected_title) to rejectionText(outcome.reason)
        is ImportOutcome.Failed -> stringResource(R.string.import_failed_title) to errorText(outcome.error) { Formatter.formatShortFileSize(context, it) }
        ImportOutcome.Cancelled -> stringResource(R.string.import_cancelled_title) to stringResource(R.string.import_cancelled_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ImportTags.RESULT),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag(ImportTags.RESULT_OK)) { Text(stringResource(R.string.import_ok)) } },
    )
}

@Composable
private fun rejectionText(reason: Detection.Reason): String = stringResource(
    when (reason) {
        Detection.Reason.EMPTY -> R.string.import_reject_empty
        Detection.Reason.ARCHIVE_NOT_OPENED -> R.string.import_reject_archive
        Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM -> R.string.import_reject_windows
        Detection.Reason.UNRECOGNISED -> R.string.import_reject_unrecognised
        Detection.Reason.CONFLICTING_ENGINES -> R.string.import_reject_conflict
    },
)

@Composable
private fun errorText(error: ImportError, size: (Long) -> String): String = when (error) {
    is ImportError.InsufficientSpace -> stringResource(R.string.import_error_space, size(error.requiredBytes), size(error.availableBytes))
    is ImportError.SourceUnreadable -> stringResource(R.string.import_error_unreadable, error.message)
    is ImportError.SourceChanged -> stringResource(R.string.import_error_changed)
    is ImportError.PasswordRequired -> stringResource(R.string.import_error_password)
    is ImportError.Storage -> stringResource(R.string.import_error_storage, error.message)
    is ImportError.LimitExceeded -> stringResource(R.string.import_error_limit, error.what)
    is ImportError.UnsupportedArchive -> stringResource(R.string.import_error_archive, error.kind)
}

@Composable
internal fun platformName(platform: Platform): String = stringResource(
    when (platform) {
        Platform.GBA -> R.string.platform_gba
        Platform.NDS -> R.string.platform_nds
        Platform.RPGMAKER -> R.string.platform_rpgmaker
        Platform.RENPY -> R.string.platform_renpy
        Platform.RPGMAKER_MV -> R.string.platform_rpgmaker_mv
    },
)
