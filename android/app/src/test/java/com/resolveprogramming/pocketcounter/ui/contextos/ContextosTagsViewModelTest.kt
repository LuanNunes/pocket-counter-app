package com.resolveprogramming.pocketcounter.ui.contextos

import com.resolveprogramming.pocketcounter.data.repository.ContextInput
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ContextosTagsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tagRepository: TagRepository = mockk(relaxed = true)

    private val ctxFood = TagContext("ctx-food", "Alimentação", 0xFF_AA_00_00L)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(ctxFood))
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun loadedViewModel(): ContextosTagsViewModel {
        val vm = ContextosTagsViewModel(tagRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `saveTag failure keeps the sheet open with an inline error and no toast`() = runTest {
        val vm = loadedViewModel()
        val input = TagInput("Mercado", TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openAddTag("ctx-food")

        vm.saveTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TagFormMode.Add("ctx-food"), state.tagForm)
        assertEquals("Não foi possível salvar (nome já existe?)", state.tagFormError)
        assertNull(state.toastMessage)
    }

    @Test
    fun `saveTag success closes the sheet, toasts and clears the inline error`() = runTest {
        val vm = loadedViewModel()
        val input = TagInput("Mercado", TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openAddTag("ctx-food")
        vm.saveTag(input)
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { tagRepository.createTag(input) } returns
            Result.success(Tag("tag-new", "Mercado", TransactionType.EXPENSE, "ctx-food"))

        vm.saveTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.tagForm)
        assertNull(state.tagFormError)
        assertEquals("Tag salva", state.toastMessage)
        // Unlike the wizard — which must not reload, or it discards the draft — this screen must.
        coVerify(exactly = 2) { tagRepository.getAllContexts() }
    }

    @Test
    fun `clearTagFormError drops the error without closing the sheet`() = runTest {
        val vm = loadedViewModel()
        val input = TagInput("Mercado", TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openAddTag("ctx-food")
        vm.saveTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.clearTagFormError()

        assertNull(vm.state.value.tagFormError)
        assertNotNull(vm.state.value.tagForm)
    }

    @Test
    fun `saveContext failure keeps the sheet open with an inline error and no toast`() = runTest {
        val vm = loadedViewModel()
        val input = ContextInput("Casa", 0xFF_00_AA_00L)
        coEvery { tagRepository.createContext(input) } returns Result.failure(IOException("boom"))
        vm.openAddContext()

        vm.saveContext(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ContextFormMode.Add, state.contextForm)
        assertEquals("Não foi possível salvar (nome já existe?)", state.contextFormError)
        assertNull(state.toastMessage)
    }

    @Test
    fun `saveContext success closes the sheet, toasts and clears the inline error`() = runTest {
        val vm = loadedViewModel()
        val input = ContextInput("Casa", 0xFF_00_AA_00L)
        coEvery { tagRepository.createContext(input) } returns Result.failure(IOException("boom"))
        vm.openAddContext()
        vm.saveContext(input)
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { tagRepository.createContext(input) } returns
            Result.success(TagContext("ctx-home", "Casa", 0xFF_00_AA_00L))

        vm.saveContext(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.contextForm)
        assertNull(state.contextFormError)
        assertEquals("Contexto salvo", state.toastMessage)
        coVerify(exactly = 2) { tagRepository.getAllContexts() }
    }

    @Test
    fun `reopening a form drops the error left by the previous attempt`() = runTest {
        val vm = loadedViewModel()
        val input = TagInput("Mercado", TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openAddTag("ctx-food")
        vm.saveTag(input)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.closeTagForm()

        vm.openAddTag("ctx-food")

        assertNull(vm.state.value.tagFormError)
    }

    @Test
    fun `saveTag failure after the sheet was dismissed falls back to a toast`() = runTest {
        val vm = loadedViewModel()
        val input = TagInput("Mercado", TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openAddTag("ctx-food")
        vm.saveTag(input)

        vm.closeTagForm()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.tagFormError)
        assertEquals("Não foi possível salvar (nome já existe?)", state.toastMessage)
    }

    @Test
    fun `saveContext failure after the sheet was dismissed falls back to a toast`() = runTest {
        val vm = loadedViewModel()
        val input = ContextInput("Casa", 0xFF_00_AA_00L)
        coEvery { tagRepository.createContext(input) } returns Result.failure(IOException("boom"))
        vm.openAddContext()
        vm.saveContext(input)

        vm.closeContextForm()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.contextFormError)
        assertEquals("Não foi possível salvar (nome já existe?)", state.toastMessage)
    }
}
