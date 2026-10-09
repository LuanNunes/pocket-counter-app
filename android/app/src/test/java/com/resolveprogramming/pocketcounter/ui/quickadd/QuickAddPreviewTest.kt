package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.resolveprogramming.pocketcounter.domain.model.CardCandidate
import com.resolveprogramming.pocketcounter.domain.model.CardResolution
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.IntentReading
import com.resolveprogramming.pocketcounter.domain.model.NewTagRequest
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/** The preview is the feature's contract with the user: what it says is what gets filed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAddPreviewTest {

    @get:Rule
    val compose = createComposeRule()

    private val card = CreditCard(
        id = "c1",
        name = "Cartão A",
        brand = "VISA",
        last4 = "0000",
        gradientStart = 0xFF000000,
        gradientEnd = 0xFF000000,
        limit = BigDecimal.ZERO,
        billDay = 10,
    )

    private fun state(
        source: Map<IntentField, ValueSource> = emptyMap(),
        idCategory: String? = null,
        draft: WizardDraft = WizardDraft(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("150.00"),
            date = LocalDate.now(),
            name = "Mercado",
        ),
        // Situação gates Lançar, so every test about something else starts with it chosen.
        defined: Set<IntentField> = setOf(IntentField.STATUS),
    ) = QuickAddUiState(
        stage = QuickAddStage.PREVIEW,
        draft = draft,
        defined = defined,
        cards = listOf(card),
        intent = TransactionIntent(
            reading = IntentReading(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("150.00"),
                date = LocalDate.now(),
                name = "Mercado",
                paymentMethod = null,
            ),
            source = source,
            cardStatus = CardResolution.RESOLVED,
            resolvedCard = CardCandidate("c1", "Cartão A"),
            cardCandidates = emptyList(),
            idTag = null,
            idCategory = idCategory,
            missing = emptyList(),
        ),
    )

    private fun render(state: QuickAddUiState) {
        compose.setContent {
            PocketTheme {
                Column(Modifier.fillMaxSize()) {
                    QuickAddPreviewStage(state = state, callbacks = noopCallbacks())
                }
            }
        }
    }

    private var opened = 0
    private var pickedStatus: PaymentStatus? = null
    private var createRequest: NewTagRequest? = null
    private var retries = 0

    private fun noopCallbacks() = QuickAddPreviewCallbacks(
        onToggleRow = {},
        onAmount = {},
        onName = {},
        onDate = {},
        onType = {},
        onPaymentMethod = {},
        onPaymentStatus = { pickedStatus = it },
        onCard = {},
        onToggleTag = {},
        onOpenNewTag = { opened += 1 },
        onCloseNewTag = {},
        onCreateTag = { createRequest = it },
        onTeachEnabled = {},
        onTeachPattern = {},
        onSave = {},
        onSaveAnyway = {},
        onDismissDuplicate = {},
        onEditSentence = {},
        onRetryLookups = { retries += 1 },
    )

    @Test
    fun `one sentence can badge the card and the payment method differently`() {
        render(
            state(
                source = mapOf(
                    IntentField.CARD to ValueSource.WRITTEN,
                    IntentField.PAYMENT_METHOD to ValueSource.INFERRED,
                ),
                draft = WizardDraft(
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("320.00"),
                    date = LocalDate.now(),
                    name = "Gasolina",
                    paymentMethod = PaymentMethod.CREDIT,
                    cardId = "c1",
                ),
            ),
        )

        // The sentence named the card, so that row is silent; the method was guessed, so it is badged.
        compose.onNodeWithContentDescription("Cartão: Cartão A.").assertExists()
        compose.onNodeWithContentDescription("Forma de Pagamento: Crédito, assumido.").assertExists()
        compose.onAllNodesWithText("DA FRASE").assertCountEquals(0)
    }

    @Test
    fun `the date quick-fill chips need no tap`() {
        render(state())

        compose.onNodeWithText("Anteontem").assertExists()
        compose.onNodeWithText("Ontem").assertExists()
        compose.onNodeWithText("Outra data…").assertExists()
    }

    @Test
    fun `Lancar is enabled with no payment method and no category`() {
        render(state())

        compose.onNodeWithContentDescription("Forma de Pagamento: não informado.").assertExists()
        compose.onNodeWithContentDescription("Categoria: sem categoria.").assertExists()
        compose.onNodeWithText("Lançar").assertIsEnabled()
    }

    @Test
    fun `the situacao row assumes pago and never blocks Lancar`() {
        render(state(defined = emptySet()))

        compose.onNodeWithContentDescription("Situação: Pago, assumido.").assertExists()
        compose.onNodeWithText("Lançar").assertIsEnabled()
    }

    @Test
    fun `the situacao options need no tap to appear`() {
        render(state(defined = emptySet()))

        // The row names the status too, so the pills are the selectable ones.
        compose.onAllNodesWithText("Pago").filterToOne(isSelectable()).assertIsSelected()
        compose.onAllNodesWithText("Pendente").filterToOne(isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `picking pendente reports that choice`() {
        render(state(defined = emptySet()))

        compose.onNodeWithText("Pendente").performScrollTo().performClick()

        assertEquals(PaymentStatus.PENDING, pickedStatus)
    }

    @Test
    fun `a chosen situacao badges definido`() {
        render(
            state(
                draft = WizardDraft(
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("300.00"),
                    date = LocalDate.now(),
                    name = "Dedetização",
                    statusPayment = PaymentStatus.PAID,
                ),
            ),
        )

        compose.onNodeWithContentDescription("Situação: Pago, definido.").assertExists()
        compose.onNodeWithText("Lançar").assertIsEnabled()
    }

    @Test
    fun `a value the user typed badges definido`() {
        render(state().copy(defined = setOf(IntentField.AMOUNT)))

        val amount = formatBrl(BigDecimal("150.00"))

        compose.onNodeWithContentDescription("Valor: $amount, definido.").assertExists()
    }

    @Test
    fun `the Nova tag pill sits among the category picks`() {
        render(
            state().copy(
                expandedRow = IntentField.TAG,
                tags = listOf(Tag("t1", "Supermercado", TransactionType.EXPENSE, idContext = "ctx")),
                contexts = listOf(TagContext("ctx", "Casa", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("Supermercado").assertExists()
        compose.onAllNodesWithText("+ Nova tag").onFirst().performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `expanding the row reaches every tag with no second tap`() {
        render(
            state().copy(
                expandedRow = IntentField.TAG,
                tags = listOf(Tag("t1", "Supermercado", TransactionType.EXPENSE, idContext = "ctx")),
                contexts = listOf(TagContext("ctx", "Casa", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("Buscar tag…").assertExists()
        compose.onNodeWithText("Ver todas as tags").assertDoesNotExist()
    }

    @Test
    fun `the inline form leaves the rest of the preview, and Lancar, in place`() {
        render(
            state().copy(
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(TagContext("ctx", "Casa", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("Nome da tag").assertExists()
        compose.onNodeWithText("Criar e aplicar").assertExists()
        compose.onNodeWithText("O QUE VOU LANÇAR").assertExists()
        compose.onNodeWithText("Lançar").assertIsEnabled()
    }

    @Test
    fun `an expense with no categories says where they are made and still lets Lancar through`() {
        render(state().copy(expandedRow = IntentField.TAG))

        compose.onNodeWithText("Crie categorias em Mais › Contextos & Tags para usar tags.").assertExists()
        compose.onNodeWithText("+ Nova tag").assertDoesNotExist()
        compose.onNodeWithText("Lançar").assertIsEnabled()
    }

    @Test
    fun `Criar e aplicar waits for the tag name`() {
        render(
            state().copy(
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(TagContext("ctx", "Casa", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("Criar e aplicar").assertIsNotEnabled()

        compose.onNodeWithText("Nome da tag").performScrollTo().performTextInput("Veterinário")

        compose.onNodeWithText("Criar e aplicar").assertIsEnabled()
    }

    @Test
    fun `with no category to go under, the form falls back to the first one`() {
        render(
            state().copy(
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(
                    TagContext("ctx-home", "Casa", 0xFF112233),
                    TagContext("ctx-health", "Saúde", 0xFF445566),
                ),
            ),
        )

        compose.onNodeWithText("Nome da tag").performScrollTo().performTextInput("Veterinário")
        compose.onNodeWithText("Criar e aplicar").performScrollTo().performClick()

        assertEquals("Veterinário", createRequest?.name)
        assertEquals("ctx-home", createRequest?.contextId)
    }

    @Test
    fun `an income category is named and coloured, with no category to pick`() {
        render(
            state(
                draft = WizardDraft(
                    type = TransactionType.INCOME,
                    amount = BigDecimal("125.00"),
                    date = LocalDate.now(),
                    name = "Dividendos",
                ),
            ).copy(
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(TagContext("ctx", "Casa", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("Nome da categoria de receita").assertExists()
        compose.onNodeWithContentDescription("Cor 1").assertExists()
        compose.onNodeWithText("CATEGORIA").assertDoesNotExist()
        compose.onNodeWithText("Casa").assertDoesNotExist()
        compose.onNodeWithText("+ Nova categoria").assertDoesNotExist()
    }

    @Test
    fun `a matched category names itself and brings its own tags`() {
        render(
            state(idCategory = "ctx-health").copy(
                expandedRow = IntentField.TAG,
                tags = listOf(
                    Tag("t-vet", "Veterinário", TransactionType.EXPENSE, idContext = "ctx-health"),
                    Tag("t-food", "Padaria", TransactionType.EXPENSE, idContext = "ctx-home"),
                ),
                contexts = listOf(
                    TagContext("ctx-health", "Saúde", 0xFF112233),
                    TagContext("ctx-home", "Casa", 0xFF445566),
                ),
            ),
        )

        compose.onNodeWithContentDescription("Categoria: sem categoria. parece Saúde.").assertExists()
        compose.onNodeWithText("Veterinário").assertExists()
        compose.onNodeWithText("Padaria").assertDoesNotExist()
    }

    @Test
    fun `the server's own tag pick is the selected pill`() {
        val vet = Tag("t-vet", "Veterinário", TransactionType.EXPENSE, idContext = "ctx-health")
        render(
            state(idCategory = "ctx-health").copy(
                expandedRow = IntentField.TAG,
                draft = WizardDraft(
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("150.00"),
                    date = LocalDate.now(),
                    name = "Consulta",
                    tagIds = listOf(vet.id),
                ),
                tags = listOf(vet),
                contexts = listOf(TagContext("ctx-health", "Saúde", 0xFF112233)),
            ),
        )

        compose.onAllNodesWithText("Veterinário").filterToOne(isSelectable()).assertIsSelected()
        // The row carries a tag, so the guess has nothing left to offer.
        compose.onNodeWithText("parece Saúde").assertDoesNotExist()
    }

    @Test
    fun `a category deleted since the read is not named at all`() {
        render(
            state(idCategory = "ctx-gone").copy(
                expandedRow = IntentField.TAG,
                tags = listOf(Tag("t-food", "Padaria", TransactionType.EXPENSE, idContext = "ctx-home")),
                contexts = listOf(TagContext("ctx-home", "Casa", 0xFF445566)),
            ),
        )

        compose.onNodeWithContentDescription("Categoria: sem categoria.").assertExists()
        // With nothing to suggest the row falls back to the first tags of the kind.
        compose.onNodeWithText("Padaria").assertExists()
    }

    @Test
    fun `an income draft takes no category from an earlier expense read`() {
        render(
            state(idCategory = "ctx-health").copy(
                draft = WizardDraft(
                    type = TransactionType.INCOME,
                    amount = BigDecimal("125.00"),
                    date = LocalDate.now(),
                    name = "Dividendos",
                ),
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(TagContext("ctx-health", "Saúde", 0xFF112233)),
            ),
        )

        compose.onNodeWithText("parece Saúde").assertDoesNotExist()
        compose.onNodeWithText("Saúde").assertDoesNotExist()
        compose.onNodeWithText("Nome da categoria de receita").assertExists()
    }

    @Test
    fun `the new tag form opens under the matched category`() {
        render(
            state(idCategory = "ctx-health").copy(
                expandedRow = IntentField.TAG,
                isCreatingTag = true,
                contexts = listOf(
                    TagContext("ctx-health", "Saúde", 0xFF112233),
                    TagContext("ctx-home", "Casa", 0xFF445566),
                ),
            ),
        )

        compose.onAllNodesWithText("Saúde").filterToOne(isSelectable()).assertIsSelected()
        compose.onAllNodesWithText("Casa").filterToOne(isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `the two-line label still aligns its value with every other row`() {
        render(state())

        val amount = compose.onNode(hasText(formatBrl(BigDecimal("150.00"))), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        // "Forma de Pagamento" wraps to two lines; its value still starts where the others do.
        val absent = compose.onAllNodes(hasText("não informado"), useUnmergedTree = true)
            .fetchSemanticsNodes()

        assertTrue(absent.isNotEmpty())
        absent.indices.forEach { index ->
            val bounds = compose.onAllNodes(hasText("não informado"), useUnmergedTree = true)[index]
                .getUnclippedBoundsInRoot()
            assertEquals(amount.left.value, bounds.left.value, 0.5f)
        }
    }

    @Test
    fun `a failed lookup offers a retry instead of claiming there are no categories`() {
        render(state().copy(expandedRow = IntentField.TAG, lookupsFailed = true))

        compose.onNodeWithText("Não foi possível carregar as categorias. Toque para tentar de novo.")
            .assertExists()
            .performScrollTo()
            .performClick()

        assertEquals(1, retries)
        compose.onAllNodesWithText("Crie categorias em Mais › Contextos & Tags para usar tags.")
            .assertCountEquals(0)
    }

    @Test
    fun `genuinely having no categories still points at where they are made`() {
        render(state().copy(expandedRow = IntentField.TAG))

        compose.onNodeWithText("Crie categorias em Mais › Contextos & Tags para usar tags.").assertExists()
        compose.onAllNodesWithText("Não foi possível carregar as categorias. Toque para tentar de novo.")
            .assertCountEquals(0)
    }
}
