package com.resolveprogramming.pocketcounter.ui.quickadd

import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.ManualEntryRelay
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.DuplicateTransactionException
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeTransactionIntentRepository
import com.resolveprogramming.pocketcounter.data.repository.IntentReadFailure
import com.resolveprogramming.pocketcounter.data.repository.IntentReadResult
import com.resolveprogramming.pocketcounter.data.repository.ContextInput
import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.CardCandidate
import com.resolveprogramming.pocketcounter.domain.model.CardResolution
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.FieldProvenance
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.NewTagRequest
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val intentRepository = FakeTransactionIntentRepository()
    private val transactionRepository: TransactionRepository = mockk()
    private val ruleRepository: ClassificationRuleRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val cardRepository: CardRepository = mockk()

    private val context = TagContext("ctx", "Casa", 0xFF112233)
    private val groceries = Tag("t-exp", "Mercado", TransactionType.EXPENSE)
    private val fees = Tag("t-inc", "Honorários", TransactionType.INCOME)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(groceries, fees))
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { transactionRepository.save(any(), any(), any()) } returns Result.success("tx-1")
        coEvery { ruleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Saved)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = QuickAddViewModel(
        intentRepository = intentRepository,
        transactionRepository = transactionRepository,
        classificationRuleRepository = ruleRepository,
        tagRepository = tagRepository,
        cardRepository = cardRepository,
        paymentMethodPrefsRepository = FakePaymentMethodPrefsRepository(),
        ledgerRefresh = LedgerRefreshSignal(),
        manualEntryRelay = ManualEntryRelay(),
    )

    private fun read(
        missing: List<MissingField> = emptyList(),
        source: Map<IntentField, ValueSource> = emptyMap(),
        candidates: List<CardCandidate> = emptyList(),
        idTag: String? = null,
        amount: BigDecimal? = BigDecimal("10.00"),
        name: String? = "Item",
        paymentMethod: PaymentMethod? = null,
        resolvedCard: CardCandidate? = null,
    ) {
        intentRepository.nextResult = IntentReadResult.Read(
            FakeTransactionIntentRepository.intent(
                missing = missing,
                source = source,
                cardCandidates = candidates,
                cardStatus = CardResolution.AMBIGUOUS.takeIf { candidates.isNotEmpty() }
                    ?: CardResolution.NOT_APPLICABLE,
                idTag = idTag,
                amount = amount,
                name = name,
                paymentMethod = paymentMethod,
                resolvedCard = resolvedCard,
            ),
        )
    }

    private fun TestScope.submitted(
        vm: QuickAddViewModel,
        text: String = "uma frase sintética",
    ): QuickAddViewModel {
        vm.setText(text)
        vm.submitSentence()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `an empty missing list lands straight on the preview`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)
        // Situação is answered on the preview, so it never joins the questions.
        assertTrue(vm.state.value.missing.isEmpty())
        assertNull(vm.state.value.currentAsk)
    }

    @Test
    fun `a read assumes pago and Lancar does not wait for it`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        val state = vm.state.value
        assertEquals(PaymentStatus.PAID, state.draft.statusPayment)
        assertEquals(FieldProvenance.INFERRED, state.provenanceOf(IntentField.STATUS))
        assertTrue(state.canSave)
    }

    @Test
    fun `choosing pago badges definido and is what the save carries`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        val draft = slot<WizardDraft>()
        coEvery { transactionRepository.save(capture(draft), any(), any()) } returns Result.success("tx-9")

        vm.setPaymentStatus(PaymentStatus.PAID)

        assertEquals(FieldProvenance.DEFINED, vm.state.value.provenanceOf(IntentField.STATUS))

        vm.save()
        advanceUntilIdle()

        assertEquals(PaymentStatus.PAID, draft.captured.statusPayment)
    }

    @Test
    fun `choosing pendente is what the save carries`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        val draft = slot<WizardDraft>()
        coEvery { transactionRepository.save(capture(draft), any(), any()) } returns Result.success("tx-9")

        vm.setPaymentStatus(PaymentStatus.PAID)
        vm.setPaymentStatus(PaymentStatus.PENDING)
        vm.save()
        advanceUntilIdle()

        assertEquals(PaymentStatus.PENDING, draft.captured.statusPayment)
        assertEquals(PaymentStatus.PENDING, vm.state.value.draft.statusPayment)
    }

    @Test
    fun `the questions follow the server order and always end on the preview`() = runTest(testDispatcher) {
        read(
            missing = listOf(MissingField.AMOUNT, MissingField.DESCRIPTION, MissingField.CARD, MissingField.TYPE),
            candidates = listOf(CardCandidate("c1", "Cartão A"), CardCandidate("c2", "Cartão B")),
            amount = null,
            name = null,
        )
        val vm = submitted(viewModel())

        assertEquals(MissingField.AMOUNT, vm.state.value.currentAsk)
        vm.setAmount(BigDecimal("12.00"))
        vm.confirmAsk()
        assertEquals(MissingField.DESCRIPTION, vm.state.value.currentAsk)
        vm.setName("Mercado")
        vm.confirmAsk()
        assertEquals(MissingField.CARD, vm.state.value.currentAsk)
        vm.skipCard()
        assertEquals(MissingField.TYPE, vm.state.value.currentAsk)
        vm.answerType(TransactionType.EXPENSE)

        assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)
    }

    @Test
    fun `an ambiguous card with no candidate is not asked`() = runTest(testDispatcher) {
        read(missing = listOf(MissingField.CARD))
        val vm = submitted(viewModel())

        assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)
        assertTrue(vm.state.value.missing.isEmpty())
    }

    @Test
    fun `an answered field badges definido, never assumido`() = runTest(testDispatcher) {
        read(missing = listOf(MissingField.AMOUNT), amount = null)
        val vm = submitted(viewModel())

        vm.setAmount(BigDecimal("12.00"))
        vm.confirmAsk()

        assertEquals(FieldProvenance.DEFINED, vm.state.value.provenanceOf(IntentField.AMOUNT))
    }

    @Test
    fun `a skipped card stays absent`() = runTest(testDispatcher) {
        read(
            missing = listOf(MissingField.CARD),
            candidates = listOf(CardCandidate("c1", "Cartão A"), CardCandidate("c2", "Cartão B")),
        )
        val vm = submitted(viewModel())

        vm.skipCard()

        assertEquals(FieldProvenance.ABSENT, vm.state.value.provenanceOf(IntentField.CARD))
    }

    @Test
    fun `the card and the payment method badge the sentence separately`() = runTest(testDispatcher) {
        // "comprei gasolina 320 no nubank": the card was written, the credit was inferred from it.
        read(
            source = mapOf(
                IntentField.CARD to ValueSource.WRITTEN,
                IntentField.PAYMENT_METHOD to ValueSource.INFERRED,
            ),
            paymentMethod = PaymentMethod.CREDIT,
            resolvedCard = CardCandidate("c1", "Cartão A"),
        )
        val vm = submitted(viewModel())

        assertEquals(FieldProvenance.WRITTEN, vm.state.value.provenanceOf(IntentField.CARD))
        assertEquals(
            FieldProvenance.INFERRED,
            vm.state.value.provenanceOf(IntentField.PAYMENT_METHOD),
        )
    }

    @Test
    fun `picking a card defines the card and the credit it implies`() = runTest(testDispatcher) {
        read(source = mapOf(IntentField.PAYMENT_METHOD to ValueSource.INFERRED))
        val vm = submitted(viewModel())

        vm.selectCard("c1")

        assertEquals(PaymentMethod.CREDIT, vm.state.value.draft.paymentMethod)
        assertEquals(FieldProvenance.DEFINED, vm.state.value.provenanceOf(IntentField.CARD))
        assertEquals(FieldProvenance.DEFINED, vm.state.value.provenanceOf(IntentField.PAYMENT_METHOD))
    }

    @Test
    fun `answering receita drops a credit card and an expense tag`() = runTest(testDispatcher) {
        read(idTag = groceries.id)
        val vm = submitted(viewModel())
        vm.selectCard("c1")

        vm.selectType(TransactionType.INCOME)

        val state = vm.state.value
        assertNull(state.draft.cardId)
        assertNull(state.draft.paymentMethod)
        assertTrue(state.draft.tagIds.isEmpty())
        assertEquals(FieldProvenance.ABSENT, state.provenanceOf(IntentField.CARD))
    }

    @Test
    fun `a read failure keeps the sentence and stays on input`() = runTest(testDispatcher) {
        intentRepository.nextResult = IntentReadResult.Failed(IntentReadFailure.Offline)
        val vm = submitted(viewModel(), text = "uma frase sintética")

        assertEquals(QuickAddStage.INPUT, vm.state.value.stage)
        assertEquals(IntentReadFailure.Offline, vm.state.value.readFailure)
        assertEquals("uma frase sintética", vm.state.value.text)
    }

    @Test
    fun `the payment method and the tag never gate Lancar`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())


        assertNull(vm.state.value.draft.paymentMethod)
        assertTrue(vm.state.value.draft.tagIds.isEmpty())
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `saving lands on saved with the id the server returned`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        coEvery { transactionRepository.save(any(), any(), any()) } returns Result.success("tx-9")
        vm.setPaymentStatus(PaymentStatus.PENDING)

        vm.save()
        advanceUntilIdle()

        assertEquals(QuickAddStage.SAVED, vm.state.value.stage)
        assertEquals("tx-9", vm.state.value.savedTransactionId)
    }

    @Test
    fun `a 409 shows the conflict and the retry allows the duplicate`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        coEvery {
            transactionRepository.save(any(), any(), false)
        } returns Result.failure(DuplicateTransactionException("tx-existing"))
        coEvery { transactionRepository.save(any(), any(), true) } returns Result.success("tx-2")

        vm.save()
        advanceUntilIdle()

        assertEquals(DuplicateConflict("tx-existing"), vm.state.value.duplicate)
        assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)

        vm.saveAnyway()
        advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any(), any(), true) }
        assertEquals(QuickAddStage.SAVED, vm.state.value.stage)
    }

    @Test
    fun `a failed save reports it without a conflict block`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        coEvery { transactionRepository.save(any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.saveFailed)
        assertNull(vm.state.value.duplicate)
        assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)
    }

    @Test
    fun `teaching writes the confirmed pattern and reports a duplicate rule`() = runTest(testDispatcher) {
        read(name = "Mercado")
        val vm = submitted(viewModel())
        vm.toggleTag(groceries.id)
        vm.setTeachEnabled(true)
        vm.setTeachPattern("mercado")
        val rule = slot<ClassificationRule>()
        coEvery { ruleRepository.create(capture(rule)) } returns Result.success(RuleWriteOutcome.Duplicate)

        vm.save()
        advanceUntilIdle()

        assertEquals("mercado", rule.captured.pattern)
        assertEquals(groceries.id, rule.captured.idTag)
        assertEquals("Lançado ✓ · a regra já existia.", vm.state.value.teachNote)
    }

    @Test
    fun `a rejected rule renders the server's own reason`() = runTest(testDispatcher) {
        read(name = "Mercado")
        val vm = submitted(viewModel())
        vm.toggleTag(groceries.id)
        vm.setTeachEnabled(true)
        coEvery { ruleRepository.create(any()) } returns
            Result.success(RuleWriteOutcome.Rejected("Você já tem 500 regras."))

        vm.save()
        advanceUntilIdle()

        assertEquals(
            "Lançado ✓ · regra não criada: Você já tem 500 regras.",
            vm.state.value.teachNote,
        )
    }

    @Test
    fun `a saved rule leaves no note`() = runTest(testDispatcher) {
        read(name = "Mercado")
        val vm = submitted(viewModel())
        vm.toggleTag(groceries.id)
        vm.setTeachEnabled(true)

        vm.save()
        advanceUntilIdle()

        assertNull(vm.state.value.teachNote)
    }

    @Test
    fun `a pattern under two characters blocks Lancar instead of dropping the rule`() =
        runTest(testDispatcher) {
            read(name = "Mercado")
            val vm = submitted(viewModel())
            vm.toggleTag(groceries.id)
            vm.setTeachEnabled(true)
            vm.setTeachPattern("m")

            assertFalse(vm.state.value.canSave)

            vm.setTeachPattern("me")

            assertTrue(vm.state.value.canSave)
        }

    @Test
    fun `the teach block is hidden when the server already classified the sentence`() =
        runTest(testDispatcher) {
            read(idTag = groceries.id)
            val vm = submitted(viewModel())

            assertFalse(vm.state.value.canTeach)
        }

    @Test
    fun `only one tag is ever selected`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        vm.toggleTag(groceries.id)
        vm.toggleTag(fees.id)

        assertEquals(listOf(fees.id), vm.state.value.draft.tagIds)

        vm.toggleTag(fees.id)

        assertTrue(vm.state.value.draft.tagIds.isEmpty())
    }

    @Test
    fun `reset clears the sentence and the read`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        vm.reset()

        val state = vm.state.value
        assertEquals("", state.text)
        assertNull(state.intent)
        assertEquals(QuickAddStage.INPUT, state.stage)
        assertEquals(listOf(groceries, fees), state.tags)
    }

    @Test
    fun `the method chips drop credit on an income draft`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        vm.selectType(TransactionType.INCOME)

        assertFalse(PaymentMethod.CREDIT in vm.state.value.selectableMethods)
    }

    @Test
    fun `a card answered before the type is discarded by Receita`() = runTest(testDispatcher) {
        read(
            missing = listOf(MissingField.CARD, MissingField.TYPE),
            candidates = listOf(CardCandidate("c1", "Cartão A"), CardCandidate("c2", "Cartão B")),
        )
        val vm = submitted(viewModel())

        vm.answerCard("c1")
        assertEquals(MissingField.TYPE, vm.state.value.currentAsk)
        vm.answerType(TransactionType.INCOME)

        val state = vm.state.value
        assertEquals(QuickAddStage.PREVIEW, state.stage)
        assertNull(state.draft.cardId)
        assertFalse(state.showsCardRow)
    }

    @Test
    fun `a rate limit counts down and only then frees the CTA`() = runTest(testDispatcher) {
        intentRepository.nextResult = IntentReadResult.Failed(IntentReadFailure.RateLimited(2))
        val vm = viewModel()
        vm.setText("uma frase sintética")
        vm.submitSentence()
        // Not advanceUntilIdle: that would run the whole countdown before the first assertion.
        runCurrent()

        assertEquals(2, vm.state.value.retrySeconds)
        assertFalse(vm.state.value.canSubmitSentence)

        advanceTimeBy(1_100)
        assertEquals(1, vm.state.value.retrySeconds)

        advanceTimeBy(1_000)
        assertNull(vm.state.value.retrySeconds)
        assertTrue(vm.state.value.canSubmitSentence)
    }

    @Test
    fun `a rate limit with no Retry-After leaves the CTA usable`() = runTest(testDispatcher) {
        intentRepository.nextResult = IntentReadResult.Failed(IntentReadFailure.RateLimited(null))
        val vm = submitted(viewModel())

        assertNull(vm.state.value.retrySeconds)
        assertTrue(vm.state.value.canSubmitSentence)
    }

    @Test
    fun `escaping to the manual form hands the sentence over trimmed`() = runTest(testDispatcher) {
        val relay = ManualEntryRelay()
        val vm = QuickAddViewModel(
            intentRepository = intentRepository,
            transactionRepository = transactionRepository,
            classificationRuleRepository = ruleRepository,
            tagRepository = tagRepository,
            cardRepository = cardRepository,
            paymentMethodPrefsRepository = FakePaymentMethodPrefsRepository(),
            ledgerRefresh = LedgerRefreshSignal(),
            manualEntryRelay = relay,
        )
        intentRepository.nextResult = IntentReadResult.Failed(IntentReadFailure.Offline)
        submitted(vm, text = "  uma frase sintética  ")

        vm.escapeToManualEntry()

        assertEquals("uma frase sintética", relay.pending.value)
    }

    @Test
    fun `a sentence nobody collected is dropped when the sheet opens again`() = runTest(testDispatcher) {
        val relay = ManualEntryRelay()
        relay.seed("frase de uma sessão anterior")

        QuickAddViewModel(
            intentRepository = intentRepository,
            transactionRepository = transactionRepository,
            classificationRuleRepository = ruleRepository,
            tagRepository = tagRepository,
            cardRepository = cardRepository,
            paymentMethodPrefsRepository = FakePaymentMethodPrefsRepository(),
            ledgerRefresh = LedgerRefreshSignal(),
            manualEntryRelay = relay,
        )

        assertNull(relay.pending.value)
    }

    @Test
    fun `a suggested tag the user no longer has is not carried into the save`() = runTest {
        val vm = viewModel()

        read(idTag = "019efa63-45e1-f000-9fd6-cbc205d0fa81")
        submitted(vm)

        assertEquals(emptyList<String>(), vm.state.value.draft.tagIds)
    }

    @Test
    fun `submitting the same sentence again reads nothing and keeps every answer`() =
        runTest(testDispatcher) {
            read(amount = null, missing = listOf(MissingField.AMOUNT))
            val vm = submitted(viewModel(), text = "almoço ontem")
            vm.setAmount(BigDecimal("68.00"))
            vm.confirmAsk()
            vm.setDate(LocalDate.of(2026, 5, 2))
            vm.setPaymentStatus(PaymentStatus.PENDING)
            val before = vm.state.value.draft

            vm.editSentence()
            vm.submitSentence()
            advanceUntilIdle()

            assertEquals(1, intentRepository.calls.size)
            assertEquals(QuickAddStage.PREVIEW, vm.state.value.stage)
            assertEquals(before, vm.state.value.draft)
        }

    @Test
    fun `a re-read keeps a correction the new sentence does not mention`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel(), text = "almoço 68")
        vm.setDate(LocalDate.of(2026, 5, 2))
        vm.setPaymentStatus(PaymentStatus.PENDING)

        vm.editSentence()
        vm.setText("almoço 72")
        read(amount = BigDecimal("72.00"), source = mapOf(IntentField.AMOUNT to ValueSource.WRITTEN))
        vm.submitSentence()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(LocalDate.of(2026, 5, 2), state.draft.date)
        assertEquals(PaymentStatus.PENDING, state.draft.statusPayment)
        assertTrue(IntentField.DATE in state.defined)
        assertEquals(FieldProvenance.DEFINED, state.provenanceOf(IntentField.DATE))
    }

    @Test
    fun `a re-read wins over a stale correction of what it says`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel(), text = "almoço 68")
        vm.setAmount(BigDecimal("100.00"))

        vm.editSentence()
        vm.setText("almoço 72")
        read(amount = BigDecimal("72.00"), source = mapOf(IntentField.AMOUNT to ValueSource.WRITTEN))
        vm.submitSentence()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(BigDecimal("72.00"), state.draft.amount)
        assertFalse(IntentField.AMOUNT in state.defined)
    }

    @Test
    fun `a tag created before the edit survives the re-read`() = runTest(testDispatcher) {
        val created = Tag("t-new", "Veterinário", TransactionType.EXPENSE, idContext = context.id)
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(context))
        coEvery { tagRepository.createTag(any()) } returns Result.success(created)
        read()
        val vm = submitted(viewModel(), text = "consulta 250")
        vm.createAndApplyTag(NewTagRequest(name = "Veterinário", color = 0, contextId = context.id))
        advanceUntilIdle()

        vm.editSentence()
        vm.setText("consulta 250 do cachorro")
        read()
        vm.submitSentence()
        advanceUntilIdle()

        assertEquals(listOf(created.id), vm.state.value.draft.tagIds)
        assertTrue(IntentField.TAG in vm.state.value.defined)
    }

    @Test
    fun `a created tag replaces the selected one instead of being appended`() = runTest(testDispatcher) {
        val created = Tag("t-new", "Veterinário", TransactionType.EXPENSE, idContext = context.id)
        coEvery { tagRepository.createTag(any()) } returns Result.success(created)
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(context))
        read()
        val vm = submitted(viewModel())
        vm.toggleTag(groceries.id)

        vm.createAndApplyTag(NewTagRequest(name = "Veterinário", color = 0, contextId = context.id))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(created.id), state.draft.tagIds)
        assertTrue(created in state.tags)
        assertTrue(IntentField.TAG in state.defined)
        assertFalse(state.isCreatingTag)
    }

    @Test
    fun `an income category is created flat, with a color and no context`() = runTest(testDispatcher) {
        val created = Tag("t-new", "Dividendos", TransactionType.INCOME, color = 0xFF23A268)
        coEvery { tagRepository.createTag(any()) } returns Result.success(created)
        read()
        val vm = submitted(viewModel())
        vm.selectType(TransactionType.INCOME)

        vm.createAndApplyTag(NewTagRequest(name = "Dividendos", color = 0xFF23A268))
        advanceUntilIdle()

        val tagInput = slot<TagInput>()
        coVerify { tagRepository.createTag(capture(tagInput)) }
        coVerify(exactly = 0) { tagRepository.createContext(any()) }
        assertEquals(TransactionType.INCOME, tagInput.captured.kind)
        assertNull(tagInput.captured.idContext)
        assertEquals(0xFF23A268, tagInput.captured.color)
    }

    @Test
    fun `Lancar is blocked while the tag is being written`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        val ready = vm.state.value

        assertTrue(ready.canSave)
        assertFalse(ready.copy(isSavingTag = true).canSave)
    }

    @Test
    fun `creating from a browsed category opens the form under it`() = runTest(testDispatcher) {
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(context))
        read()
        val vm = submitted(viewModel())

        vm.openNewTag(contextId = context.id)

        assertEquals(context.id, vm.state.value.newTagContextId)
    }

    @Test
    fun `an incomplete request is never written`() = runTest(testDispatcher) {
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(context))
        read()
        val vm = submitted(viewModel())

        vm.createAndApplyTag(NewTagRequest(name = "  ", color = 0, contextId = context.id))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.createTag(any()) }
        assertFalse(vm.state.value.isSavingTag)
    }

    @Test
    fun `a failed tag create keeps the form open with the reason`() = runTest(testDispatcher) {
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(context))
        coEvery { tagRepository.createTag(any()) } returns
            Result.failure(IllegalStateException("nome já existe"))
        read()
        val vm = submitted(viewModel())
        vm.openNewTag()

        vm.createAndApplyTag(NewTagRequest(name = "Veterinário", color = 0, contextId = context.id))
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isCreatingTag)
        assertEquals("Não foi possível criar a tag: nome já existe", state.tagFormError)
        assertFalse(state.isSavingTag)
    }

    @Test
    fun `an expense with no categories cannot open the form`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())

        vm.openNewTag()

        assertFalse(vm.state.value.isCreatingTag)
    }

    @Test
    fun `closing the form leaves the preview and its answers untouched`() = runTest(testDispatcher) {
        read()
        val vm = submitted(viewModel())
        vm.openNewTag()

        vm.closeNewTag()

        val state = vm.state.value
        assertFalse(state.isCreatingTag)
        assertNull(state.tagFormError)
        assertEquals(QuickAddStage.PREVIEW, state.stage)
    }
}
