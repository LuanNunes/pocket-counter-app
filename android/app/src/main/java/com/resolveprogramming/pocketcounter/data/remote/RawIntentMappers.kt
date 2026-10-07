package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.dto.CardCandidateDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RawSourceDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionRawResponseDto
import com.resolveprogramming.pocketcounter.domain.model.CardCandidate
import com.resolveprogramming.pocketcounter.domain.model.CardResolution
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.IntentReading
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import java.time.LocalDate

/** `/transactions/raw` DTO → domain. Nothing here throws on a value it does not recognise. */
internal object RawIntentMappers {

    fun TransactionRawResponseDto.toIntent(referenceDate: LocalDate): TransactionIntent =
        TransactionIntent(
            reading = IntentReading(
                type = RemoteMappers.parseType(reading.type),
                amount = reading.amount,
                date = RemoteMappers.parseDate(reading.date) ?: referenceDate,
                name = reading.name,
                paymentMethod = RemoteMappers.parsePaymentMethod(reading.paymentMethod),
            ),
            source = source.toProvenances(),
            cardStatus = parseCardResolution(card.status),
            resolvedCard = card.resolved?.toCandidate(),
            cardCandidates = card.candidates.map { it.toCandidate() },
            idTag = tag.idTag,
            idCategory = tag.idCategory,
            missing = missing.mapNotNull(::parseMissingField),
        )

    /** Every field the server gave a provenance for; an unreadable one is dropped, never guessed. */
    private fun RawSourceDto.toProvenances(): Map<IntentField, ValueSource> = listOfNotNull(
        provenance(IntentField.TYPE, type),
        provenance(IntentField.AMOUNT, amount),
        provenance(IntentField.DATE, date),
        provenance(IntentField.NAME, name),
        provenance(IntentField.PAYMENT_METHOD, paymentMethod),
        provenance(IntentField.CARD, card),
    ).toMap()

    private fun provenance(field: IntentField, raw: String?): Pair<IntentField, ValueSource>? =
        parseValueSource(raw)?.let { field to it }

    private fun parseValueSource(raw: String?): ValueSource? =
        raw?.uppercase()?.let { name -> ValueSource.entries.firstOrNull { it.name == name } }

    /** Server order is the ask order, so the list is never sorted; an unreadable entry is dropped. */
    private fun parseMissingField(raw: String): MissingField? =
        MissingField.entries.firstOrNull { it.name == raw.uppercase() }

    private fun CardCandidateDto.toCandidate(): CardCandidate = CardCandidate(id = id, name = name)

    /** A status this build cannot read leaves nothing to decide, which is what NOT_APPLICABLE means. */
    private fun parseCardResolution(raw: String?): CardResolution =
        raw?.uppercase()?.let { name -> CardResolution.entries.firstOrNull { it.name == name } }
            ?: CardResolution.NOT_APPLICABLE
}
