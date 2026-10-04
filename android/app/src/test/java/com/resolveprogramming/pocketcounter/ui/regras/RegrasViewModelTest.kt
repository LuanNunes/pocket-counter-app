package com.resolveprogramming.pocketcounter.ui.regras

import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegrasViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleRepository: ClassificationRuleRepository = mockk(relaxed = true)
    private val tagRepository: TagRepository = mockk(relaxed = true)

    private val ifoodRule = ClassificationRule(
        id = "rule-1",
        pattern = "Ifood",
        idTag = "tag-1",
        active = true,
        appliedCount = 3,
        action = RuleAction.SUGGEST,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { ruleRepository.getAll() } returns Result.success(listOf(ifoodRule))
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun makeViewModel() = RegrasViewModel(ruleRepository, tagRepository)

    @Test
    fun `saveEdit persists the edited pattern, tag and active flag onto the edited rule`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Saved)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood Delivery", idTag = "tag-2", active = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<ClassificationRule>()
        coVerify { ruleRepository.update(capture(slot)) }
        assertEquals("rule-1", slot.captured.id)
        assertEquals("Ifood Delivery", slot.captured.pattern)
        assertEquals("tag-2", slot.captured.idTag)
        assertEquals(false, slot.captured.active)
        assertEquals(RuleAction.SUGGEST, slot.captured.action)
    }

    @Test
    fun `requestDelete labels the confirmation with the rule's pattern`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete("rule-1")

        assertEquals("Ifood", vm.state.value.confirmDelete?.patternLabel)
    }

    @Test
    fun `saveEdit success closes the sheet and reloads`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Saved)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.editTarget)
        assertFalse(vm.state.value.savingEdit)
        assertNull(vm.state.value.editError)
        assertEquals("Regra atualizada", vm.state.value.toastMessage)
        // getAll runs once on init + once after a successful update.
        coVerify(exactly = 2) { ruleRepository.getAll() }
    }

    // -------------------------------------------------------------------------
    // editError — the 409 belongs under the pattern field, not behind the scrim
    // -------------------------------------------------------------------------

    @Test
    fun `saveEdit on a duplicate keeps the sheet open with an inline error and does not reload`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Duplicate)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("rule-1", vm.state.value.editTarget?.id)
        assertFalse(vm.state.value.savingEdit)
        assertEquals(DUPLICATE_PATTERN_ERROR, vm.state.value.editError)
        assertNull(vm.state.value.toastMessage)
        coVerify(exactly = 1) { ruleRepository.getAll() }
    }

    @Test
    fun `a duplicate reported after the sheet was dismissed falls back to the toast`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Duplicate)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        vm.cancelEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.editError)
        assertEquals(DUPLICATE_PATTERN_ERROR, vm.state.value.toastMessage)
    }

    @Test
    fun `clearEditError clears the inline error`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Duplicate)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.clearEditError()

        assertNull(vm.state.value.editError)
    }

    @Test
    fun `openEdit clears an error left by a previous attempt`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Duplicate)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openEdit("rule-1")

        assertNull(vm.state.value.editError)
    }

    @Test
    fun `a rejection shows the server's own reason, not a guess`() = runTest {
        val serverReason = "A tag precisa ser de despesa."
        coEvery { ruleRepository.update(any()) } returns
            Result.success(RuleWriteOutcome.Rejected(serverReason))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(serverReason, vm.state.value.editError)
        assertEquals("rule-1", vm.state.value.editTarget?.id)
    }

    @Test
    fun `a rejection with no reason falls back to the generic sentence`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(RuleWriteOutcome.Rejected(null))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UPDATE_FAILED, vm.state.value.editError)
    }

    @Test
    fun `saveEdit failure keeps the sheet open and reports inline, where the scrim cannot hide it`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("rule-1", vm.state.value.editTarget?.id)
        assertFalse(vm.state.value.savingEdit)
        assertEquals(UPDATE_FAILED, vm.state.value.editError)
        assertNull(vm.state.value.toastMessage)
    }

    @Test
    fun `a failure reported after the sheet was dismissed falls back to the toast`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(pattern = "Ifood", idTag = "tag-1", active = true)
        vm.cancelEdit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.editError)
        assertEquals(UPDATE_FAILED, vm.state.value.toastMessage)
    }
}
