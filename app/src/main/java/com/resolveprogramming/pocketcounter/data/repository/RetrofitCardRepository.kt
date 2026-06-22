package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.api.CreditCardApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleTagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CreditCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.domain.billing.BillingCycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cartões data is DERIVED — the backend has no card/fatura endpoint. A credit card is a
 * PaymentSource of type CREDIT_CARD; its open fatura is the current month's credit
 * expenses grouped by payment source. The backend stores no limit/last4/brand/gradient,
 * so those are left empty (limit ZERO) and the screen hides the fields it can't fill.
 */
@Singleton
class RetrofitCardRepository @Inject constructor(
    private val creditCardApi: CreditCardApi,
    private val transactionApi: TransactionApi,
    private val classificationRuleApi: ClassificationRuleApi,
    private val sourceRepository: SourceRepository,
    private val tagRepository: TagRepository,
) : CardRepository {

    override suspend fun getCards(): Result<List<CreditCard>> = runCatching {
        creditCards()
    }

    override suspend fun getOpenInvoices(): Result<List<OpenInvoice>> = runCatching {
        val cards = creditCards()
        val ref = RemoteMappers.currentRefYearMonth()
        val expenses = transactionApi.getExpenses(ref)
        // Fatura items are real transactions, so they INHERIT their source's default tags when
        // the transaction has none of its own. Resolve the effective set against the loaded sources.
        val sources = sourceRepository.getAll().getOrDefault(emptyList())
        val sourceTags = sources.associate { it.id to it.tags }
        val sourceNames = sources.associate { it.id to it.name }
        val tagsById = tagRepository.getAllTags().getOrDefault(emptyList()).associateBy { it.id }

        cards.map { card ->
            val items = expenses
                .filter { it.cardId == card.id }
                .mapNotNull { tx ->
                    val txId = tx.id ?: return@mapNotNull null
                    val effectiveIds = effectiveTagIds(
                        tx.tags?.mapNotNull { it.id },
                        sourceTags[tx.idSource].orEmpty(),
                    )
                    // Override tags carry their own name; fall back to it if the id isn't in the
                    // cached global tag list (just-created / out-of-band tag) instead of dropping it.
                    val ownById = tx.tags?.associateBy { it.id }.orEmpty()
                    InvoiceItem(
                        transactionId = txId,
                        // Per-purchase label is the fonte (source) name, then the freeform
                        // description. Never fall back to paymentSourceName — that's the card
                        // itself, which would make every row read identically (e.g. the card name).
                        name = sourceNames[tx.idSource]?.takeIf { it.isNotBlank() }
                            ?: tx.name?.takeIf { it.isNotBlank() }
                            ?: tx.description?.takeIf { it.isNotBlank() }
                            ?: "Compra",
                        date = RemoteMappers.parseDate(tx.datePaid)
                            ?: RemoteMappers.parseDate(tx.dateDue)
                            ?: java.time.LocalDate.now(),
                        amount = (tx.amount ?: BigDecimal.ZERO).abs(),
                        tags = effectiveIds.mapNotNull { id -> tagsById[id] ?: ownById[id]?.toDomain() },
                        installmentLabel = null,
                    )
                }
            val total = items.fold(BigDecimal.ZERO) { acc, it -> acc + it.amount }
            val usage = if (card.limit > BigDecimal.ZERO) {
                total.divide(card.limit, 4, RoundingMode.HALF_UP).toFloat().coerceAtMost(1f)
            } else {
                0f
            }
            OpenInvoice(
                card = card,
                total = total,
                usage = usage,
                closesInDays = BillingCycle.closesInDays(card.billDay),
                dueLabel = BillingCycle.dueLabel(card.billDay),
                items = items.sortedByDescending { it.date },
            )
        }
    }

    override suspend fun classifyPurchase(
        transactionId: String,
        tags: List<Tag>,
        learnRule: Boolean,
        card: CreditCard,
    ): Result<ClassifyOutcome> = runCatching {
        val ref = RemoteMappers.currentRefYearMonth()
        val tx = transactionApi.getExpenses(ref).firstOrNull { it.id == transactionId }
            ?: error("Transaction $transactionId not found")

        val updated = tx.copy(tags = tags.map { TagDto(id = it.id, name = it.name) })
        transactionApi.update(transactionId, updated)

        if (!learnRule) {
            return@runCatching ClassifyOutcome(ruleRequested = false, ruleCreated = false)
        }

        // Only learn tags that carry a context — the backend rule needs {idTag, idContext}.
        val ruleTags = tags.filter { !it.idContext.isNullOrBlank() }
        if (ruleTags.isEmpty()) {
            return@runCatching ClassifyOutcome(ruleRequested = true, ruleCreated = false)
        }

        val ruleCreated = runCatching {
            val dto = ClassificationRuleDto(
                pattern = tx.name ?: tx.description ?: card.name,
                idPaymentSource = card.id,
                idSource = tx.idSource, // TODO(phase-3 faturas): legacy field, null on the new backend
                transactionType = "EXPENSE",
                tagIds = ruleTags.map { ClassificationRuleTagDto(it.id, it.idContext!!) },
            )
            classificationRuleApi.create(dto)
        }.isSuccess

        ClassifyOutcome(ruleRequested = true, ruleCreated = ruleCreated)
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
}
