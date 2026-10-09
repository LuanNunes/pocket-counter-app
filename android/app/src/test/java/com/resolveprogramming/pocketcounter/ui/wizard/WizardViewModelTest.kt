package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.local.AppMessageRelay
import com.resolveprogramming.pocketcounter.data.repository.BlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome
import com.resolveprogramming.pocketcounter.data.repository.FakeBlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeIssuerCardRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeProductiveSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.ProductiveSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.notification.NotificationEvidence
import com.resolveprogramming.pocketcounter.domain.notification.resolveDraftFromNotification
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.contextos.TagFormMode
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.IgnoreScope
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val notificationRepository: NotificationRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val seriesRepository: SeriesRepository = mockk()
    private val classificationRuleRepository: ClassificationRuleRepository = mockk()
    private val cardLast4Repository: CardLast4Repository = mockk()
    private val issuerCardRepository = FakeIssuerCardRepository()
    private val fakePaymentMethodPrefsRepository = FakePaymentMethodPrefsRepository()
    private val paymentMethodDictionaryRepository: PaymentMethodDictionaryRepository = mockk()
    private val blockedSourceRepository: BlockedSourceRepository = mockk()
    private val productiveSourceRepository = FakeProductiveSourceRepository()
    private val appMessageRelay = AppMessageRelay()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default stubs — individual tests may override
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { seriesRepository.getAll() } returns Result.success(emptyList())
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, null))
        coEvery { seriesRepository.setTags(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns Result.success(Unit)
        coEvery { classificationRuleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Saved)
        // Broad fallbacks so in-place switches resolve without NPE; tests override for specific ids.
        coEvery { notificationRepository.getById(any()) } answers {
            Result.success(makeNotification(id = firstArg()))
        }
        coEvery { notificationRepository.classify(any(), any()) } answers {
            Result.success(ClassifiedNotification(notification = secondArg(), pendingTransactionId = null))
        }
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        // Default: queue is empty after any save/ignore → onDone is called
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { notificationRepository.markIgnored(any()) } returns Result.success(Unit)
        // Default: empty last-4 map so most tests are unaffected by the prefill logic.
        coEvery { cardLast4Repository.getMap() } returns emptyMap()
        coEvery { cardLast4Repository.associate(any(), any()) } returns Unit
        // Default: empty dictionary so most tests are unaffected by the learned-dictionary logic.
        coEvery { paymentMethodDictionaryRepository.getMap() } returns emptyMap()
        coEvery { paymentMethodDictionaryRepository.learn(any(), any()) } returns Unit
        coEvery { paymentMethodDictionaryRepository.forget(any()) } returns Unit
        coEvery { blockedSourceRepository.block(any(), any()) } returns Unit
        coEvery { blockedSourceRepository.unblock(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeNotification(
        id: String = "notif-1",
        status: NotificationStatus = NotificationStatus.NEEDS_REVIEW,
        type: TransactionType? = TransactionType.EXPENSE,
        amount: BigDecimal? = BigDecimal("49.90"),
        paymentMethod: PaymentMethod? = null,
        suggestedTagId: String? = null,
        tokens: List<Token> = emptyList(),
        paymentHint: String? = null,
        app: String = "Banco Itaú",
        channel: NotificationChannel = NotificationChannel.SMS,
        text: String = "Compra aprovada R$ 49,90",
    ) = NotificationItem(
        id = id,
        app = app,
        channel = channel,
        time = "agora",
        received = "10:00",
        // A method is never suggested by the classifier; it can only be worded in the text.
        text = text + methodWording(paymentMethod),
        status = status,
        parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = LocalDate.of(2026, 6, 12),
            merchantRaw = "IFOOD",
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(idTag = suggestedTagId),
        tokens = tokens,
    )

    private val methodWordings = mapOf(
        PaymentMethod.PIX to " via pix",
        PaymentMethod.CREDIT to " no crédito",
        PaymentMethod.DEBIT to " no débito",
    )

    /** The parser reads the method off the text, so a method only reaches a draft if it is worded in. */
    private fun methodWording(method: PaymentMethod?): String = methodWordings[method].orEmpty()

    private fun makeCreditCard(id: String = "card-x") = CreditCard(
        id = id,
        name = "Nubank",
        brand = "Mastercard",
        last4 = "1234",
        gradientStart = 0xFF6F00C9L,
        gradientEnd = 0xFF4A0096L,
        limit = BigDecimal("5000.00"),
        billDay = 10,
    )

    private fun makeViewModel(
        notificationId: String = "notif-1",
        paymentMethodPrefsRepository: FakePaymentMethodPrefsRepository = fakePaymentMethodPrefsRepository,
        dictionaryRepository: PaymentMethodDictionaryRepository = paymentMethodDictionaryRepository,
        blockedSources: BlockedSourceRepository = blockedSourceRepository,
        productiveSources: ProductiveSourceRepository = productiveSourceRepository,
    ): WizardViewModel {
        val handle = SavedStateHandle(mapOf("notificationId" to notificationId))
        return WizardViewModel(
            savedStateHandle = handle,
            notificationRepository = notificationRepository,
            cardRepository = cardRepository,
            tagRepository = tagRepository,
            seriesRepository = seriesRepository,
            classificationRuleRepository = classificationRuleRepository,
            confirmClassifiedNotification = ConfirmClassifiedNotificationUseCase(
                transactionRepository,
                notificationRepository,
            ),
            cardLast4Repository = cardLast4Repository,
            issuerCardRepository = issuerCardRepository,
            paymentMethodPrefsRepository = paymentMethodPrefsRepository,
            paymentMethodDictionaryRepository = dictionaryRepository,
            blockedSourceRepository = blockedSources,
            productiveSourceRepository = productiveSources,
            appMessageRelay = appMessageRelay,
        )
    }

    /**
     * Collects everything [appMessageRelay] emits while [block] runs. Unconfined so the collector
     * subscribes at launch: the relay replays nothing, so a later subscription sees no message.
     */
    private fun TestScope.relayedMessages(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            appMessageRelay.messages.collect { messages += it }
        }
        testDispatcher.scheduler.advanceUntilIdle()
        block()
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()
        return messages
    }

    // -------------------------------------------------------------------------
    // WizardStep enum — 4 steps only
    // -------------------------------------------------------------------------

    @Test
    fun `WizardStep has exactly 4 entries`() {
        assertEquals(4, WizardStep.entries.size)
    }

    @Test
    fun `WizardStep entries are TYPE AMOUNT PAYMENT TAGS in order`() {
        val entries = WizardStep.entries.map { it.name }
        assertEquals(listOf("TYPE", "AMOUNT", "PAYMENT", "TAGS"), entries)
    }

    @Test
    fun `WizardStep subtitles are 1 de 4 through 4 de 4`() {
        assertEquals("1 de 4", WizardStep.TYPE.subtitle)
        assertEquals("2 de 4", WizardStep.AMOUNT.subtitle)
        assertEquals("3 de 4", WizardStep.PAYMENT.subtitle)
        assertEquals("4 de 4", WizardStep.TAGS.subtitle)
    }

    // -------------------------------------------------------------------------
    // Short-circuit: classify returns pendingTransactionId != null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification with pendingTransactionId sets isConfirmingPending true`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = "tx-pending-42")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val ready = awaitItem()
            assertTrue(ready.isConfirmingPending)
            assertEquals("tx-pending-42", ready.pendingTransactionId)
            assertFalse(ready.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNotification with pendingTransactionId does not build a draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = "tx-pending-42")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // loading
            val ready = awaitItem()
            assertNull(ready.draft.type)
            assertNull(ready.draft.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmPending calls markPaid then markClassified and sets pendingConfirmed`() = runTest {
        val notification = makeNotification()
        val pendingId = "tx-pending-42"
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = pendingId)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.markPaid(pendingId) } returns Result.success(Unit)
        coEvery { notificationRepository.markClassified("notif-1", pendingId) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.confirmPending()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.pendingConfirmed)
        assertFalse(state.isConfirmingPending)
        coVerify(exactly = 1) { transactionRepository.markPaid(pendingId) }
        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", pendingId) }
    }

    @Test
    fun `confirmPending does nothing when pendingTransactionId is null`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.confirmPending()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { transactionRepository.markPaid(any()) }
    }

    // -------------------------------------------------------------------------
    // Normal enrich: classify returns pendingTransactionId == null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification normal enrich builds draft from classified notification`() = runTest {
        val enrichedNotification = makeNotification(
            status = NotificationStatus.NEEDS_REVIEW,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("153.98"),
            paymentMethod = PaymentMethod.CREDIT,
            paymentHint = "final 3685",
            suggestedTagId = "tag-1",
        )
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-abc" to "3685")
        val classified = ClassifiedNotification(notification = enrichedNotification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(enrichedNotification)
        coEvery { notificationRepository.classify("notif-1", enrichedNotification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.EXPENSE, state.draft.type)
        assertEquals(BigDecimal("153.98"), state.draft.amount)
        assertEquals(PaymentMethod.CREDIT, state.draft.paymentMethod)
        assertEquals("card-abc", state.draft.cardId)
        assertEquals(listOf("tag-1"), state.draft.tagIds)
    }

    @Test
    fun `loadNotification normal enrich populates tokens from classified notification`() = runTest {
        val tokenList = listOf(
            Token(text = "Compra"),
            Token(text = "49,90", role = TokenRole.AMOUNT, value = "49.90"),
        )
        val enrichedNotification = makeNotification(tokens = tokenList)
        val classified = ClassifiedNotification(notification = enrichedNotification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(enrichedNotification)
        coEvery { notificationRepository.classify("notif-1", enrichedNotification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(tokenList, vm.state.value.tokens)
    }

    @Test
    fun `loadNotification NEEDS_TAGS status resolves start step to TAGS`() = runTest {
        val notification = makeNotification(status = NotificationStatus.NEEDS_TAGS)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WizardStep.TAGS, vm.state.value.step)
    }

    @Test
    fun `loadNotification non-NEEDS_TAGS status resolves start step to TYPE`() = runTest {
        val notification = makeNotification(status = NotificationStatus.NEEDS_REVIEW)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WizardStep.TYPE, vm.state.value.step)
    }

    @Test
    fun `loadNotification sets isLoading false after enrich`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `loadNotification normal enrich does not set isConfirmingPending`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isConfirmingPending)
    }

    @Test
    fun `loadNotification loads cards into state from cardRepository`() = runTest {
        val card = makeCreditCard("card-x")
        coEvery { cardRepository.getCards() } returns Result.success(listOf(card))
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(card), vm.state.value.cards)
    }

    // -------------------------------------------------------------------------
    // Graceful degrade: classify returns Result.failure
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification classify failure falls back to base notification draft`() = runTest {
        val base = makeNotification(
            type = TransactionType.INCOME,
            amount = BigDecimal("200.00"),
            paymentMethod = PaymentMethod.PIX,
        )
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("network error"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.INCOME, state.draft.type)
        assertEquals(BigDecimal("200.00"), state.draft.amount)
        assertEquals(PaymentMethod.PIX, state.draft.paymentMethod)
    }

    @Test
    fun `the wizard opens on the same draft the one-tap path builds from the same evidence`() = runTest {
        val itau = makeCreditCard("card-itau").copy(name = "Itaú")
        data class Case(
            val notification: NotificationItem,
            val evidence: NotificationEvidence,
        )
        val cases = listOf(
            Case(
                makeNotification(text = "Compra parcelado R$ 49,90"),
                NotificationEvidence(
                    cards = listOf(itau),
                    paymentMethodDictionary = mapOf("parcelado" to PaymentMethod.CREDIT),
                ),
            ),
            Case(
                makeNotification(text = "Compra no crédito R$ 49,90", paymentHint = "final 3685"),
                NotificationEvidence(cards = listOf(itau), last4Map = mapOf("card-itau" to "3685")),
            ),
            Case(
                makeNotification(text = "Compra no débito R$ 49,90"),
                NotificationEvidence(cards = listOf(itau)),
            ),
            Case(
                makeNotification(
                    text = "Crédito em conta R$ 49,90",
                    type = TransactionType.INCOME,
                ),
                NotificationEvidence(cards = listOf(itau)),
            ),
        )

        cases.forEach { (notification, evidence) ->
            coEvery { cardRepository.getCards() } returns Result.success(evidence.cards)
            coEvery { cardLast4Repository.getMap() } returns evidence.last4Map
            coEvery { paymentMethodDictionaryRepository.getMap() } returns evidence.paymentMethodDictionary
            coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
            coEvery { notificationRepository.classify("notif-1", notification) } returns
                Result.success(ClassifiedNotification(notification, pendingTransactionId = null))

            val vm = makeViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                resolveDraftFromNotification(notification, evidence).draft,
                vm.state.value.draft,
            )
        }
    }

    @Test
    fun `loadNotification classify failure toasts the cause and leaves error null`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("classify failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.toastMessage)
        assertTrue(vm.state.value.toastMessage!!.contains("classify failed"))
        assertNull(vm.state.value.error)
    }

    @Test
    fun `loadNotification classify failure does not set isConfirmingPending`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("classify failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isConfirmingPending)
    }

    @Test
    fun `loadNotification classify failure still sets isLoading false`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("timeout"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
    }

    // -------------------------------------------------------------------------
    // save() path
    // -------------------------------------------------------------------------

    @Test
    fun `save calls markClassified after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", "tx-new-99") }
        coVerify(exactly = 1) { transactionRepository.save(any(), "notif-1") }
    }

    @Test
    fun `save calls onDone after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    @Test
    fun `save calls onDone even when markClassified returns failure (best-effort)`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.failure(RuntimeException("server error"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    @Test
    fun `save does not call onDone when transactionRepository save fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(doneCalled)
    }

    @Test
    fun `save surfaces a toast carrying the cause when transactionRepository save fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("HTTP 400 Bad Request"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Não foi possível salvar: HTTP 400 Bad Request", vm.state.value.toastMessage)
    }

    @Test
    fun `save toast falls back to a bare message when the cause has none`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Não foi possível salvar", vm.state.value.toastMessage)
    }

    @Test
    fun `consumeToast clears the save-failure toast`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()
        vm.consumeToast()

        assertNull(vm.state.value.toastMessage)
    }

    @Test
    fun `save failure leaves the CTA usable so the user can retry`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.isSwitching)
    }

    @Test
    fun `save resets isSaving to false on transaction save failure`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isSaving)
    }

    // -------------------------------------------------------------------------
    // NEW: selectPaymentMethod
    // -------------------------------------------------------------------------

    @Test
    fun `selectPaymentMethod sets paymentMethod on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.PIX)

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
    }

    @Test
    fun `selectPaymentMethod CREDIT then selectCard sets both on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.selectCard("card-x")

        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-x", draft.cardId)
    }

    @Test
    fun `selectPaymentMethod DEBIT after cardId was set clears cardId`() = runTest {
        val notification = makeNotification(paymentMethod = PaymentMethod.CREDIT, paymentHint = "final 1234")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-x" to "1234")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify card was loaded into draft
        assertEquals("card-x", vm.state.value.draft.cardId)

        vm.selectPaymentMethod(PaymentMethod.DEBIT)

        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.DEBIT, draft.paymentMethod)
        assertNull(draft.cardId)
    }

    // -------------------------------------------------------------------------
    // NEW: selectCard
    // -------------------------------------------------------------------------

    @Test
    fun `selectCard sets cardId on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectCard("card-abc")

        assertEquals("card-abc", vm.state.value.draft.cardId)
    }

    // -------------------------------------------------------------------------
    // NEW: toggleFixo and updateRecurrenceDay
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFixo true then updateRecurrenceDay 10 both reflected in draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(10)

        val draft = vm.state.value.draft
        assertTrue(draft.isFixo)
        assertEquals(10, draft.recurrenceDay)
    }

    @Test
    fun `toggleFixo false clears isFixo on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.toggleFixo(false)

        assertFalse(vm.state.value.draft.isFixo)
    }

    @Test
    fun `updateRecurrenceDay null clears recurrenceDay on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.updateRecurrenceDay(15)

        vm.updateRecurrenceDay(null)

        assertNull(vm.state.value.draft.recurrenceDay)
    }

    // -------------------------------------------------------------------------
    // save() — recurring-series flow
    // -------------------------------------------------------------------------

    @Test
    fun `save non-fixo does not touch seriesRepository`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // draft.isFixo defaults to false — no toggleFixo call

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { seriesRepository.linkTransaction(any(), any(), any()) }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo with no existing series creates series then links transaction`() = runTest {
        val notification = makeNotification(suggestedTagId = "t1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create("IFOOD", TransactionType.EXPENSE, 10) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, 10))
        coEvery { seriesRepository.setTags("s-new", listOf("t1")) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction("s-new", "tx-1", false) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(10)
        // draft.seriesId remains null — no selectSeries call

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            transactionRepository.save(any(), any())
            seriesRepository.create("IFOOD", TransactionType.EXPENSE, 10)
            seriesRepository.setTags("s-new", listOf("t1"))
            seriesRepository.linkTransaction("s-new", "tx-1", false)
        }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo derives series name from merchant`() = runTest {
        // makeNotification sets merchantRaw = "IFOOD"; fromNotification seeds both merchant and name
        // from merchantRaw, so draft.merchant = "IFOOD" drives the series name via linkSeries
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.create("IFOOD", any(), any()) }
    }

    @Test
    fun `save fixo with empty tagIds does not call setTags`() = runTest {
        // notification has no suggested tag → draft.tagIds is empty
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.setTags(any(), any()) }
    }

    @Test
    fun `save fixo linking an existing series links without creating`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction("s-existing", "tx-1", false) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)
        vm.selectSeries("s-existing")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.linkTransaction("s-existing", "tx-1", false) }
        coVerify(exactly = 0) { seriesRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { seriesRepository.setTags(any(), any()) }
    }

    @Test
    fun `save fixo still succeeds when series create fails (best-effort, transaction not rolled back)`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.failure(RuntimeException("series create failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any(), any()) }
        coVerify(exactly = 1) { notificationRepository.markClassified(any(), any()) }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo still succeeds when linkTransaction fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, 5))
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns
            Result.failure(RuntimeException("link failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    // -------------------------------------------------------------------------
    // save() — queue advancement
    // -------------------------------------------------------------------------

    @Test
    fun `save advances to next pending item in place when queue has another notification`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val nextNotification = makeNotification(id = "notif-2")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-99")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-2", vm.state.value.notification?.id)
        assertFalse(doneCalled)
    }

    @Test
    fun `save calls onDone when no more pending notifications`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-99")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    // -------------------------------------------------------------------------
    // ignore() path — IgnoreScope.ThisOnly
    // -------------------------------------------------------------------------

    @Test
    fun `ignore ThisOnly marks notification ignored and advances in place to next pending item`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val nextNotification = makeNotification(id = "notif-2")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.ignore(scope = IgnoreScope.ThisOnly, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertEquals("notif-2", vm.state.value.notification?.id)
        assertFalse(doneCalled)
    }

    @Test
    fun `ignore ThisOnly calls onDone when no more pending notifications`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.ignore(scope = IgnoreScope.ThisOnly, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertTrue(doneCalled)
    }

    @Test
    fun `ignore ThisOnly never creates a rule`() = runTest {
        // ThisOnly is what the wizard falls back to when neither a pattern nor source-blocking is
        // derivable — it must still ignore cleanly, and never touch the rule repository.
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.ThisOnly, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    // -------------------------------------------------------------------------
    // ignore() path — IgnoreScope.Pattern
    // -------------------------------------------------------------------------

    @Test
    fun `ignore Pattern creates an IGNORE rule with the given pattern and marks ignored`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Pattern("IFOOD"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { it.action == RuleAction.IGNORE && it.pattern == "IFOOD" && it.idTag == null },
            )
        }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore Pattern treats a duplicate IGNORE rule as success`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { classificationRuleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Duplicate)

        val vm = makeViewModel()
        val messages = relayedMessages {
            vm.ignore(scope = IgnoreScope.Pattern("IFOOD"), onDone = {})
        }

        assertTrue(messages.isEmpty())
        assertNull(vm.state.value.toastMessage)
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore Pattern reports a server-refused IGNORE rule, not silent success`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { classificationRuleRepository.create(any()) } returns
            Result.success(RuleWriteOutcome.Rejected("Padrão inválido."))

        val vm = makeViewModel()
        val messages = relayedMessages {
            vm.ignore(scope = IgnoreScope.Pattern("IFOOD"), onDone = {})
        }

        assertEquals(listOf("Notificação ignorada, mas não foi possível salvar a regra."), messages)
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore Pattern toast survives advancing to the next pending item after a rule create failure`() = runTest {
        // Regression: loadNotification rebuilds state wholesale on advance; the toast set just
        // before it must ride along or it is destroyed before it ever renders.
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        val nextNotification = makeNotification(id = "notif-2")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.getById("notif-2") } returns Result.success(nextNotification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.classify("notif-2", nextNotification) } returns
            Result.success(ClassifiedNotification(nextNotification, null))
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(notification, nextNotification))
        coEvery { classificationRuleRepository.create(any()) } returns Result.failure(RuntimeException("boom"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Pattern("IFOOD"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-2", vm.state.value.notification?.id)
        assertEquals(
            "Notificação ignorada, mas não foi possível salvar a regra.",
            vm.state.value.toastMessage,
        )
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore Pattern relays the rule-create failure to Home when the queue empties`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { classificationRuleRepository.create(any()) } returns Result.failure(RuntimeException("boom"))

        val vm = makeViewModel()

        var doneCalled = false
        val messages = relayedMessages {
            vm.ignore(scope = IgnoreScope.Pattern("IFOOD"), onDone = { doneCalled = true })
        }

        assertTrue(doneCalled)
        assertEquals(listOf("Notificação ignorada, mas não foi possível salvar a regra."), messages)
        assertNull(vm.state.value.toastMessage)
    }

    // -------------------------------------------------------------------------
    // ignore() path — IgnoreScope.Source
    // -------------------------------------------------------------------------

    @Test
    fun `ignore Source blocks the app in the repository`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { blockedSourceRepository.block("Google", any()) }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore Source bulk-ignores other pending items from the same app by normalized key`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val other = makeNotification(id = "notif-2", app = "google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification, other))
        coEvery { notificationRepository.markIgnored("notif-2") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-2") }
    }

    @Test
    fun `ignore Source does not bulk-ignore pending items from a different app`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val other = makeNotification(id = "notif-2", app = "Nubank", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification, other))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { notificationRepository.markIgnored("notif-2") }
    }

    @Test
    fun `ignore Source excludes the current and bulk-ignored ids from the next advance`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val other = makeNotification(id = "notif-2", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification, other))
        coEvery { notificationRepository.markIgnored("notif-2") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Both the current item and the bulk-ignored one are excluded from "next", even though the
        // (stale) getPendingReview stub still lists notif-2 as pending.
        var doneCalled = false
        vm.ignore(scope = IgnoreScope.Source("Google"), onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    @Test
    fun `ignore Source toast has no pendente suffix when no other pending item matched`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))

        val vm = makeViewModel()

        val messages = relayedMessages { vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {}) }

        assertEquals(listOf("Notificações do Google não serão mais capturadas."), messages)
    }

    @Test
    fun `ignore Source toast uses singular wording for exactly one bulk-ignored pending item`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val other = makeNotification(id = "notif-2", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification, other))
        coEvery { notificationRepository.markIgnored("notif-2") } returns Result.success(Unit)

        val vm = makeViewModel()

        val messages = relayedMessages { vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {}) }

        assertEquals(
            listOf("Notificações do Google não serão mais capturadas. 1 pendente foi descartada."),
            messages,
        )
    }

    @Test
    fun `ignore Source toast counts only successful bulk markIgnored calls, not attempts`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val ok = makeNotification(id = "notif-2", app = "Google", channel = NotificationChannel.PUSH)
        val failed = makeNotification(id = "notif-3", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(notification, ok, failed))
        coEvery { notificationRepository.markIgnored("notif-2") } returns Result.success(Unit)
        coEvery { notificationRepository.markIgnored("notif-3") } returns Result.failure(RuntimeException("boom"))

        val vm = makeViewModel()

        val messages = relayedMessages { vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {}) }

        assertEquals(
            listOf("Notificações do Google não serão mais capturadas. 1 pendente foi descartada."),
            messages,
        )
    }

    @Test
    fun `ignore Source toast uses plural wording for more than one bulk-ignored pending item`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val other1 = makeNotification(id = "notif-2", app = "Google", channel = NotificationChannel.PUSH)
        val other2 = makeNotification(id = "notif-3", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(notification, other1, other2))
        coEvery { notificationRepository.markIgnored("notif-2") } returns Result.success(Unit)
        coEvery { notificationRepository.markIgnored("notif-3") } returns Result.success(Unit)

        val vm = makeViewModel()

        val messages = relayedMessages { vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {}) }

        assertEquals(
            listOf("Notificações do Google não serão mais capturadas. 2 pendentes foram descartadas."),
            messages,
        )
    }

    @Test
    fun `ignore Source toast survives advancing to the next pending item`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val nextNotification = makeNotification(id = "notif-2", app = "Nubank", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.getById("notif-2") } returns Result.success(nextNotification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.classify("notif-2", nextNotification) } returns
            Result.success(ClassifiedNotification(nextNotification, null))
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(notification, nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-2", vm.state.value.notification?.id)
        assertEquals(
            "Notificações do Google não serão mais capturadas.",
            vm.state.value.toastMessage,
        )
    }

    @Test
    fun `ignore Source relays the toast instead of stranding it in state when the queue empties`() = runTest {
        // The screen is popped as soon as onDone runs, taking its toast host with it — a message left
        // in state.toastMessage here would never render.
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()

        var doneCalled = false
        val messages = relayedMessages {
            vm.ignore(scope = IgnoreScope.Source("Google"), onDone = { doneCalled = true })
        }

        assertTrue(doneCalled)
        assertEquals(listOf("Notificações do Google não serão mais capturadas."), messages)
        assertNull(vm.state.value.toastMessage)
    }

    @Test
    fun `ignore Source still ignores and says so when the block write fails`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { blockedSourceRepository.block(any(), any()) } throws IOException("disk full")

        val vm = makeViewModel()

        val messages = relayedMessages { vm.ignore(scope = IgnoreScope.Source("Google"), onDone = {}) }

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertEquals(listOf("Notificação ignorada, mas não foi possível bloquear Google."), messages)
    }

    @Test
    fun `ignore Source with a blank-normalizing label sweeps nothing and blocks nothing`() = runTest {
        // A null key must not be treated as a wildcard: it would match every other blank-labelled
        // pending item and discard them.
        val blockedSources = FakeBlockedSourceRepository()
        val notification = makeNotification(id = "notif-1", app = "📉", channel = NotificationChannel.PUSH)
        val other = makeNotification(id = "notif-2", app = "📈", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification, other))

        val vm = makeViewModel(blockedSources = blockedSources)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(scope = IgnoreScope.Source("📉"), onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        coVerify(exactly = 0) { notificationRepository.markIgnored("notif-2") }
        assertTrue(blockedSources.blocklist.first().entries.isEmpty())
    }

    // -------------------------------------------------------------------------
    // state.ignoreOption
    // -------------------------------------------------------------------------

    @Test
    fun `state ignoreOption is Source when no pattern is derivable but the source can be blocked`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(IgnoreScope.Source("Google"), vm.state.value.ignoreOption)
    }

    // -------------------------------------------------------------------------
    // state.sourceTransactionCount
    // -------------------------------------------------------------------------

    @Test
    fun `sourceTransactionCount is loaded for the notification's source app`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))

        val vm = makeViewModel(productiveSources = FakeProductiveSourceRepository(mapOf("google" to 3)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.sourceTransactionCount)
    }

    @Test
    fun `sourceTransactionCount is zero for a source that never produced a transaction`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification, null))

        val vm = makeViewModel(productiveSources = FakeProductiveSourceRepository(mapOf("nubank" to 9)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.sourceTransactionCount)
    }

    @Test
    fun `save records the notification's source as productive`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new-99")
        val productiveSources = FakeProductiveSourceRepository()

        val vm = makeViewModel(productiveSources = productiveSources)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, productiveSources.countFor("Google"))
    }

    @Test
    fun `a failed save does not record the source as productive`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.failure(RuntimeException("save failed"))
        val productiveSources = FakeProductiveSourceRepository()

        val vm = makeViewModel(productiveSources = productiveSources)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, productiveSources.countFor("Google"))
    }

    @Test
    fun `confirmPending records the notification's source as productive`() = runTest {
        val notification = makeNotification(id = "notif-1", app = "Google", channel = NotificationChannel.PUSH)
        val pendingId = "tx-pending-42"
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = pendingId)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.markPaid(pendingId) } returns Result.success(Unit)
        val productiveSources = FakeProductiveSourceRepository()

        val vm = makeViewModel(productiveSources = productiveSources)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.confirmPending()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, productiveSources.countFor("Google"))
    }

    @Test
    fun `loadNotification prefills DEBIT from an explicit debito mention in the text`() = runTest {
        val notification = makeNotification(id = "notif-1")
            .copy(text = "Compra no débito aprovada Compra de R$ 14,42 em PONTE DO TRIGO")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns emptyMap()

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaymentMethod.DEBIT, vm.state.value.draft.paymentMethod)
    }

    // -------------------------------------------------------------------------
    // updateName()
    // -------------------------------------------------------------------------

    @Test
    fun `updateName sets name and merchant on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateName("Coffee Shop")

        val draft = vm.state.value.draft
        assertEquals("Coffee Shop", draft.name)
        assertEquals("Coffee Shop", draft.merchant)
    }

    @Test
    fun `updateName with blank value sets name to blank and clears merchant`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateName("Coffee")
        vm.updateName("")

        val draft = vm.state.value.draft
        assertEquals("", draft.name)
        assertNull(draft.merchant)
    }

    // -------------------------------------------------------------------------
    // fromNotification — name seeded from merchantRaw (via initial state)
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification seeds draft name from notification merchantRaw`() = runTest {
        // makeNotification sets merchantRaw = "IFOOD"; fromNotification now seeds both
        // name and merchant from that field so the Descrição field opens pre-filled
        val notification = makeNotification() // merchantRaw = "IFOOD"
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("IFOOD", vm.state.value.draft.name)
        assertEquals("IFOOD", vm.state.value.draft.merchant)
    }

    // -------------------------------------------------------------------------
    // Item-axis navigation: queue + skipToNext / skipToPrevious
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification populates queue from getPendingReview`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(
                makeNotification(id = "notif-1"),
                makeNotification(id = "notif-2"),
                makeNotification(id = "notif-3"),
            ),
        )

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("notif-1", "notif-2", "notif-3"), vm.state.value.queue)
    }

    @Test
    fun `skipToNext loads the next pending id in place, wrapping after the last`() = runTest {
        val notification = makeNotification(id = "notif-3")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-3") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-3", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(
                makeNotification(id = "notif-1"),
                makeNotification(id = "notif-2"),
                makeNotification(id = "notif-3"),
            ),
        )

        val vm = makeViewModel(notificationId = "notif-3")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipToNext()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-1", vm.state.value.notification?.id)
    }

    @Test
    fun `skipToPrevious loads the previous pending id in place, wrapping before the first`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(
                makeNotification(id = "notif-1"),
                makeNotification(id = "notif-2"),
                makeNotification(id = "notif-3"),
            ),
        )

        val vm = makeViewModel(notificationId = "notif-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipToPrevious()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-3", vm.state.value.notification?.id)
    }

    @Test
    fun `skipToNext does nothing when queue has less than 2 items`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(makeNotification(id = "notif-1")),
        )

        val vm = makeViewModel(notificationId = "notif-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.skipToNext()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-1", vm.state.value.notification?.id)
    }

    @Test
    fun `skipToNext is a no-op while isSaving`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(
                makeNotification(id = "notif-1"),
                makeNotification(id = "notif-2"),
            ),
        )
        // A save in flight that never completes keeps isSaving = true.
        coEvery { transactionRepository.save(any(), any()) } coAnswers { awaitCancellation() }

        val vm = makeViewModel(notificationId = "notif-1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isSaving)

        vm.skipToNext()

        assertEquals("notif-1", vm.state.value.notification?.id)
    }

    // -------------------------------------------------------------------------
    // Span token selection: tapToken / assignRoleToSelection / removeRoleFromSelection
    // -------------------------------------------------------------------------

    private fun spanTokens() = listOf(
        Token(text = "PERSON"),
        Token(text = "BLACK"),
        Token(text = "CASHBAC"),
        Token(text = "final"),
        Token(text = "3685"),
    )

    private fun makeSpanViewModel(tokens: List<Token> = spanTokens()): WizardViewModel {
        val notification = makeNotification(tokens = tokens)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `tapToken starts a length-1 selection`() = runTest {
        val vm = makeSpanViewModel()

        vm.tapToken(2)

        assertEquals(2..2, vm.state.value.selectionRange)
    }

    @Test
    fun `tapToken then tapToken extends the selection and keeps anchor sticky`() = runTest {
        val vm = makeSpanViewModel()

        vm.tapToken(1)
        vm.tapToken(3)
        assertEquals(1..3, vm.state.value.selectionRange)

        // Tapping a third token recomputes against the original anchor (index 1).
        vm.tapToken(0)
        assertEquals(0..1, vm.state.value.selectionRange)
    }

    @Test
    fun `assignRoleToSelection MERCHANT over a span tags every token and sets draft`() = runTest {
        val vm = makeSpanViewModel()

        vm.tapToken(0)
        vm.tapToken(4)
        vm.assignRoleToSelection(TokenRole.MERCHANT)

        val joined = "PERSON BLACK CASHBAC final 3685"
        val state = vm.state.value
        state.tokens.forEach { token ->
            assertEquals(TokenRole.MERCHANT, token.role)
            assertEquals(joined, token.value)
        }
        assertEquals(joined, state.draft.name)
        assertEquals(joined, state.draft.merchant)
        assertNull(state.selectionRange)
    }

    @Test
    fun `assignRoleToSelection clears the same role from tokens outside the new span`() = runTest {
        val vm = makeSpanViewModel()

        // First tag token 0 as MERCHANT.
        vm.tapToken(0)
        vm.assignRoleToSelection(TokenRole.MERCHANT)
        assertEquals(TokenRole.MERCHANT, vm.state.value.tokens[0].role)

        // Now tag a different span as MERCHANT — token 0 must lose the role.
        vm.tapToken(2)
        vm.tapToken(3)
        vm.assignRoleToSelection(TokenRole.MERCHANT)

        val tokens = vm.state.value.tokens
        assertNull(tokens[0].role)
        assertNull(tokens[0].value)
        assertEquals(TokenRole.MERCHANT, tokens[2].role)
        assertEquals(TokenRole.MERCHANT, tokens[3].role)
        assertEquals("CASHBAC final", vm.state.value.draft.name)
    }

    @Test
    fun `tapToken on an assigned token selects its whole contiguous run`() = runTest {
        val vm = makeSpanViewModel()

        vm.tapToken(1)
        vm.tapToken(3)
        vm.assignRoleToSelection(TokenRole.MERCHANT)

        // Tapping any token within the run reselects the full run bounds.
        vm.tapToken(2)

        assertEquals(1..3, vm.state.value.selectionRange)
    }

    @Test
    fun `removeRoleFromSelection clears the span and merchant but keeps the description`() = runTest {
        val vm = makeSpanViewModel()

        vm.tapToken(0)
        vm.tapToken(2)
        vm.assignRoleToSelection(TokenRole.MERCHANT)

        // Re-select the run (edit mode) and remove.
        vm.tapToken(1)
        vm.removeRoleFromSelection()

        val state = vm.state.value
        listOf(0, 1, 2).forEach { i ->
            assertNull(state.tokens[i].role)
            assertNull(state.tokens[i].value)
        }
        // merchant (the hint) is cleared, but name (the user-editable Descrição) is preserved.
        assertNull(state.draft.merchant)
        assertEquals("PERSON BLACK CASHBAC", state.draft.name)
        assertNull(state.selectionRange)
    }

    @Test
    fun `assignRoleToSelection AMOUNT parses the joined span into draft amount`() = runTest {
        val vm = makeSpanViewModel(
            tokens = listOf(Token(text = "RS"), Token(text = "30,96")),
        )

        vm.tapToken(0)
        vm.tapToken(1)
        vm.assignRoleToSelection(TokenRole.AMOUNT)

        assertEquals(BigDecimal("30.96"), vm.state.value.draft.amount)
    }

    // -------------------------------------------------------------------------
    // CardLast4 prefill — loadNotification
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification prefills CREDIT and cardId when hint last4 matches known card`() = runTest {
        val notification = makeNotification(paymentHint = "final 3685")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-a" to "3685")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-a", draft.cardId)
        assertNull(vm.state.value.unknownCardLast4)
    }

    @Test
    fun `loadNotification sets unknownCardLast4 when hint last4 not in map`() = runTest {
        val notification = makeNotification(paymentHint = "final 9999")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-a" to "1234")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("9999", vm.state.value.unknownCardLast4)
        // payment method is NOT changed when the card is unknown
        assertNull(vm.state.value.draft.paymentMethod)
    }

    @Test
    fun `loadNotification does not prefill when paymentHint is cartao`() = runTest {
        val notification = makeNotification(paymentHint = "cartão")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.draft.paymentMethod)
        assertNull(vm.state.value.unknownCardLast4)
    }

    @Test
    fun `loadNotification does not prefill when paymentHint is null`() = runTest {
        val notification = makeNotification(paymentHint = null)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.draft.paymentMethod)
        assertNull(vm.state.value.unknownCardLast4)
    }

    @Test
    fun `loadNotification does not stamp cardId on an INCOME draft even when last4 matches`() = runTest {
        // A "final NNNN" hint on an INCOME notification must not leak a cardId: the credit guard
        // no-ops withPaymentMethod(CREDIT) for income, so the card must not be copied either.
        val notification = makeNotification(type = TransactionType.INCOME, paymentHint = "final 3685")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-a" to "3685")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.draft.cardId)
        assertNotEquals(PaymentMethod.CREDIT, vm.state.value.draft.paymentMethod)
    }

    @Test
    fun `loadNotification resolves the card from the last4 the notification itself names`() = runTest {
        val notification = makeNotification(
            paymentHint = "final 3685",
            paymentMethod = PaymentMethod.CREDIT,
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-a" to "3685")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("card-a", vm.state.value.draft.cardId)
        assertNull(vm.state.value.unknownCardLast4)
    }

    private fun TestScope.loadedDraftCard(
        notification: NotificationItem,
        cards: List<CreditCard>,
    ): String? {
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { cardRepository.getCards() } returns Result.success(cards)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        return vm.state.value.draft.cardId
    }

    @Test
    fun `loadNotification resolves the card from the issuer when there is no last4 hint`() = runTest {
        val notification = makeNotification(paymentMethod = PaymentMethod.CREDIT)

        val cardId = loadedDraftCard(notification, listOf(makeCreditCard("card-itau").copy(name = "Itaú")))

        assertEquals("card-itau", cardId)
    }

    @Test
    fun `loadNotification leaves the card unset when neither last4 nor issuer resolves`() = runTest {
        val notification = makeNotification(app = "Banco Desconhecido", paymentMethod = PaymentMethod.CREDIT)

        val cardId = loadedDraftCard(notification, listOf(makeCreditCard("card-itau").copy(name = "Itaú")))

        assertNull(cardId)
    }

    @Test
    fun `loadNotification leaves the card unset when the issuer is ambiguous`() = runTest {
        val notification = makeNotification(paymentMethod = PaymentMethod.CREDIT)
        val twins = listOf(
            makeCreditCard("card-itau-1").copy(name = "Itaú"),
            makeCreditCard("card-itau-2").copy(name = "Itaú"),
        )

        assertNull(loadedDraftCard(notification, twins))
    }

    // -------------------------------------------------------------------------
    // CardLast4 prefill — assignLast4ToCard and dismissUnknownCard
    // -------------------------------------------------------------------------

    @Test
    fun `assignLast4ToCard associates and applies CREDIT prefill then clears unknownCardLast4`() = runTest {
        val notification = makeNotification(paymentHint = "final 9999")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns emptyMap()

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("9999", vm.state.value.unknownCardLast4)

        vm.assignLast4ToCard("card-b")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { cardLast4Repository.associate("card-b", "9999") }
        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-b", draft.cardId)
        assertNull(vm.state.value.unknownCardLast4)
    }

    @Test
    fun `dismissUnknownCard clears unknownCardLast4 without changing draft`() = runTest {
        val notification = makeNotification(paymentHint = "final 9999")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns emptyMap()

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("9999", vm.state.value.unknownCardLast4)
        val draftBefore = vm.state.value.draft

        vm.dismissUnknownCard()

        assertNull(vm.state.value.unknownCardLast4)
        assertEquals(draftBefore, vm.state.value.draft)
    }

    // -------------------------------------------------------------------------
    // learnRuleIfRequested — one pattern, one tag, create-only
    // -------------------------------------------------------------------------

    private fun makeCategoryTag(
        id: String = "tag-cat",
        idContext: String? = "ctx-food",
        kind: TransactionType = TransactionType.EXPENSE,
    ) = Tag(id = id, name = "Tag $id", kind = kind, idContext = idContext)

    /** Loads a notification whose text names RAPPI, with [catalog] as the tag catalog. */
    private fun TestScope.teachViewModel(
        catalog: List<Tag>,
        base: NotificationItem = makeNotification(id = "notif-1").copy(text = "Compra RAPPI aprovada R$ 49,90"),
    ): WizardViewModel {
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns
            Result.success(ClassifiedNotification(notification = base, pendingTransactionId = null))
        coEvery { tagRepository.getAllTags() } returns Result.success(catalog)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun TestScope.saveTeaching(vm: WizardViewModel, vararg picked: String, onDone: () -> Unit = {}) {
        picked.forEach(vm::toggleTag)
        vm.toggleLearnRule(true)
        vm.updateName("RAPPI")
        vm.save(onDone = onDone)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `teach creates one SUGGEST rule carrying the pattern and the tag`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        saveTeaching(vm, "tag-cat")

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { it.action == RuleAction.SUGGEST && it.pattern == "RAPPI" && it.idTag == "tag-cat" },
            )
        }
        coVerify(exactly = 0) { classificationRuleRepository.update(any()) }
    }

    @Test
    fun `teach never loads the existing rules`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        saveTeaching(vm, "tag-cat")

        coVerify(exactly = 0) { classificationRuleRepository.getAll() }
    }

    @Test
    fun `teach takes the tag the user picked first, not the first one in the catalog`() = runTest {
        // Regression: the catalog order is [tag-a, tag-b, tag-c]; the user picks c, then a.
        val catalog = listOf(makeCategoryTag("tag-a"), makeCategoryTag("tag-b"), makeCategoryTag("tag-c"))
        val vm = teachViewModel(catalog)

        saveTeaching(vm, "tag-c", "tag-a")

        coVerify(exactly = 1) { classificationRuleRepository.create(match { it.idTag == "tag-c" }) }
    }

    @Test
    fun `teach works for an expense tag with no context`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-loose", idContext = null)))

        saveTeaching(vm, "tag-loose")

        coVerify(exactly = 1) { classificationRuleRepository.create(match { it.idTag == "tag-loose" }) }
    }

    @Test
    fun `teach is skipped when the only selected tag is an income tag`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-inc", idContext = null, kind = TransactionType.INCOME)))

        saveTeaching(vm, "tag-inc")

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `a duplicate rule is disclosed and the save completes`() = runTest {
        coEvery { classificationRuleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Duplicate)
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        var done = false
        val messages = relayedMessages { saveTeaching(vm, "tag-cat", onDone = { done = true }) }

        assertTrue(done)
        assertEquals(listOf("Lançado ✓ · a regra já existia."), messages)
    }

    @Test
    fun `a refused rule reports the server's reason instead of doing nothing`() = runTest {
        // The rule cap is only knowable after the write, and the transaction is already saved.
        coEvery { classificationRuleRepository.create(any()) } returns
            Result.success(RuleWriteOutcome.Rejected("Você já tem 500 regras."))
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        val messages = relayedMessages { saveTeaching(vm, "tag-cat") }

        assertEquals(listOf("Lançado ✓ · regra não criada: Você já tem 500 regras."), messages)
    }

    @Test
    fun `a rule that saved says nothing`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        val messages = relayedMessages { saveTeaching(vm, "tag-cat") }

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a failed rule create is swallowed and the save completes`() = runTest {
        coEvery { classificationRuleRepository.create(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))

        var done = false
        saveTeaching(vm, "tag-cat", onDone = { done = true })

        assertTrue(done)
        coVerify(exactly = 1) { transactionRepository.save(any(), any()) }
    }

    @Test
    fun `teach is skipped when the toggle is off`() = runTest {
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")))
        vm.toggleTag("tag-cat")
        vm.updateName("RAPPI")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `teach falls through a gateway-prefix merchant to the parsed merchant`() = runTest {
        val base = makeNotification(id = "notif-1")
        val notification = base.copy(
            text = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = "PADARIA DE TESTE"),
        )
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")), notification)

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("IFD*")
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { classificationRuleRepository.create(match { it.pattern == "PADARIA DE TESTE" }) }
    }

    @Test
    fun `teach refuses a payment hint of cartao when there is no merchant`() = runTest {
        val base = makeNotification(id = "notif-1", paymentHint = "cartão")
        val notification = base.copy(
            text = "Compra aprovada no cartão R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")), notification)

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `teach refuses a final-digits payment hint when there is no merchant`() = runTest {
        // A SUGGEST rule keyed on one card's last digits would claim every purchase on that card.
        val base = makeNotification(id = "notif-1", paymentHint = "final 3685")
        val notification = base.copy(
            text = "Compra aprovada no cartão final 3685 R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")), notification)

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `teach does not fall through a gateway-prefix merchant to the payment hint`() = runTest {
        val base = makeNotification(id = "notif-1", paymentHint = "cartão")
        val notification = base.copy(
            // "Ifd*" MUST occur in the text, or the containment filter drops it first and the gateway
            // rejection is never what causes the fall-through.
            text = "Compra Ifd* aprovada no cartão R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val vm = teachViewModel(listOf(makeCategoryTag("tag-cat")), notification)

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("Ifd*")
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    // -------------------------------------------------------------------------
    // enabledMethods — payment-method availability config
    // -------------------------------------------------------------------------

    @Test
    fun `enabledMethods in state reflects the repo emission`() = runTest {
        val fakeRepo = FakePaymentMethodPrefsRepository(initial = setOf(PaymentMethod.PIX))
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel(paymentMethodPrefsRepository = fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(PaymentMethod.PIX), vm.state.value.enabledMethods)
    }

    // -------------------------------------------------------------------------
    // paymentPrefilled — drives the payment step's support line
    // -------------------------------------------------------------------------

    private fun loadedWizard(
        notification: NotificationItem,
        prefs: FakePaymentMethodPrefsRepository = fakePaymentMethodPrefsRepository,
    ): WizardViewModel {
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        val vm = makeViewModel(paymentMethodPrefsRepository = prefs)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `paymentPrefilled is false when nothing resolved a method`() = runTest {
        val vm = loadedWizard(makeNotification())

        assertNull(vm.state.value.draft.paymentMethod)
        assertFalse(vm.state.value.paymentPrefilled)
    }

    @Test
    fun `paymentPrefilled is true when the text named the method`() = runTest {
        val vm = loadedWizard(makeNotification(paymentMethod = PaymentMethod.PIX))

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
        assertTrue(vm.state.value.paymentPrefilled)
    }

    @Test
    fun `paymentPrefilled does not flip when the user picks a method`() = runTest {
        val vm = loadedWizard(makeNotification())

        vm.selectPaymentMethod(PaymentMethod.PIX)

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
        assertFalse(vm.state.value.paymentPrefilled)
    }

    @Test
    fun `the sole enabled method is prefilled on a cold load`() = runTest {
        val prefs = FakePaymentMethodPrefsRepository(initial = setOf(PaymentMethod.CASH))

        val vm = loadedWizard(makeNotification(), prefs = prefs)

        assertEquals(PaymentMethod.CASH, vm.state.value.draft.paymentMethod)
        assertTrue(vm.state.value.paymentPrefilled)
    }

    // -------------------------------------------------------------------------
    // learnPaymentMethodIfMarked — dictionary learning on save
    // -------------------------------------------------------------------------

    @Test
    fun `learnPaymentMethodIfMarked_genuineUnknownToken_callsLearn`() = runTest {
        // "eletronico" is not in the builtin parser; a single PAYMENT token + concrete method
        // on the draft must trigger learn("eletronico", CREDIT).
        val tokens = listOf(Token(text = "eletronico", role = TokenRole.PAYMENT))
        val notification = makeNotification(
            paymentMethod = PaymentMethod.CREDIT,
            tokens = tokens,
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { paymentMethodDictionaryRepository.learn("eletronico", PaymentMethod.CREDIT) }
    }

    @Test
    fun `learnPaymentMethodIfMarked_cardHintToken_doesNotCallLearn`() = runTest {
        // A "final 3685" PAYMENT span is a card hint, not a word to learn.
        val tokens = listOf(
            Token(text = "final", role = TokenRole.PAYMENT),
            Token(text = "3685", role = TokenRole.PAYMENT),
        )
        val notification = makeNotification(tokens = tokens)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { paymentMethodDictionaryRepository.learn(any(), any()) }
    }

    @Test
    fun `learnPaymentMethodIfMarked_singleCartaoToken_doesNotCallLearn`() = runTest {
        // A lone "cartão" token is ambiguous (credit or debit) — must never be learned.
        val notification = makeNotification(tokens = listOf(Token(text = "cartão", role = TokenRole.PAYMENT)))
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { paymentMethodDictionaryRepository.learn(any(), any()) }
    }

    @Test
    fun `learnPaymentMethodIfMarked_singleDigitsToken_doesNotCallLearn`() = runTest {
        // A lone digit token (a manually-marked card-number fragment) must never be learned.
        val notification = makeNotification(tokens = listOf(Token(text = "3685", role = TokenRole.PAYMENT)))
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { paymentMethodDictionaryRepository.learn(any(), any()) }
    }

    @Test
    fun `learnPaymentMethodIfMarked_multiTokenNonCardSpan_doesNotCallLearn`() = runTest {
        // v1 skips multi-token PAYMENT spans, even when they are not a card hint.
        val tokens = listOf(
            Token(text = "vale", role = TokenRole.PAYMENT),
            Token(text = "refeição", role = TokenRole.PAYMENT),
        )
        val notification = makeNotification(tokens = tokens)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.selectPaymentMethod(PaymentMethod.CASH)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { paymentMethodDictionaryRepository.learn(any(), any()) }
    }

    @Test
    fun `learnPaymentMethodIfMarked_builtinKnownWord_doesNotCallLearn`() = runTest {
        // "débito" is already known by BrNotificationParser as DEBIT → no need to relearn.
        val tokens = listOf(Token(text = "débito", role = TokenRole.PAYMENT))
        val notification = makeNotification(tokens = tokens)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.DEBIT)
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { paymentMethodDictionaryRepository.learn(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Prefill from learned dictionary
    // -------------------------------------------------------------------------

    @Test
    fun `prefill_learnedToken_setsPaymentMethod`() = runTest {
        // "eletronico" is NOT recognized by the builtin; the learned map maps it to CREDIT.
        val fakeDictRepo = FakePaymentMethodDictionaryRepository(
            initial = mapOf("eletronico" to PaymentMethod.CREDIT),
        )
        val notification = makeNotification()
            .copy(text = "pagamento eletronico aprovado R\$ 50,00")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel(dictionaryRepository = fakeDictRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaymentMethod.CREDIT, vm.state.value.draft.paymentMethod)
    }

    // -------------------------------------------------------------------------
    // Create a tag from the tag step
    // -------------------------------------------------------------------------

    private fun TestScope.loadedViewModel(type: TransactionType?): WizardViewModel {
        val notification = makeNotification(type = type)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification = notification, pendingTransactionId = null))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    /** The create-tag gate needs a context to pick, so the expense create tests need one stubbed. */
    private fun TestScope.taggableExpenseViewModel(): WizardViewModel {
        coEvery { tagRepository.getAllContexts() } returns
            Result.success(listOf(TagContext("ctx-food", "Comida", 1L)))
        return loadedViewModel(TransactionType.EXPENSE)
    }

    @Test
    fun `canCreateTag is false while the draft type is only guessed`() = runTest {
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(TagContext("ctx-food", "Comida", 1L)))
        val vm = loadedViewModel(null)

        assertFalse(vm.state.value.canCreateTag)
    }

    @Test
    fun `canCreateTag is false for an expense draft with no contexts to pick`() = runTest {
        val vm = loadedViewModel(TransactionType.EXPENSE)

        assertFalse(vm.state.value.canCreateTag)
    }

    @Test
    fun `canCreateTag is true for an expense draft once a context exists`() = runTest {
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(TagContext("ctx-food", "Comida", 1L)))
        val vm = loadedViewModel(TransactionType.EXPENSE)

        assertTrue(vm.state.value.canCreateTag)
    }

    @Test
    fun `canCreateTag is true for an income draft with no contexts`() = runTest {
        val vm = loadedViewModel(TransactionType.INCOME)

        assertTrue(vm.state.value.canCreateTag)
    }

    @Test
    fun `openCreateTag seeds the form name from a search query`() = runTest {
        val vm = loadedViewModel(TransactionType.INCOME)

        vm.openCreateTag(null, "Sal")

        assertEquals("Sal", vm.state.value.tagFormInitialName)
    }

    @Test
    fun `openCreateTag on an expense draft opens the add form under the given context`() = runTest {
        val vm = taggableExpenseViewModel()

        vm.openCreateTag("ctx-food")

        assertEquals(TagFormMode.Add("ctx-food"), vm.state.value.tagForm)
    }

    @Test
    fun `openCreateTag on an income draft opens the income form`() = runTest {
        val vm = loadedViewModel(TransactionType.INCOME)

        vm.openCreateTag(null)

        assertEquals(TagFormMode.AddIncome, vm.state.value.tagForm)
    }

    @Test
    fun `openCreateTag refuses to open while the draft type is only guessed`() = runTest {
        coEvery { tagRepository.getAllContexts() } returns
            Result.success(listOf(TagContext("ctx-food", "Comida", 1L)))
        val vm = loadedViewModel(null)
        assertNull(vm.state.value.draft.type)

        vm.openCreateTag("ctx-food")

        assertNull(vm.state.value.tagForm)
    }

    @Test
    fun `openCreateTag refuses to open an expense form with no context to pick`() = runTest {
        val vm = loadedViewModel(TransactionType.EXPENSE)

        vm.openCreateTag(null)

        assertNull(vm.state.value.tagForm)
    }

    @Test
    fun `closeCreateTag clears the form`() = runTest {
        val vm = taggableExpenseViewModel()
        vm.openCreateTag("ctx-food")

        vm.closeCreateTag()

        assertNull(vm.state.value.tagForm)
    }

    @Test
    fun `saveNewTag success adds the tag to allTags and selects it without refetching`() = runTest {
        val vm = taggableExpenseViewModel()
        val created = Tag(id = "tag-new", name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.success(created)
        vm.openCreateTag("ctx-food")

        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(created in state.allTags)
        assertTrue("tag-new" in state.draft.tagIds)
        assertNull(state.tagForm)
        assertFalse(state.isSavingTag)
        assertNotNull(state.toastMessage)
        coVerify(exactly = 1) { tagRepository.getAllTags() }
    }

    @Test
    fun `saveNewTag failure keeps the sheet open with an inline error`() = runTest {
        val vm = taggableExpenseViewModel()
        vm.toggleTag("tag-existing")
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("HTTP 409 Conflict"))
        vm.openCreateTag("ctx-food")

        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("tag-existing"), state.draft.tagIds)
        assertTrue(state.allTags.isEmpty())
        assertNotNull(state.tagForm)
        assertFalse(state.isSavingTag)
        assertNull(state.toastMessage)
        assertTrue(state.tagFormError.orEmpty().contains("HTTP 409 Conflict"))
        assertFalse(state.tagFormError.orEmpty().contains("Não foi possível salvar"))
    }

    @Test
    fun `clearTagFormError drops the inline error and keeps the sheet open`() = runTest {
        val vm = taggableExpenseViewModel()
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openCreateTag("ctx-food")
        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.clearTagFormError()

        assertNull(vm.state.value.tagFormError)
        assertNotNull(vm.state.value.tagForm)
    }

    @Test
    fun `saveNewTag success clears a previous inline error`() = runTest {
        val vm = taggableExpenseViewModel()
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openCreateTag("ctx-food")
        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { tagRepository.createTag(input) } returns
            Result.success(Tag("tag-new", "Mercado", TransactionType.EXPENSE, "ctx-food"))

        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.tagFormError)
        assertNull(vm.state.value.tagForm)
    }

    @Test
    fun `saveNewTag failure after the sheet was dismissed falls back to a toast`() = runTest {
        val vm = taggableExpenseViewModel()
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("HTTP 409 Conflict"))
        vm.openCreateTag("ctx-food")
        vm.saveNewTag(input)

        vm.closeCreateTag()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.tagForm)
        assertNull(state.tagFormError)
        assertFalse(state.isSavingTag)
        assertTrue(state.toastMessage.orEmpty().contains("HTTP 409 Conflict"))
    }

    @Test
    fun `closeCreateTag mid-save releases the double-tap guard`() = runTest {
        val vm = taggableExpenseViewModel()
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.failure(IOException("boom"))
        vm.openCreateTag("ctx-food")
        vm.saveNewTag(input)

        vm.closeCreateTag()

        assertFalse(vm.state.value.isSavingTag)
    }

    @Test
    fun `saveNewTag replaces an existing entry when the backend echoes its id`() = runTest {
        val existing = Tag("tag-1", "Mercado", TransactionType.EXPENSE, "ctx-food")
        coEvery { tagRepository.getAllContexts() } returns
            Result.success(listOf(TagContext("ctx-food", "Comida", 1L)))
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(existing))
        val vm = loadedViewModel(TransactionType.EXPENSE)
        val input = TagInput(name = "Mercado ", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns Result.success(existing.copy(name = "Mercado"))
        vm.openCreateTag("ctx-food")

        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.allTags.count { it.id == "tag-1" })
    }

    @Test
    fun `saveNewTag while a save is in flight creates the tag once`() = runTest {
        val vm = loadedViewModel(TransactionType.EXPENSE)
        val input = TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "ctx-food")
        coEvery { tagRepository.createTag(input) } returns
            Result.success(Tag("tag-new", "Mercado", TransactionType.EXPENSE, "ctx-food"))

        vm.saveNewTag(input)
        vm.saveNewTag(input)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.createTag(any()) }
    }

    private fun TestScope.learnWithNewTag(created: Tag, type: TransactionType) {
        val notification = makeNotification(type = type, text = "Compra RAPPI aprovada R$ 49,90")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns
            Result.success(ClassifiedNotification(notification = notification, pendingTransactionId = null))
        coEvery { transactionRepository.save(any(), any()) } returns Result.success("tx-new")
        coEvery { tagRepository.createTag(any()) } returns Result.success(created)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.saveNewTag(TagInput(created.name, created.kind, created.idContext, created.color))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.toggleLearnRule(true)
        vm.selectPaymentMethod(PaymentMethod.PIX)
        vm.updateName("RAPPI")
        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `a newly created expense tag is taught to the rule`() = runTest {
        val created = Tag("tag-new", "Mercado", TransactionType.EXPENSE, idContext = "ctx-food")

        learnWithNewTag(created, TransactionType.EXPENSE)

        coVerify(exactly = 1) {
            classificationRuleRepository.create(match { rule -> rule.idTag == "tag-new" })
        }
    }

    @Test
    fun `a newly created income tag is deliberately not taught to the rule`() = runTest {
        val created = Tag("tag-inc", "Salário", TransactionType.INCOME, color = 0xFF00AA00L)

        learnWithNewTag(created, TransactionType.INCOME)

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
        coVerify(exactly = 0) { classificationRuleRepository.update(any()) }
    }
}
