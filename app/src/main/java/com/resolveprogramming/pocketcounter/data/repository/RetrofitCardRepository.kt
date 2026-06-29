package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.cache.SuspendCache
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.api.CreditCardApi
import com.resolveprogramming.pocketcounter.data.remote.api.InvoiceItemApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleTagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CreditCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionItemDto
import com.resolveprogramming.pocketcounter.domain.billing.BillingCycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cartões data is DERIVED. A credit card's open fatura is its current-month invoice
 * TransactionDto (isInvoice=true): the invoice total is the server-maintained amount and its
 * line items come from the items sub-resource. When no invoice tx exists, or it carries no
 * items, we fall back to grouping the card's plain (non-isInvoice) credit expenses.
 */
@Singleton
class RetrofitCardRepository @Inject constructor(
    private val creditCardApi: CreditCardApi,
    private val transactionApi: TransactionApi,
    private val classificationRuleApi: ClassificationRuleApi,
    private val invoiceItemApi: InvoiceItemApi,
) : CardRepository {

    private val cardsCache = SuspendCache<List<CreditCard>>()

    override suspend fun getCards(): Result<List<CreditCard>> = runCatching {
        cardsCache.get { creditCards() }
    }

    override suspend fun getOpenInvoices(refYearMonth: Int): Result<List<OpenInvoice>> = runCatching {
        val cards = creditCards()
        val ref = refYearMonth
        val expenses = transactionApi.getExpenses(ref)

        // The fatura on screen is the data month's (ref) statement. Anchor its closing/due label
        // to that month so the card reads the SAME month as the values shown. Using LocalDate.now()
        // rolled the closing into the next month once today passed billDay (June data → "jul"
        // vencimento). Anchoring to the first of the ref month keeps closesInDays and dueLabel
        // consistent with each other and with the month being displayed.
        val statementAnchor = LocalDate.of(ref / 100, ref % 100, 1)

        cards.map { card ->
            val cardExpenses = expenses.filter { it.cardId == card.id }
            val invoiceTx = cardExpenses.firstOrNull { it.isInvoice && it.id != null }

            val (items, total) = run {
                if (invoiceTx != null) {
                    val invoiceId = invoiceTx.id!!
                    // Items have no own date field; the purchase date is embedded in the name.
                    // Fall back to the invoice's date (not today) when the name carries none.
                    val invoiceDate = RemoteMappers.parseDate(invoiceTx.datePaid)
                        ?: RemoteMappers.parseDate(invoiceTx.dateDue)
                        ?: LocalDate.now()
                    val builtItems = invoiceItemApi.getItems(invoiceId)
                        .map { it.toInvoiceItem(invoiceId, invoiceDate) }
                    // The invoice header is the source of truth for the total — honor its amount even
                    // when it has no line items yet (a manual/projected fatura, e.g. a future month).
                    return@run builtItems to (invoiceTx.amount ?: BigDecimal.ZERO).abs()
                }
                val fallback = fallbackItems(cardExpenses)
                fallback to fallback.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
            }

            val usage = run {
                if (card.limit > BigDecimal.ZERO) {
                    return@run total.divide(card.limit, 4, RoundingMode.HALF_UP).toFloat().coerceAtMost(1f)
                }
                0f
            }
            OpenInvoice(
                card = card,
                total = total,
                usage = usage,
                closesInDays = BillingCycle.closesInDays(card.billDay, statementAnchor),
                dueLabel = BillingCycle.dueLabel(card.billDay, statementAnchor),
                items = items.sortedByDescending { it.date },
            )
        }
    }

    override suspend fun classifyPurchase(
        invoiceId: String,
        itemId: String,
        tags: List<Tag>,
        learnRule: Boolean,
        card: CreditCard,
    ): Result<ClassifyOutcome> = runCatching {
        // Fetch the item so the tags-only edit round-trips its name/amount unchanged. If the
        // fetch fails or the item is gone, fail the call — never PUT empty name/zero amount,
        // which would clobber the server's item (the parent invoice total is autoTotal'd from it).
        val existing = invoiceItemApi.getItems(invoiceId).firstOrNull { it.id == itemId }
            ?: error("Invoice item $itemId not found on invoice $invoiceId")

        val body = TransactionItemDto(
            id = itemId,
            idTransaction = invoiceId,
            name = existing.name,
            amount = existing.amount,
            tagIds = tags.map { it.id },
        )
        invoiceItemApi.updateItem(invoiceId, itemId, body)

        if (!learnRule) {
            return@runCatching ClassifyOutcome(ruleRequested = false, ruleCreated = false)
        }

        // Only learn tags that carry a category — the backend rule needs {idTag, idCategory}.
        val ruleTags = tags.filter { !it.idContext.isNullOrBlank() }
        if (ruleTags.isEmpty()) {
            return@runCatching ClassifyOutcome(ruleRequested = true, ruleCreated = false)
        }

        val ruleCreated = runCatching {
            // Key on the merchant, not the date-stamped item name — a pattern like
            // "<merchant> · 2026-05-28" would never CONTAINS-match a future purchase.
            val (cleanName, _) = splitTrailingDate(existing.name)
            val pattern = cleanName.takeIf { it.isNotBlank() } ?: existing.name
            val dto = ClassificationRuleDto(
                patterns = listOf(pattern),
                matchType = "CONTAINS",
                transactionType = "EXPENSE",
                paymentMethod = "CREDIT",
                cardId = card.id,
                tagIds = ruleTags.map { ClassificationRuleTagDto(idTag = it.id, idCategory = it.idContext!!) },
            )
            classificationRuleApi.create(dto)
        }.isSuccess

        ClassifyOutcome(ruleRequested = true, ruleCreated = ruleCreated)
    }

    override suspend fun addCard(
        name: String,
        brand: String?,
        closingDay: Int?,
        color: String?,
    ): Result<CreditCard> = runCatching {
        val id = creditCardApi.create(
            CreditCardDto(name = name, brand = brand, closingDay = closingDay, color = color),
        )
        val created = creditCardApi.getCards().firstOrNull { it.id == id }
            ?: CreditCardDto(id = id, name = name, brand = brand, closingDay = closingDay, color = color)
        created.toCreditCard()
    }.onSuccess { cardsCache.invalidate() }

    private fun TransactionItemDto.toInvoiceItem(invoiceId: String, fallbackDate: LocalDate): InvoiceItem {
        val (cleanName, parsedDate) = splitTrailingDate(name)
        return InvoiceItem(
            transactionId = invoiceId,
            invoiceId = invoiceId,
            itemId = id,
            name = cleanName.takeIf { it.isNotBlank() } ?: "Compra",
            date = parsedDate ?: fallbackDate,
            amount = amount.abs(),
            tags = tags.orEmpty().map { it.toDomain() },
            installmentLabel = null,
        )
    }

    /**
     * Invoice item names arrive as "<merchant> · YYYY-MM-DD" — the only place the per-item purchase
     * date is carried (the DTO has no date field). Splits the trailing ISO date off so the date
     * shows on the date line, not appended to the merchant name. Returns the name unchanged + null
     * when no trailing date is present.
     */
    private fun splitTrailingDate(raw: String): Pair<String, LocalDate?> {
        val match = TRAILING_ISO_DATE.find(raw) ?: return raw to null
        val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull() ?: return raw to null
        val name = raw.removeRange(match.range).trim().trimEnd('·', '-', ' ').trim()
        return name to date
    }

    private fun fallbackItems(cardExpenses: List<TransactionDto>): List<InvoiceItem> =
        cardExpenses
            .filter { !it.isInvoice }
            .mapNotNull { tx ->
                val txId = tx.id ?: return@mapNotNull null
                InvoiceItem(
                    transactionId = txId,
                    invoiceId = txId,
                    itemId = null,
                    name = tx.name?.takeIf { it.isNotBlank() }
                        ?: tx.description?.takeIf { it.isNotBlank() }
                        ?: "Compra",
                    date = RemoteMappers.parseDate(tx.datePaid)
                        ?: RemoteMappers.parseDate(tx.dateDue)
                        ?: LocalDate.now(),
                    amount = (tx.amount ?: BigDecimal.ZERO).abs(),
                    tags = tx.tags.orEmpty().map { it.toDomain() },
                    installmentLabel = null,
                )
            }

    private suspend fun creditCards(): List<CreditCard> =
        creditCardApi.getCards().map { it.toCreditCard() }

    private fun CreditCardDto.toCreditCard(): CreditCard {
        val key = id ?: name
        val (start, end) = RemoteMappers.cardGradient(key)
        return CreditCard(
            id = key,
            name = name,
            brand = brand ?: "",
            last4 = "",
            gradientStart = start,
            gradientEnd = end,
            limit = BigDecimal.ZERO,
            billDay = closingDay ?: 1,
        )
    }

    private companion object {
        // Trailing " · 2026-05-28" (separator optional) at the end of an invoice item name.
        val TRAILING_ISO_DATE = Regex("""[\s·-]*(\d{4}-\d{2}-\d{2})\s*$""")
    }
}
