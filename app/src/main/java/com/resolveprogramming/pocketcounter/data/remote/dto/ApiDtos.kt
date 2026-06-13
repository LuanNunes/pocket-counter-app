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
    val idSource: String? = null,
    val idPaymentSource: String? = null,
    val type: String? = null,                 // "INCOME" | "EXPENSE"
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    val statusPayment: String? = null,        // "PAID" | "PENDING"
    val refYearMonth: Int = 0,
    val name: String? = null,                 // source name (join)
    val paymentSourceName: String? = null,
    val paymentSourceType: String? = null,
    val idBillingCard: String? = null,
    val dateDue: String? = null,              // ISO yyyy-MM-dd
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
data class SourceDto(
    val id: String? = null,
    val idPaymentSource: String,
    val name: String,
    val allowsIncome: Boolean = false,
    val allowsExpense: Boolean = false,
    val refDayRecurring: Int? = null,
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal = BigDecimal.ZERO,
    val tags: List<String> = emptyList(),
)

@Serializable
data class PaymentSourceDto(
    val id: String? = null,
    val idUser: String? = null,
    val name: String = "",
    val type: String = "CREDIT_CARD",         // PaymentTypeEnum name
    val allowsIncome: Boolean = false,
    val allowsExpense: Boolean = false,
    val refDayBill: Int? = null,
)

@Serializable
data class TagDto(
    val id: String? = null,
    val idUser: String? = null,
    val idContext: String? = null,
    val idTransaction: String? = null,
    val name: String,
)

@Serializable
data class ContextDto(
    val id: String? = null,
    val idUser: String? = null,
    val name: String,
    val color: String? = null,
    val displayOrder: Int? = null,
)

@Serializable
data class ClassificationRuleTagDto(
    val idTag: String,
    val idContext: String,
)

@Serializable
data class ClassificationRuleDto(
    val id: String? = null,
    val pattern: String,
    val idPaymentSource: String? = null,
    val idSource: String? = null,
    val transactionType: String? = null,        // "INCOME" | "EXPENSE"
    val tagIds: List<ClassificationRuleTagDto> = emptyList(),
)

@Serializable
data class ReorderItemDto(val id: String, val displayOrder: Int)

@Serializable
data class TransactionReorderRequest(val items: List<ReorderItemDto>)

@Serializable
data class ContextReorderDto(val items: List<ReorderItemDto>)

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
    val idPaymentSource: String? = null,
    val idSource: String? = null,
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class ClassifyResponseDto(
    val notificationId: String,
    val status: String,                       // "AUTO" | "NEEDS_TAGS" | "NEEDS_REVIEW"
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
