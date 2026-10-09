package com.resolveprogramming.pocketcounter.data.remote.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `POST api/v1/transactions/raw` — the server reads one sentence and answers with what it
 * understood; it never writes. [referenceDate] is the client's own local date, ISO yyyy-MM-dd.
 *
 * Every enum rides as a [String]: a value this build does not know must degrade in the mapper
 * instead of failing the whole read, and `coerceInputValues` cannot save a non-nullable enum.
 */
@Serializable
data class TransactionRawRequestDto(
    val text: String,
    val referenceDate: String,
)

@Serializable
data class RawReadingDto(
    val type: String? = null,                 // TransactionType name (INCOME|EXPENSE)
    @Serializable(with = RemoteBigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    val date: String? = null,                 // ISO yyyy-MM-dd
    val name: String? = null,
    val paymentMethod: String? = null,        // PaymentMethodEnum name (UPPERCASE)
)

/** One provenance ("WRITTEN"|"INFERRED") per read field; a field with no value is absent here too. */
@Serializable
data class RawSourceDto(
    val type: String? = null,
    val amount: String? = null,
    val date: String? = null,
    val name: String? = null,
    val paymentMethod: String? = null,
    val card: String? = null,
)

@Serializable
data class CardCandidateDto(
    val id: String,
    val name: String,
)

@Serializable
data class RawCardDto(
    val status: String? = null,               // CardResolution name
    val resolved: CardCandidateDto? = null,
    val candidates: List<CardCandidateDto> = emptyList(),
)

@Serializable
data class TransactionRawResponseDto(
    val reading: RawReadingDto = RawReadingDto(),
    val source: RawSourceDto = RawSourceDto(),
    val card: RawCardDto = RawCardDto(),
    val tag: ClassificationSuggestionDto = ClassificationSuggestionDto(),
    val missing: List<String> = emptyList(),
)
