// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pocketverse.core.model.api.Platform
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: ImportUiState,
        onCancel: () -> Unit = {},
        onChoose: (String) -> Unit = {},
        onPassword: (CharArray) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = compose.setContent { ImportDialogs(state, onCancel, onChoose, onPassword, onDismiss) }

    @Test
    fun `idle shows nothing`() {
        show(ImportUiState.Idle)
        compose.onNodeWithTag(ImportTags.PROGRESS).assertDoesNotExist()
        compose.onNodeWithTag(ImportTags.RESULT).assertDoesNotExist()
    }

    @Test
    fun `running shows a cancel button that calls back`() {
        var cancelled = 0
        show(ImportUiState.Running(ImportProgress(ImportPhase.EXTRACTING, 10, 100)), onCancel = { cancelled++ })
        compose.onNodeWithTag(ImportTags.PROGRESS).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.CANCEL).performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `choice lists the candidates and reports the tapped one`() {
        var chosen: String? = null
        show(
            ImportUiState.NeedsChoice(listOf(ChoiceItem("a.gba", "Alpha", Platform.GBA), ChoiceItem("b.nds", "Beta", Platform.NDS))),
            onChoose = { chosen = it },
        )
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Beta").performClick()
        assertEquals("b.nds", chosen)
    }

    @Test
    fun `password dialog sends what was typed`() {
        var sent: String? = null
        show(ImportUiState.NeedsPassword("locked.zip", 2), onPassword = { sent = it.concatToString() })
        compose.onNodeWithTag(ImportTags.PASSWORD_FIELD).performTextInput("secret")
        compose.onNodeWithTag(ImportTags.PASSWORD_OK).performClick()
        assertEquals("secret", sent)
    }

    @Test
    fun `a result shows warnings and dismisses`() {
        var dismissed = 0
        show(ImportUiState.Finished(ImportOutcome.Imported("Zelda", listOf("careful"))), onDismiss = { dismissed++ })
        compose.onNodeWithText("careful", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.RESULT_OK).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a rejection renders`() {
        show(ImportUiState.Finished(ImportOutcome.Rejected(Detection.Reason.UNSUPPORTED_WINDOWS_PROGRAM, "x")))
        compose.onNodeWithTag(ImportTags.RESULT).assertIsDisplayed()
    }

    @Test
    fun `a failure renders its error`() {
        show(ImportUiState.Finished(ImportOutcome.Failed(ImportError.Storage("disk exploded"))))
        compose.onNodeWithText("disk exploded", substring = true).assertIsDisplayed()
    }
}
