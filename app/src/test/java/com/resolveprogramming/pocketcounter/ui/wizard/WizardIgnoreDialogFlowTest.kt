package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.data.local.AppMessageRelay
import com.resolveprogramming.pocketcounter.data.repository.BlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The offered ignore option is derived from the draft, so it can change while the dialog is closed.
 * Taps go through the OnClick semantics action: Robolectric does not deliver injected pointer
 * events to the dialog window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class WizardIgnoreDialogFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val notificationRepository: NotificationRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val seriesRepository: SeriesRepository = mockk()
    private val classificationRuleRepository: ClassificationRuleRepository = mockk()
    private val cardLast4Repository: CardLast4Repository = mockk()
    private val blockedSourceRepository: BlockedSourceRepository = mockk()

    /** A blockable push whose text names a merchant the parser did not extract. */
    private val notification = NotificationItem(
        id = "notif-1",
        app = "Google",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "10:00",
        text = "Netflix R$23.25",
        status = NotificationStatus.NEEDS_REVIEW,
        parsed = ParsedNotification(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("23.25"),
            date = LocalDate.of(2026, 8, 20),
            merchantRaw = null,
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(tagIds = emptyList()),
        tokens = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { seriesRepository.getAll() } returns Result.success(emptyList())
        coEvery { notificationRepository.getById(any()) } returns Result.success(notification)
        coEvery { notificationRepository.classify(any(), any()) } returns
            Result.success(ClassifiedNotification(notification = notification, pendingTransactionId = null))
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { cardLast4Repository.getMap() } returns emptyMap()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WizardViewModel(
        savedStateHandle = SavedStateHandle(mapOf("notificationId" to "notif-1")),
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
        paymentMethodPrefsRepository = FakePaymentMethodPrefsRepository(),
        paymentMethodDictionaryRepository = FakePaymentMethodDictionaryRepository(),
        blockedSourceRepository = blockedSourceRepository,
        appMessageRelay = AppMessageRelay(),
    )

    private fun tap(text: String) =
        compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.OnClick)

    private fun openIgnoreDialog() = compose
        .onNodeWithText("Ignorar")
        .performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `a learn choice made for a pattern does not carry into a source block`() {
        val vm = viewModel()
        compose.setContent { PocketTheme { WizardScreen(onDismiss = {}, onBackToApp = {}, viewModel = vm) } }

        vm.updateName("Netflix")
        compose.waitForIdle()

        openIgnoreDialog()
        tap("Ignorar sempre \"Netflix\"")
        compose.onNodeWithText("Ignorar sempre \"Netflix\"", substring = true).assertIsSelected()
        tap("Cancelar")

        // Clearing the description drops the pattern, so the only option left is blocking the app.
        vm.updateName("")
        compose.waitForIdle()
        openIgnoreDialog()

        compose.onNodeWithText("Só esta notificação", substring = true).assertIsSelected()
        compose
            .onNodeWithText("Nunca capturar notificações do Google", substring = true)
            .assertIsNotSelected()
    }
}
