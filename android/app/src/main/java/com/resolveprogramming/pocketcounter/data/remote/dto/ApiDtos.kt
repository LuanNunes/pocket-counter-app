package com.resolveprogramming.pocketcounter.data.remote.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Remote DTOs mirroring the pocket-counter backend JSON (Jackson). Enums, dates and
 * UUIDs are kept as [String] and converted in the repository mappers; numbers use
 * [RemoteBigDecimalSerializer]. Unknown keys are ignored by the shared [kotlinx.serialization.json.Json].
 */

@Serializable
data class TransactionDto(
    val id: String? = null,
    val transactionType: String? = null,      // "INCOME" | "EXPENSE" (controller sets it on create)
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    val statusPayment: String? = null,        // "PAID" | "PENDING"
    val refYearMonth: Int = 0,
    val name: String? = null,
    val paymentMethod: String? = null,        // PaymentMethodEnum name (UPPERCASE)
    val cardId: String? = null,               // UUID of the credit card
    val isInvoice: Boolean = false,
    val idSeries: String? = null,             // UUID of the recurring series
    /**
     * Create-only: ties the resulting invoice line item to the notification's bank text, which is what
     * a later statement import matches on. Create returns the invoice id, not the item id, so this can
     * never be set afterwards. An id the backend cannot resolve is ignored, not an error.
     */
    val idNotification: String? = null,       // UUID of the source notification
    val dateDue: String? = null,              // ISO yyyy-MM-dd
    // The date the purchase was made, ISO yyyy-MM-dd. The backend picks which card invoice a credit
    // purchase nests into from this; dateDue is only accepted as a deprecated fallback for it.
    val datePurchase: String? = null,
    val datePaid: String? = null,
    val tags: List<TagDto>? = null,
    // Passthrough fields the backend's update copies verbatim — preserved on tag-only edits.
    val displayOrder: Int = 0,
    val description: String? = null,
    val currency: String? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amountOriginal: BigDecimal? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val exchangeRate: BigDecimal? = null,
)

@Serializable
data class CreditCardDto(
    val id: String? = null,
    val idUser: String? = null,
    val name: String,
    val brand: String? = null,
    val closingDay: Int? = null,
    val color: String? = null,
)

@Serializable
data class TagDto(
    val id: String? = null,
    val idUser: String? = null,
    val idCategory: String? = null,
    val idTransaction: String? = null,
    val name: String,
    val kind: String? = null,                   // TransactionType name (INCOME|EXPENSE)
    val color: String? = null,
    val idSeries: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String? = null,
    val idUser: String? = null,
    val name: String,
    val color: String? = null,
    val displayOrder: Int? = null,
)

@Serializable
data class ClassificationRuleDto(
    val id: String? = null,
    val pattern: String = "",
    val idTag: String? = null,                  // SUGGEST iff non-null
    val idCategory: String? = null,             // read-only, projected from the tag; never sent
    val active: Boolean? = null,
    val appliedCount: Int = 0,                  // read-only
    // "SUGGEST" (default) assigns the tag; "IGNORE" auto-ignores matching notifications.
    // null is omitted on write (encodeDefaults=false) so SUGGEST rules stay wire-compatible.
    val action: String? = null,
)

@Serializable
data class TransactionItemDto(
    val id: String? = null,
    val idUser: String? = null,
    val idTransaction: String,
    val name: String,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal,
    // ISO yyyy-MM-dd. On a partial PUT, null means "keep the stored value" — the backend preserves
    // it. Never send null expecting to clear the field.
    val datePurchase: String? = null,
    val tagIds: List<String>? = null,
    val tags: List<TagDto>? = null,
)

@Serializable
data class ReorderItemDto(val id: String, val displayOrder: Int)

@Serializable
data class TransactionReorderRequest(val items: List<ReorderItemDto>)

@Serializable
data class CategoryReorderDto(val items: List<ReorderItemDto>)

@Serializable
data class ClassifyRequestDto(
    val notificationId: String,
)

@Serializable
data class ParsedNotificationDto(
    val type: String? = null,                 // "INCOME" | "EXPENSE"
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    val date: String? = null,                 // ISO yyyy-MM-dd
    val merchantRaw: String? = null,
    val paymentHint: String? = null,
    val installments: Int? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val installmentValue: BigDecimal? = null,
)

@Serializable
data class ClassificationSuggestionDto(
    val idTag: String? = null,
    val idCategory: String? = null,           // read-only projection of the tag
)

@Serializable
data class ClassifyResponseDto(
    val notificationId: String,
    val status: String,                       // Backend emits "AUTO" | "NEEDS_REVIEW"; the "NEEDS_TAGS" branch is legacy-defensive
    val parsed: ParsedNotificationDto = ParsedNotificationDto(),
    val suggestions: ClassificationSuggestionDto = ClassificationSuggestionDto(),
    val pendingTransactionId: String? = null,
)

@Serializable
data class ClassifiedRequestDto(
    val idTransaction: String,
)

@Serializable
data class NotificationRequestDto(
    val app: String,
    val channel: String,                      // "SMS" | "PUSH"
    val text: String,
    val receivedAt: String,                   // ISO instant
    val parsedType: String? = null,           // "INCOME" | "EXPENSE"
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val parsedAmount: BigDecimal? = null,
    val parsedDate: String? = null,           // ISO yyyy-MM-dd
    val parsedMerchant: String? = null,
    val parsedPaymentHint: String? = null,
    val parsedInstallments: Int? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val parsedInstallmentValue: BigDecimal? = null,
)

@Serializable
data class RecurringSeriesDto(
    val id: String,
    val idUser: String? = null,
    val name: String,
    val transactionType: String,                // "INCOME" | "EXPENSE"
    val recurrenceDay: Int? = null,
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class CreateRecurringSeriesRequest(
    val name: String,
    val transactionType: String,                // TransactionType.name ("INCOME" | "EXPENSE")
    val recurrenceDay: Int? = null,
)

@Serializable
data class RenameRecurringSeriesRequest(val name: String)

@Serializable
data class CategorizeRecurringSeriesRequest(val tagIds: List<String>)

@Serializable
data class CarryForwardRequest(
    val sourceRefYearMonth: Int,
    val onlyRecurring: Boolean,
)

@Serializable
data class CarryForwardResultDto(
    val target: Int = 0,
    val sourceRefYearMonth: Int = 0,
    val createdCount: Int,
    val skippedCount: Int,
)

@Serializable
data class NotificationDto(
    val id: String,
    val app: String,
    val channel: String,                      // "SMS" | "PUSH"
    val text: String,
    val status: String,                       // NotificationStatusEnum name
    val parsedType: String? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val parsedAmount: BigDecimal? = null,
    val parsedDate: String? = null,
    val parsedMerchant: String? = null,
    val parsedPaymentHint: String? = null,
    val parsedInstallments: Int? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val parsedInstallmentValue: BigDecimal? = null,
    val idTransaction: String? = null,
    val idPendingMatch: String? = null,
    val receivedAt: String? = null,           // ISO instant
)
