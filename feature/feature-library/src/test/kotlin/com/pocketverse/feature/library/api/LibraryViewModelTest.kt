// SPDX-License-Identifier: GPL-3.0-only
package com.pocketverse.feature.library.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocketverse.core.database.api.LibraryIndex
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
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var library: Library
    private lateinit var index: LibraryIndex

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        library = Library(tmp.newFolder("lib"))
        index = LibraryIndex.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        index.close()
        Dispatchers.resetMain()
    }

    private fun addGame(title: String, prefix: String) {
        val record = GameRecord(
            id = prefix,
            title = title,
            platform = Platform.GBA,
            engine = EngineRef("libretro-mgba"),
            launch = LaunchSpec("x.gba"),
            hash = ContentHash(prefix + "0".repeat(56), HashMethod.ROM),
            import = ImportInfo(Instant.EPOCH, "x.gba", SourceKind.FILE, "0"),
            rom = RomInfo("AAAA", "T", 1),
        )
        library.beginImport(record).commit()
    }

    private fun LibraryViewModel.awaitState(predicate: (LibraryUiState) -> Boolean) = runBlocking {
        withTimeout(10_000) { uiState.first(predicate) }
    }

    @Test
    fun `an empty library ends loading with no games and no error`() {
        val vm = LibraryViewModel(index, library)
        val state = vm.awaitState { !it.loading }
        assertTrue(state.games.isEmpty())
        assertEquals(null, state.error)
        assertFalse(vm.canAddTestGame)
    }

    @Test
    fun `existing games show up after the first sync`() {
        addGame("Apple", "aaaaaaaa")
        val vm = LibraryViewModel(index, library)
        val state = vm.awaitState { !it.loading && it.games.isNotEmpty() }
        assertEquals(listOf("Apple"), state.games.map { it.title })
    }

    @Test
    fun `the test game callback runs and the list refreshes`() {
        var added = 0
        val vm = LibraryViewModel(index, library) { addGame("Test", "bbbbbbbb"); added++ }
        assertTrue(vm.canAddTestGame)
        vm.awaitState { !it.loading }
        vm.onAddTestGame()
        val state = vm.awaitState { it.games.size == 1 }
        assertEquals(1, added)
        assertEquals("Test", state.games.single().title)
    }

    @Test
    fun `a failing test game callback is shown as an error, not thrown`() {
        val vm = LibraryViewModel(index, library) { error("boom") }
        vm.awaitState { !it.loading }
        vm.onAddTestGame()
        val state = vm.awaitState { it.error != null }
        assertNotNull(state.error)
        assertEquals("boom", state.error)
    }

    @Test
    fun `unreadable folders are counted`() {
        addGame("Apple", "aaaaaaaa")
        java.io.File(library.root, "Broken [deadbeef]").mkdir()
        val vm = LibraryViewModel(index, library)
        val state = vm.awaitState { !it.loading }
        assertEquals(1, state.unreadableFolders)
    }
}
