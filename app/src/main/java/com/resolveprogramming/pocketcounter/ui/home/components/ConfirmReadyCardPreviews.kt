package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Not tests — the only cheap way to eyeball the one thing the unit tests can't reach: whether
 * line 2 (date · tag · payment) survives a long merchant, a long card name and fontScale 2f
 * without wrapping.
 */

private val previewToday = LocalDate.of(2026, 6, 20)

private val previewCard = CreditCard(
    id = "card-1",
    name = "Nubank",
    brand = "mastercard",
    last4 = "1234",
    gradientStart = 0xFF_82_0A_D1L,
    gradientEnd = 0xFF_69_0C_A3L,
    limit = BigDecimal("5000"),
    billDay = 10,
)

private val previewContext = TagContext("c1", "Saúde", 0xFF_2E_A0_6BL)

private val previewTag = Tag("t-farmacia", "Farmácia", TransactionType.EXPENSE, idContext = "c1")

private val previewLookups = LedgerLookups(
    cards = mapOf(previewCard.id to previewCard),
    tags = mapOf(
        previewTag.id to previewTag,
        "t-extra" to Tag("t-extra", "Recorrente", TransactionType.EXPENSE, idContext = "c1"),
    ),
    contexts = mapOf(previewContext.id to previewContext),
)

private fun previewItem(
    merchant: String = "IFD*DROGARIA DROGACENT",
    tagIds: List<String> = listOf(previewTag.id),
    pendingTransactionId: String? = null,
    installments: Int? = null,
) = ConfirmReadyItem(
    notificationId = "n1",
    draft = WizardDraft(
        type = TransactionType.EXPENSE,
        amount = BigDecimal("129.90"),
        date = LocalDate.of(2026, 6, 14),
        paymentMethod = PaymentMethod.CREDIT,
        cardId = previewCard.id,
        name = merchant,
        tagIds = tagIds,
        installments = installments,
    ),
    pendingTransactionId = pendingTransactionId,
    notification = NotificationItem(
        id = "n1",
        app = "Nubank",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-14T13:25:00Z",
        text = "Compra aprovada",
        status = NotificationStatus.AUTO,
        parsed = ParsedNotification(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("129.90"),
            date = LocalDate.of(2026, 6, 14),
            merchantRaw = merchant,
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(tagIds = tagIds),
        tokens = emptyList(),
    ),
)

@Composable
private fun PreviewCard(item: ConfirmReadyItem, isConfirming: Boolean = false) {
    PocketTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(PocketTheme.colors.bg)
                .padding(20.dp),
        ) {
            ConfirmReadyCard(
                item = item,
                presentation = confirmReadyPresentation(
                    item = item,
                    lookups = previewLookups,
                    today = previewToday,
                ),
                isConfirming = isConfirming,
                onConfirm = {},
                onReview = {},
                onIgnore = {},
            )
        }
    }
}

@Preview(name = "Confirm ready · tagged", showBackground = true)
@Composable
private fun ConfirmReadyCardTaggedPreview() = PreviewCard(previewItem())

@Preview(name = "Confirm ready · untagged", showBackground = true)
@Composable
private fun ConfirmReadyCardUntaggedPreview() = PreviewCard(previewItem(tagIds = emptyList()))

@Preview(name = "Confirm ready · pending match", showBackground = true)
@Composable
private fun ConfirmReadyCardPendingMatchPreview() =
    PreviewCard(previewItem(pendingTransactionId = "tx-99"))

@Preview(name = "Confirm ready · long merchant + parcelas", showBackground = true)
@Composable
private fun ConfirmReadyCardLongMerchantPreview() = PreviewCard(
    previewItem(
        merchant = "IFD*DROGARIA DROGACENTER SANTA CECILIA MATRIZ LTDA",
        tagIds = listOf(previewTag.id, "t-extra"),
        installments = 3,
    ),
)

@Preview(name = "Confirm ready · fontScale 2", showBackground = true, fontScale = 2f)
@Composable
private fun ConfirmReadyCardLargeFontPreview() = PreviewCard(previewItem())
