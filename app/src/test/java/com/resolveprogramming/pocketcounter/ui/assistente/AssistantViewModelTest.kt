package com.resolveprogramming.pocketcounter.ui.assistente

import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.AssistantRepository
import com.resolveprogramming.pocketcounter.domain.model.AssistantAnswer
import com.resolveprogramming.pocketcounter.domain.model.AssistantMessageStatus
import com.resolveprogramming.pocketcounter.domain.model.AssistantResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization tests for AssistantViewModel's applyResult reducer and state transitions.
 *
 * Each test wires a mocked AssistantRepository, sends one question via send(), advances
 * all pending coroutines (including the staged-loading delay timer), then asserts the
 * final state produced by applyResult.
 *
 * AssistantResult variants exercised:
 *   Success        → items updated to OK, remaining updated, busy cleared
 *   Validation     → optimistic item removed, input restored, inlineError set, busy cleared
 *   QuotaExhausted → item status becomes LIMIT, remaining forced to 0, busy cleared
 *   ServerError    → item status becomes ERROR, remaining unchanged, busy cleared
 *   Unavailable    → optimistic item removed, unavailable = true, busy cleared
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AssistantRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = AssistantViewModel(repository)

    private fun makeAnswer(
        markdown: String = "Resposta de teste.",
        elapsedMs: Long = 500L,
        remaining: Int = 4,
    ) = AssistantAnswer(markdown = markdown, elapsedMs = elapsedMs, remaining = remaining)

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty items, no inlineError, remaining null, not busy`() {
        val vm = makeViewModel()
        val state = vm.state.value

        assertTrue(state.items.isEmpty())
        assertNull(state.inlineError)
        assertNull(state.remaining)
        assertFalse(state.busy)
        assertFalse(state.unavailable)
    }

    @Test
    fun `canSend is false when input is blank`() {
        val vm = makeViewModel()

        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun `canSend is true when input has text and not busy and not exhausted`() {
        val vm = makeViewModel()
        vm.updateInput("Olá")

        assertTrue(vm.state.value.canSend)
    }

    // -------------------------------------------------------------------------
    // updateInput
    // -------------------------------------------------------------------------

    @Test
    fun `updateInput trims to ASSISTANT_MAX_CHARS characters`() {
        val vm = makeViewModel()
        val overlong = "x".repeat(ASSISTANT_MAX_CHARS + 100)

        vm.updateInput(overlong)

        assertEquals(ASSISTANT_MAX_CHARS, vm.state.value.input.length)
    }

    @Test
    fun `updateInput clears inlineError`() = runTest {
        coEvery { repository.ask(any()) } returns
            AssistantResult.Validation("erro anterior")

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        // inlineError was set by Validation; now updateInput should clear it
        vm.updateInput("nova pergunta")
        assertNull(vm.state.value.inlineError)
    }

    // -------------------------------------------------------------------------
    // AssistantResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success propagates remaining into state`() = runTest {
        val answer = makeAnswer(remaining = 3)
        coEvery { repository.ask("oi") } returns AssistantResult.Success(answer)

        val vm = makeViewModel()
        vm.updateInput("oi")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.remaining)
    }

    @Test
    fun `Success adds the answer to the matching item and sets status OK`() = runTest {
        val answer = makeAnswer(markdown = "**oi**", remaining = 2)
        coEvery { repository.ask("oi") } returns AssistantResult.Success(answer)

        val vm = makeViewModel()
        vm.updateInput("oi")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.state.value.items.single()
        assertEquals(AssistantMessageStatus.OK, item.status)
        assertNotNull(item.answer)
        assertEquals("**oi**", item.answer!!.markdown)
    }

    @Test
    fun `Success clears busy flag`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Success(makeAnswer())

        val vm = makeViewModel()
        vm.updateInput("oi")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `Success clears input after send`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Success(makeAnswer())

        val vm = makeViewModel()
        vm.updateInput("oi")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.state.value.input)
    }

    @Test
    fun `Success with remaining = 0 makes canSend false`() = runTest {
        coEvery { repository.ask(any()) } returns
            AssistantResult.Success(makeAnswer(remaining = 0))

        val vm = makeViewModel()
        vm.updateInput("última pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.remaining)
        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun `Success item carries the original question text`() = runTest {
        coEvery { repository.ask("quanto gastei?") } returns AssistantResult.Success(makeAnswer())

        val vm = makeViewModel()
        vm.updateInput("quanto gastei?")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("quanto gastei?", vm.state.value.items.single().question)
    }

    // -------------------------------------------------------------------------
    // AssistantResult.Validation
    // -------------------------------------------------------------------------

    @Test
    fun `Validation removes the optimistic item from the list`() = runTest {
        coEvery { repository.ask(any()) } returns
            AssistantResult.Validation("Não consegui entender. Reformule a pergunta (máx. 500 caracteres).")

        val vm = makeViewModel()
        vm.updateInput("???")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `Validation restores the question text into input`() = runTest {
        coEvery { repository.ask("texto ruim") } returns
            AssistantResult.Validation("Não consegui entender. Reformule a pergunta (máx. 500 caracteres).")

        val vm = makeViewModel()
        vm.updateInput("texto ruim")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("texto ruim", vm.state.value.input)
    }

    @Test
    fun `Validation sets inlineError to the message from the result`() = runTest {
        val errorMsg = "Não consegui entender. Reformule a pergunta (máx. 500 caracteres)."
        coEvery { repository.ask(any()) } returns AssistantResult.Validation(errorMsg)

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(errorMsg, vm.state.value.inlineError)
    }

    @Test
    fun `Validation clears busy flag`() = runTest {
        coEvery { repository.ask(any()) } returns
            AssistantResult.Validation("erro")

        val vm = makeViewModel()
        vm.updateInput("x")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `Validation leaves remaining unchanged (null when no prior success)`() = runTest {
        coEvery { repository.ask(any()) } returns
            AssistantResult.Validation("erro")

        val vm = makeViewModel()
        vm.updateInput("x")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.remaining)
    }

    // -------------------------------------------------------------------------
    // AssistantResult.QuotaExhausted
    // -------------------------------------------------------------------------

    @Test
    fun `QuotaExhausted forces remaining to 0`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.QuotaExhausted

        val vm = makeViewModel()
        vm.updateInput("mais uma")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.remaining)
    }

    @Test
    fun `QuotaExhausted sets item status to LIMIT`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.QuotaExhausted

        val vm = makeViewModel()
        vm.updateInput("mais uma")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AssistantMessageStatus.LIMIT, vm.state.value.items.single().status)
    }

    @Test
    fun `QuotaExhausted clears busy flag`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.QuotaExhausted

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `QuotaExhausted makes canSend false`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.QuotaExhausted

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        // remaining = 0 → canSend must be false even if input has text
        vm.updateInput("nova tentativa")
        assertFalse(vm.state.value.canSend)
    }

    // -------------------------------------------------------------------------
    // AssistantResult.ServerError
    // -------------------------------------------------------------------------

    @Test
    fun `ServerError sets item status to ERROR`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.ServerError

        val vm = makeViewModel()
        vm.updateInput("erro")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AssistantMessageStatus.ERROR, vm.state.value.items.single().status)
    }

    @Test
    fun `ServerError clears busy flag`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.ServerError

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `ServerError leaves remaining unchanged`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.ServerError

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.remaining)
    }

    @Test
    fun `ServerError does not set unavailable`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.ServerError

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.unavailable)
    }

    // -------------------------------------------------------------------------
    // AssistantResult.Unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `Unavailable removes the optimistic item`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Unavailable

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `Unavailable sets unavailable flag to true`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Unavailable

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.unavailable)
    }

    @Test
    fun `Unavailable clears busy flag`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Unavailable

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `Unavailable makes canSend false`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Unavailable

        val vm = makeViewModel()
        vm.updateInput("pergunta")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateInput("nova tentativa")
        assertFalse(vm.state.value.canSend)
    }

    // -------------------------------------------------------------------------
    // send() — no-op guards
    // -------------------------------------------------------------------------

    @Test
    fun `send does nothing when input is blank`() = runTest {
        val vm = makeViewModel()
        vm.updateInput("   ")

        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.busy)
    }

    // -------------------------------------------------------------------------
    // Staged loading phases (Turbine)
    // -------------------------------------------------------------------------

    @Test
    fun `send immediately sets busy and adds a LOADING item before result arrives`() = runTest {
        coEvery { repository.ask(any()) } returns AssistantResult.Success(makeAnswer())

        val vm = makeViewModel()
        vm.updateInput("oi")

        vm.state.test {
            val initial = awaitItem() // idle state
            assertFalse(initial.busy)

            vm.send()

            val loading = awaitItem()
            assertTrue(loading.busy)
            assertEquals(1, loading.items.size)
            assertEquals(AssistantMessageStatus.LOADING, loading.items.single().status)

            // Drain remaining emissions (phase updates + final result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry removes error item then re-asks with same question`() = runTest {
        // First call: error; second call (retry): success
        coEvery { repository.ask("retry question") } returnsMany listOf(
            AssistantResult.ServerError,
            AssistantResult.Success(makeAnswer(remaining = 1)),
        )

        val vm = makeViewModel()
        vm.updateInput("retry question")
        vm.send()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorItemId = vm.state.value.items.single().id
        assertEquals(AssistantMessageStatus.ERROR, vm.state.value.items.single().status)

        vm.retry(errorItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = vm.state.value
        assertEquals(1, finalState.items.size)
        assertEquals(AssistantMessageStatus.OK, finalState.items.single().status)
        assertEquals(1, finalState.remaining)
    }
}
