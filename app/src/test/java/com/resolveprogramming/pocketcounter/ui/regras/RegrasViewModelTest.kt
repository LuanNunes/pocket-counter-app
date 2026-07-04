package com.resolveprogramming.pocketcounter.ui.regras

import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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

    @Test
    fun `applyDedupe updates canonicals before deleting duplicates`() = runTest {
        val tagA = Tag(id = "tag-a", name = "a", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val tagB = Tag(id = "tag-b", name = "b", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val canonical = ifoodRule.copy(id = "rule-1", patterns = listOf("Ifood"), tags = listOf(tagA))
        val duplicate = ifoodRule.copy(id = "rule-2", patterns = listOf("Rappi"), tags = listOf(tagB))
        coEvery { ruleRepository.getAll() } returns Result.success(listOf(canonical, duplicate))
        coEvery { ruleRepository.update(any()) } returns Result.success(Unit)
        coEvery { ruleRepository.delete(any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.previewDedupe()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.dedupePreview?.mergedRules)
        assertEquals(1, vm.state.value.dedupePreview?.deletedRules)

        vm.applyDedupe()
        testDispatcher.scheduler.advanceUntilIdle()

        val updateSlot = slot<ClassificationRule>()
        coVerifyOrder {
            ruleRepository.update(capture(updateSlot))
            ruleRepository.delete("rule-2")
        }
        assertEquals("rule-1", updateSlot.captured.id)
        assertEquals(listOf("Ifood", "Rappi"), updateSlot.captured.patterns)
        assertNull(vm.state.value.dedupePreview)
        assertFalse(vm.state.value.isMerging)
        assertEquals("Regras mescladas", vm.state.value.toastMessage)
    }

    @Test
    fun `applyDedupe withholds deletes when a canonical update fails`() = runTest {
        val tagA = Tag(id = "tag-a", name = "a", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val tagB = Tag(id = "tag-b", name = "b", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val canonical = ifoodRule.copy(id = "rule-1", patterns = listOf("Ifood"), tags = listOf(tagA))
        val duplicate = ifoodRule.copy(id = "rule-2", patterns = listOf("Rappi"), tags = listOf(tagB))
        coEvery { ruleRepository.getAll() } returns Result.success(listOf(canonical, duplicate))
        coEvery { ruleRepository.update(any()) } returns Result.failure(RuntimeException("boom"))
        coEvery { ruleRepository.delete(any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.previewDedupe()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.applyDedupe()
        testDispatcher.scheduler.advanceUntilIdle()

        // The update failed, so 'rule-2' (whose "Rappi" pattern wasn't absorbed) must NOT be deleted.
        coVerify(exactly = 0) { ruleRepository.delete(any()) }
        assertEquals("Não foi possível mesclar todas as regras", vm.state.value.toastMessage)
        assertFalse(vm.state.value.isMerging)
    }

    @Test
    fun `previewDedupe with no duplicates reports the empty case`() = runTest {
        // setUp already returns a single, ungroupable rule → nothing to merge.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.previewDedupe()
        testDispatcher.scheduler.advanceUntilIdle()

        val preview = vm.state.value.dedupePreview
        assertEquals(0, preview?.mergedRules)
        assertEquals(0, preview?.deletedRules)
    }
}
