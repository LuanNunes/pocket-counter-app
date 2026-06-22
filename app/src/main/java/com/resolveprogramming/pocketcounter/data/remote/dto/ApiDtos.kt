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
    // Legacy read-only fields kept until analytics/faturas migrate off them (phase-3).
    // The new backend no longer returns them, so they degrade to null on read.
    val idSource: String? = null,
    val idPaymentSource: String? = null,
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
    val transactionType: String? = null,      // "INCOME" | "EXPENSE"
    val paymentMethod: String? = null,        // PaymentMethodEnum name (UPPERCASE)
    val cardId: String? = null,               // UUID
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
