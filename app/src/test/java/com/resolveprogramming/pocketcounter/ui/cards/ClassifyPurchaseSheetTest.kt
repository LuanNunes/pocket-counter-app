package com.resolveprogramming.pocketcounter.ui.cards

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Taps go through the OnClick semantics action: Robolectric does not deliver injected pointer
 * events to the sheet's dialog window, so `performClick` silently does nothing here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ClassifyPurchaseSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private val card = CreditCard(
        id = "card-1",
        name = "Nubank",
        brand = "Mastercard",
        last4 = "1234",
        gradientStart = 0xFF820AD1L,
        gradientEnd = 0xFF4A0A8AL,
        limit = BigDecimal.ZERO,
        billDay = 10,
    )

    private val tagMercado = Tag("t-mercado", "Mercado", TransactionType.EXPENSE, idContext = null)

    // Not present in allTags — models a tag the item already carries that fell out of the
    // (expense-only) catalog, e.g. deleted or reclassified server-side.
    private val tagLegado = Tag("t-legado", "Antiga", TransactionType.EXPENSE, idContext = null)

    private fun item(tags: List<Tag> = emptyList()) = InvoiceItem(
        transactionId = "tx-1",
        invoiceId = "inv-1",
        itemId = "item-1",
        name = "Restaurante X",
        date = LocalDate.of(2026, 6, 4),
        amount = BigDecimal("42.50"),
        tags = tags,
        installmentLabel = null,
    )

    private fun render(
        item: InvoiceItem,
        allTags: List<Tag> = listOf(tagMercado),
        contexts: List<TagContext> = emptyList(),
        onSave: (selectedTags: List<Tag>, learnRule: Boolean) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            PocketTheme {
                ClassifyPurchaseSheet(
                    card = card,
                    item = item,
                    allTags = allTags,
                    contexts = contexts,
                    formatter = formatter,
                    onDismiss = {},
                    onSave = onSave,
                )
            }
        }
    }

    private fun openOrphanCategory() = compose
        .onNodeWithContentDescription("Sem categoria", substring = true)
        .performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `CTA is disabled and the hint is visible when nothing is selected`() {
        render(item())

        compose.onNodeWithText("Escolha ao menos uma tag.").assertIsDisplayed()
        compose.onNodeWithText("Salvar e criar regra").assertIsNotEnabled()
    }

    @Test
    fun `selecting a tag enables the CTA and clears the hint`() {
        render(item())

        openOrphanCategory()
        compose.onNodeWithText("Mercado").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Salvar e criar regra").assertIsEnabled()
        compose.onNodeWithText("Escolha ao menos uma tag.").assertDoesNotExist()
    }

    @Test
    fun `the CTA label mirrors the learn-rule switch`() {
        render(item())

        compose.onNodeWithText("Salvar e criar regra").assertIsDisplayed()

        compose.onNode(isToggleable()).performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Salvar classificação").assertIsDisplayed()
    }

    @Test
    fun `saving with an item tag missing from the catalog keeps it in the payload`() {
        var saved: List<Tag>? = null
        render(
            item = item(tags = listOf(tagLegado)),
            allTags = listOf(tagMercado),
            onSave = { tags, _ -> saved = tags },
        )

        compose.onNodeWithText("Salvar classificação").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(
            "the item's pre-existing tag must survive an untouched save",
            saved?.any { it.id == "t-legado" } == true,
        )
    }

    @Test
    fun `a tag already on the item renders as a removable pill`() {
        render(item = item(tags = listOf(tagMercado)), allTags = listOf(tagMercado))

        compose.onNodeWithContentDescription("Remover Mercado").assertIsDisplayed()
    }
}
