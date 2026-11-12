// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.importing

import android.net.Uri
import com.pocketverse.core.model.api.ContentHash
import com.pocketverse.core.model.api.EngineRef
import com.pocketverse.core.model.api.GameRecord
import com.pocketverse.core.model.api.HashMethod
import com.pocketverse.core.model.api.ImportInfo
import com.pocketverse.core.model.api.LaunchSpec
import com.pocketverse.core.model.api.Platform
import com.pocketverse.core.model.api.RomInfo
import com.pocketverse.core.model.api.SourceKind
import com.pocketverse.core.storage.api.Library
import com.pocketverse.feature.importing.api.ArchivePasswords
import com.pocketverse.feature.importing.api.Detection
import com.pocketverse.feature.importing.api.ImportError
import com.pocketverse.feature.importing.api.ImportPhase
import com.pocketverse.feature.importing.api.ImportProgress
import com.pocketverse.feature.importing.api.ImportResult
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportControllerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var library: Library
    private val request = ImportRequest.File(Uri.parse("content://test/rom"))

    @Before
    fun setUp() {
        library = Library(tmp.newFolder("lib"))
    }

    @After
    fun tearDown() = scope.cancel()

    private var counter = 0
    private fun imported(title: String): ImportResult.Imported {
        val prefix = "%08x".format(++counter)
        val record = GameRecord(
            id = prefix, title = title, platform = Platform.GBA, engine = EngineRef("e"), launch = LaunchSpec("x"),
            hash = ContentHash(prefix + "0".repeat(56), HashMethod.ROM),
            import = ImportInfo(Instant.EPOCH, "x", SourceKind.FILE, "0"), rom = RomInfo("AAAA", "T", 1),
        )
        return ImportResult.Imported(library.beginImport(record).commit(), listOf("a warning"))
    }

    private fun cartridge(path: String, title: String) = Detection.Cartridge(
        Platform.GBA, EngineRef("e"), path, RomInfo("AAAA", "T", 1), title,
    )

    private fun controller(changed: () -> Unit = {}, runner: ImportRunner) = ImportController(scope, runner, Dispatchers.IO, changed)

    private fun ImportController.await(predicate: (ImportUiState) -> Boolean): ImportUiState = runBlocking {
        withTimeout(10_000) { state.first(predicate) }
    }

    @Test
    fun `a successful import goes Idle then Running then Finished and tells the library to refresh`() {
        val changed = AtomicInteger()
        val c = controller({ changed.incrementAndGet() }) { _, _, _, _, _ -> imported("Zelda") }
        assertEquals(ImportUiState.Idle, c.state.value)

        c.start(request)
        val done = c.await { it is ImportUiState.Finished } as ImportUiState.Finished

        val outcome = done.outcome as ImportOutcome.Imported
        assertEquals("Zelda", outcome.title)
        assertEquals(listOf("a warning"), outcome.warnings)
        assertEquals(1, changed.get())
        c.dismiss()
        assertEquals(ImportUiState.Idle, c.state.value)
    }

    @Test
    fun `progress reported by the runner reaches the state`() {
        val release = CountDownLatch(1)
        val c = controller { _, _, _, _, onProgress ->
            onProgress(ImportProgress(ImportPhase.COPYING, 50, 100))
            release.await(10, TimeUnit.SECONDS)
            imported("Mario")
        }
        c.start(request)
        val running = c.await { it is ImportUiState.Running && it.progress != null } as ImportUiState.Running
        assertEquals(ImportProgress(ImportPhase.COPYING, 50, 100), running.progress)
        assertTrue(c.isBusy)
        release.countDown()
        c.await { it is ImportUiState.Finished }
        assertFalse(c.isBusy)
    }

    @Test
    fun `a second start while busy is ignored`() {
        val release = CountDownLatch(1)
        val runs = AtomicInteger()
        val c = controller { _, _, _, _, _ -> runs.incrementAndGet(); release.await(10, TimeUnit.SECONDS); imported("A") }
        c.start(request)
        c.await { it is ImportUiState.Running }
        c.start(request)
        release.countDown()
        c.await { it is ImportUiState.Finished }
        assertEquals(1, runs.get())
    }

    @Test
    fun `several cartridges ask for a choice and the chosen path is passed on`() {
        val preferred = mutableListOf<String?>()
        val c = controller { _, _, pref, _, _ ->
            preferred += pref
            if (pref == null) ImportResult.NeedsChoice(listOf(cartridge("a.gba", "A"), cartridge("b.gba", "B"))) else imported("B")
        }
        c.start(request)
        val choice = c.await { it is ImportUiState.NeedsChoice } as ImportUiState.NeedsChoice
        assertEquals(listOf("a.gba", "b.gba"), choice.candidates.map { it.romPath })

        c.choose("b.gba")
        val done = c.await { it is ImportUiState.Finished } as ImportUiState.Finished
        assertEquals(listOf(null, "b.gba"), preferred)
        assertEquals("B", (done.outcome as ImportOutcome.Imported).title)
    }

    @Test
    fun `a password prompt blocks the import until the user answers, and a wrong one asks again`() {
        val seen = mutableListOf<String?>()
        val c = controller { _, passwords, _, _, _ ->
            var attempt = 0
            var answer: CharArray?
            do {
                answer = passwords.password("locked.zip", ++attempt)
                seen += answer?.concatToString()
            } while (answer != null && String(answer) != "right")
            if (answer == null) ImportResult.Failed(ImportError.PasswordRequired("no")) else imported("Locked")
        }
        c.start(request)
        var prompt = c.await { it is ImportUiState.NeedsPassword } as ImportUiState.NeedsPassword
        assertEquals("locked.zip", prompt.archiveName)
        assertEquals(1, prompt.attempt)
        assertTrue(c.isBusy)

        c.submitPassword("wrong".toCharArray())
        prompt = c.await { it is ImportUiState.NeedsPassword && it.attempt == 2 } as ImportUiState.NeedsPassword
        c.submitPassword("right".toCharArray())
        c.await { it is ImportUiState.Finished }
        assertEquals(listOf("wrong", "right"), seen)
    }

    @Test
    fun `cancelling at a password prompt gives up`() {
        val c = controller { _, passwords, _, _, _ ->
            if (passwords.password("x.zip", 1) == null) ImportResult.Failed(ImportError.PasswordRequired("gave up")) else imported("X")
        }
        c.start(request)
        c.await { it is ImportUiState.NeedsPassword }
        c.cancel()
        val done = c.await { it is ImportUiState.Finished } as ImportUiState.Finished
        assertTrue((done.outcome as ImportOutcome.Failed).error is ImportError.PasswordRequired)
    }

    @Test
    fun `cancel is seen by a running import`() {
        val c = controller { _, _, _, shouldCancel, _ ->
            while (!shouldCancel()) Thread.sleep(5)
            ImportResult.Cancelled
        }
        c.start(request)
        c.await { it is ImportUiState.Running }
        c.cancel()
        assertEquals(ImportOutcome.Cancelled, (c.await { it is ImportUiState.Finished } as ImportUiState.Finished).outcome)
    }

    @Test
    fun `rejections failures and crashes become outcomes the screen can show`() {
        val rejection = Detection.Rejected(Detection.Reason.UNRECOGNISED, "nope")
        val results = ArrayDeque<() -> ImportResult>(
            listOf(
                { ImportResult.Rejected(rejection) },
                { ImportResult.Failed(ImportError.InsufficientSpace(10, 5)) },
                { throw IllegalStateException("boom") },
                { ImportResult.AlreadyInLibrary(imported("Existing").game) },
            ),
        )
        val c = controller { _, _, _, _, _ -> results.removeFirst()() }

        c.start(request)
        assertEquals(ImportOutcome.Rejected(Detection.Reason.UNRECOGNISED, "nope"), (c.await { it is ImportUiState.Finished } as ImportUiState.Finished).outcome)
        c.dismiss(); c.start(request)
        assertEquals(ImportOutcome.Failed(ImportError.InsufficientSpace(10, 5)), (c.await { it is ImportUiState.Finished } as ImportUiState.Finished).outcome)
        c.dismiss(); c.start(request)
        assertEquals(ImportOutcome.Failed(ImportError.Storage("boom")), (c.await { it is ImportUiState.Finished } as ImportUiState.Finished).outcome)
        c.dismiss(); c.start(request)
        assertEquals(ImportOutcome.AlreadyInLibrary("Existing"), (c.await { it is ImportUiState.Finished } as ImportUiState.Finished).outcome)
    }
}
