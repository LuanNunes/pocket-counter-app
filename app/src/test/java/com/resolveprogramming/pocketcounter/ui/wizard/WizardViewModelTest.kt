package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.Tag
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
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.math.BigDecimal
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
    private val fakePaymentMethodPrefsRepository = FakePaymentMethodPrefsRepository()
    private val paymentMethodDictionaryRepository: PaymentMethodDictionaryRepository = mockk()

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
        coEvery { classificationRuleRepository.create(any()) } returns Result.success(Unit)
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())
        coEvery { classificationRuleRepository.update(any()) } returns Result.success(Unit)
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
        cardId: String? = null,
        tagIds: List<String> = emptyList(),
        tokens: List<Token> = emptyList(),
        paymentHint: String? = null,
    ) = NotificationItem(
        id = id,
        app = "Banco Itaú",
        channel = NotificationChannel.SMS,
        time = "agora",
        received = "10:00",
        text = "Compra aprovada R$ 49,90",
        status = status,
        parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = LocalDate.of(2026, 6, 12),
            merchantRaw = "IFOOD",
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(
            tagIds = tagIds,
            paymentMethod = paymentMethod,
            cardId = cardId,
        ),
        tokens = tokens,
    )

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
            paymentMethodPrefsRepository = paymentMethodPrefsRepository,
            paymentMethodDictionaryRepository = dictionaryRepository,
        )
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
            cardId = "card-abc",
            tagIds = listOf("tag-1"),
        )
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
    fun `loadNotification classify failure sets non-null error message`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("classify failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertEquals("classify failed", vm.state.value.error)
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", "tx-new-99") }
    }

    @Test
    fun `save calls onDone after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("HTTP 400 Bad Request"))

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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException())

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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

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
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

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
        val notification = makeNotification(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        val notification = makeNotification(tagIds = listOf("t1"))
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
            transactionRepository.save(any())
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        // notification has no suggested tagIds → draft.tagIds is empty
        val notification = makeNotification(tagIds = emptyList())
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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

        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify(exactly = 1) { notificationRepository.markClassified(any(), any()) }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo still succeeds when linkTransaction fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-99")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-99")
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
    // ignore() path
    // -------------------------------------------------------------------------

    @Test
    fun `ignore marks notification ignored and advances in place to next pending item`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val nextNotification = makeNotification(id = "notif-2")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.ignore(learn = false, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertEquals("notif-2", vm.state.value.notification?.id)
        assertFalse(doneCalled)
    }

    @Test
    fun `ignore calls onDone when no more pending notifications`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.ignore(learn = false, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertTrue(doneCalled)
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

    @Test
    fun `loadNotification does not override a rule-provided method with the text mention`() = runTest {
        // Rule suggests PIX; the text says "débito" — the rule wins, text is only a fallback.
        val notification = makeNotification(id = "notif-1", paymentMethod = PaymentMethod.PIX)
            .copy(text = "Compra no débito aprovada R$ 14,42")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns emptyMap()

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
    }

    @Test
    fun `ignore with learn creates an IGNORE rule from the merchant and marks ignored`() = runTest {
        // Merchant "IFOOD" appears in the text, so it becomes the CONTAINS pattern.
        val notification = makeNotification(id = "notif-1").copy(text = "Compra IFOOD aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(learn = true, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { it.action == RuleAction.IGNORE && it.patterns == listOf("IFOOD") && it.tags.isEmpty() },
            )
        }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore with learn but no derivable pattern still ignores without a rule`() = runTest {
        // Default text "Compra aprovada R$ 49,90" does NOT contain the merchant "IFOOD" → no pattern.
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(learn = true, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore with learn accepts a bare gateway prefix as pattern`() = runTest {
        val base = makeNotification(id = "notif-1")
        val notification = base.copy(
            text = "Compra IFD*APROVADO R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = "Ifd*"),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(learn = true, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { it.action == RuleAction.IGNORE && it.patterns == listOf("Ifd*") && it.tags.isEmpty() },
            )
        }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore with learn keeps the payment hint as pattern when no merchant candidate exists`() = runTest {
        val base = makeNotification(id = "notif-1", paymentHint = "final 3685")
        val notification = base.copy(
            text = "Compra aprovada no cartão final 3685 R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(learn = true, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { it.action == RuleAction.IGNORE && it.patterns == listOf("final 3685") && it.tags.isEmpty() },
            )
        }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
    }

    @Test
    fun `ignore with learn refuses a bare card-word payment hint`() = runTest {
        // "conta" is emitted for the INCOME phrase "crédito em conta" too, so an IGNORE rule keyed on
        // it would drop incoming money out of the review queue.
        val base = makeNotification(id = "notif-1", paymentHint = "conta")
        val notification = base.copy(
            text = "Crédito em conta R$ 1.200,00",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.ignore(learn = true, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
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
        coEvery { transactionRepository.save(any()) } coAnswers { awaitCancellation() }

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
    fun `loadNotification does not override existing CREDIT plus cardId from classification`() = runTest {
        // Classification already returned CREDIT+card-from-rule — last4 map should not override it.
        val notification = makeNotification(
            paymentHint = "final 3685",
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-from-rule",
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { cardLast4Repository.getMap() } returns mapOf("card-a" to "3685")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // draft must keep the classification result, not be overridden by the last4 match
        assertEquals("card-from-rule", vm.state.value.draft.cardId)
        assertNull(vm.state.value.unknownCardLast4)
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
    // learnRuleIfRequested — merge via RuleTeachPlanner
    // -------------------------------------------------------------------------

    /** A tag with a non-blank idContext so it survives the ruleTags filter. */
    private fun makeCategoryTag(
        id: String = "tag-cat",
        idContext: String = "ctx-food",
    ) = Tag(id = id, name = "Alimentação", kind = TransactionType.EXPENSE, idContext = idContext)

    private fun makeExistingRule(
        id: String = "rule-existing",
        patterns: List<String> = listOf("IFOOD"),
        tags: List<Tag> = listOf(makeCategoryTag()),
    ) = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = "CONTAINS",
        active = true,
        appliedCount = 0,
        transactionType = TransactionType.EXPENSE,
        paymentMethod = null,
        cardId = null,
        tags = tags,
        action = RuleAction.SUGGEST,
    )

    @Test
    fun `learnRuleIfRequested_noRuleMatchesNotification_callsCreate_withDraftPaymentAndCard`() = runTest {
        // Notification text must contain the merchant so learnPattern resolves it
        val notification = makeNotification(id = "notif-1").copy(text = "Compra RAPPI aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        // Return the category tag from the tag repository so the ViewModel has it in allTags
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))

        // No existing rules → TeachPlanner will Create
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle tag, enable learn rule, and mark the draft's own payment method/card
        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.selectCard("card-x")
        // Update name/merchant to RAPPI (appears in the notification text)
        vm.updateName("RAPPI")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { rule ->
                    rule.paymentMethod == PaymentMethod.CREDIT &&
                        rule.cardId == "card-x" &&
                        rule.patterns.contains("RAPPI") &&
                        rule.action == RuleAction.SUGGEST
                },
            )
        }
        coVerify(exactly = 0) { classificationRuleRepository.update(any()) }
    }

    @Test
    fun `learnRuleIfRequested_ruleMatchingNotificationExists_callsUpdate_withPatternCompacted_notCreate`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra RAPPI DELIVERY aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))

        // Tag id differs from the taught one, otherwise the plan collapses to NoOp and neither
        // repository call fires.
        val existingRule = makeExistingRule(
            id = "rule-existing",
            patterns = listOf("RAPPI"),
            tags = listOf(makeCategoryTag(id = "tag-other", idContext = "ctx-food")),
        )
        coEvery { classificationRuleRepository.getAll() } returns Result.success(listOf(existingRule))

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("RAPPI DELIVERY")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        // Must call update, not create
        coVerify(exactly = 1) {
            classificationRuleRepository.update(
                match { rule ->
                    rule.id == "rule-existing" &&
                        rule.patterns == listOf("RAPPI")
                },
            )
        }
        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `learnRuleIfRequested_draftMerchantIsAGatewayPrefix_fallsThroughToParsedMerchantRaw_callsCreate`() = runTest {
        val base = makeNotification(id = "notif-1")
        val notification = base.copy(
            text = "Compra IFD*PADARIA DE TESTE aprovada R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = "PADARIA DE TESTE"),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("IFD*")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { rule -> rule.patterns == listOf("PADARIA DE TESTE") },
            )
        }
    }

    @Test
    fun `learnRuleIfRequested_noMerchantCandidates_paymentHintIsCartao_doesNotCreateRule`() = runTest {
        // With no merchant candidate left, the hint is all that could reach the rule — and "cartão"
        // would match nearly every card notification.
        val base = makeNotification(id = "notif-1", paymentHint = "cartão")
        val notification = base.copy(
            text = "Compra aprovada no cartão R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `learnRuleIfRequested_noMerchantCandidates_paymentHintIsFinalDigits_doesNotCreateRule`() = runTest {
        // A SUGGEST rule keyed on one card's last digits would claim every purchase on that card.
        val base = makeNotification(id = "notif-1", paymentHint = "final 3685")
        val notification = base.copy(
            text = "Compra aprovada no cartão final 3685 R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `learnRuleIfRequested_draftMerchantIsGatewayPrefix_doesNotFallThroughToPaymentHint`() = runTest {
        val base = makeNotification(id = "notif-1", paymentHint = "cartão")
        val notification = base.copy(
            // "Ifd*" MUST occur in the text, or the containment filter drops it first and the gateway
            // rejection is never what causes the fall-through.
            text = "Compra Ifd* aprovada no cartão R$ 49,90",
            parsed = base.parsed.copy(merchantRaw = null),
        )
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("Ifd*")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
    }

    @Test
    fun `learnRuleIfRequested_sameContextRuleExists_butPatternDoesNotMatchNotification_callsCreate`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra RAPPI aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))

        // Existing rule shares the taught tag's context, but its pattern ("IFOOD") doesn't appear
        // anywhere in this notification's text ("Compra RAPPI aprovada...") — must NOT be targeted.
        val existingRule = makeExistingRule(
            id = "rule-existing",
            patterns = listOf("IFOOD"),
            tags = listOf(categoryTag),
        )
        coEvery { classificationRuleRepository.getAll() } returns Result.success(listOf(existingRule))

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("RAPPI")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            classificationRuleRepository.create(
                match { rule -> rule.patterns.contains("RAPPI") },
            )
        }
        coVerify(exactly = 0) { classificationRuleRepository.update(any()) }
    }

    @Test
    fun `learnRuleIfRequested_getAllFails_neitherCreatesNorUpdates`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra RAPPI aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))

        coEvery { classificationRuleRepository.getAll() } returns Result.failure(RuntimeException("boom"))

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("RAPPI")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        // A failed rule load must NOT be treated as "no rules exist" — that would mint a duplicate.
        coVerify(exactly = 0) { classificationRuleRepository.create(any()) }
        coVerify(exactly = 0) { classificationRuleRepository.update(any()) }
    }

    @Test
    fun `learnRuleIfRequested_consultesGetAll_beforeDeciding`() = runTest {
        val notification = makeNotification(id = "notif-1").copy(text = "Compra RAPPI aprovada R$ 49,90")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val categoryTag = makeCategoryTag(id = "tag-cat", idContext = "ctx-food")
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(categoryTag))
        coEvery { classificationRuleRepository.getAll() } returns Result.success(emptyList())

        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleTag("tag-cat")
        vm.toggleLearnRule(true)
        vm.updateName("RAPPI")

        vm.save(onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        // getAll must have been called (we don't care about exact count, just that it was)
        coVerify(atLeast = 1) { classificationRuleRepository.getAll() }
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
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

    @Test
    fun `prefill_existingMethod_notOverriddenByLearnedDictionary`() = runTest {
        // Classification already resolved PIX; the learned map has "eletronico"→CREDIT in the text
        // but the existing method must not be replaced.
        val fakeDictRepo = FakePaymentMethodDictionaryRepository(
            initial = mapOf("eletronico" to PaymentMethod.CREDIT),
        )
        val notification = makeNotification(paymentMethod = PaymentMethod.PIX)
            .copy(text = "pagamento eletronico aprovado R\$ 50,00")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel(dictionaryRepository = fakeDictRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
    }
}
