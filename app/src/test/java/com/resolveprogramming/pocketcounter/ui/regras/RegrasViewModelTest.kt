package com.resolveprogramming.pocketcounter.ui.regras

import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
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
    private val cardRepository: CardRepository = mockk(relaxed = true)
    private val tagRepository: TagRepository = mockk(relaxed = true)

    private val ifoodRule = ClassificationRule(
        id = "rule-1",
        patterns = listOf("Ifood"),
        matchType = "CONTAINS",
        active = true,
        appliedCount = 3,
        transactionType = TransactionType.EXPENSE,
        paymentMethod = null,
        cardId = null,
        tags = emptyList(),
        action = RuleAction.SUGGEST,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { ruleRepository.getAll() } returns Result.success(listOf(ifoodRule))
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun makeViewModel() = RegrasViewModel(ruleRepository, cardRepository, tagRepository)

    @Test
    fun `saveEdit persists the chosen method and card onto the edited rule`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(PaymentMethod.CREDIT, cardId = "card-9")
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<ClassificationRule>()
        coVerify { ruleRepository.update(capture(slot)) }
        assertEquals("rule-1", slot.captured.id)
        assertEquals(PaymentMethod.CREDIT, slot.captured.paymentMethod)
        assertEquals("card-9", slot.captured.cardId)
        // Patterns/type/tags must survive the edit so the update doesn't wipe them.
        assertEquals(listOf("Ifood"), slot.captured.patterns)
        assertEquals(TransactionType.EXPENSE, slot.captured.transactionType)
    }

    @Test
    fun `saveEdit drops the card when the method is not CREDIT`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(PaymentMethod.PIX, cardId = "card-9")
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<ClassificationRule>()
        coVerify { ruleRepository.update(capture(slot)) }
        assertEquals(PaymentMethod.PIX, slot.captured.paymentMethod)
        assertNull(slot.captured.cardId)
    }

    @Test
    fun `saveEdit success closes the sheet and reloads`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(PaymentMethod.CREDIT, cardId = "card-9")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.editTarget)
        assertFalse(vm.state.value.savingEdit)
        assertEquals("Regra atualizada", vm.state.value.toastMessage)
        // getAll runs once on init + once after a successful update.
        coVerify(exactly = 2) { ruleRepository.getAll() }
    }

    @Test
    fun `saveEdit failure keeps the sheet open and toasts`() = runTest {
        coEvery { ruleRepository.update(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit("rule-1")
        vm.saveEdit(PaymentMethod.CREDIT, cardId = "card-9")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("rule-1", vm.state.value.editTarget?.id)
        assertFalse(vm.state.value.savingEdit)
        assertEquals("Não foi possível atualizar", vm.state.value.toastMessage)
    }
}
