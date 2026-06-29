package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleTagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CategoryDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardResultDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RecurringSeriesDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.CarryForwardResult
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.notification.NotificationTokenizer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared DTO → domain mappers + helpers for the Retrofit repositories. */
internal object RemoteMappers {

    /** refYearMonth integer the backend partitions by, e.g. 2026-06 → 202606. */
    fun refYearMonth(date: LocalDate): Int = date.year * 100 + date.monthValue

    fun currentRefYearMonth(): Int = refYearMonth(LocalDate.now())

    /** "2026-06" → 202606. Throws a clear error on a malformed key (callers wrap in runCatching). */
    fun monthKeyToRef(monthKey: String): Int {
        val parts = monthKey.split("-")
        require(parts.size >= 2) { "Invalid monthKey: '$monthKey' (expected yyyy-MM)" }
        val y = parts[0].toIntOrNull() ?: error("Invalid year in monthKey: '$monthKey'")
        val m = parts[1].toIntOrNull() ?: error("Invalid month in monthKey: '$monthKey'")
        return y * 100 + m
    }

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun parseType(value: String?): TransactionType? = run {
        when (value?.uppercase()) {
            "INCOME" -> return@run TransactionType.INCOME
            "EXPENSE" -> return@run TransactionType.EXPENSE
        }
        null
    }

    /** PaymentMethodEnum name (UPPERCASE on the wire) → domain enum; null when absent/unknown. */
    fun parsePaymentMethod(value: String?): PaymentMethod? =
        value?.uppercase()?.let { name ->
            PaymentMethod.entries.firstOrNull { it.name == name }
        }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // A small palette reused for display-only colors the backend doesn't store
    // (payment-source brand color, context color when it can't be parsed).
    private val palette = listOf(
        0xFF7B2FBE, 0xFF3366BB, 0xFF33AA66, 0xFFCC5533,
        0xFFCC3388, 0xFF6644AA, 0xFFCC8800, 0xFF33AA77,
    )

    private fun paletteFor(seed: String): Long =
        palette[(seed.hashCode().let { h -> run { if (h < 0) return@run -h; h } }) % palette.size]

    /** Two ARGB stops for a credit-card tile gradient (the backend stores no gradient). */
    fun cardGradient(seed: String): Pair<Long, Long> {
        val base = paletteFor(seed)
        return base to darken(base, 0.78f)
    }

    private fun darken(argb: Long, factor: Float): Long {
        val a = argb and 0xFF000000L
        val r = ((argb shr 16 and 0xFF) * factor).toLong().coerceIn(0, 255)
        val g = ((argb shr 8 and 0xFF) * factor).toLong().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toLong().coerceIn(0, 255)
        return a or (r shl 16) or (g shl 8) or b
    }

    /** Parses a backend color string ("#RRGGBB"/"#AARRGGBB"); falls back to the palette. */
    fun parseColor(value: String?, seed: String): Long {
        val hex = value?.trim()?.removePrefix("#")
        if (hex != null && (hex.length == 6 || hex.length == 8) && hex.all { it.isHexDigit() }) {
            val rgb = hex.toLong(16)
            if (hex.length == 6) return 0xFF000000L or rgb
            return rgb
        }
        return paletteFor(seed)
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** ARGB Long → "#RRGGBB" (drops alpha; the backend palette is opaque 6-digit hex). */
    fun colorToHex(argb: Long): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun RecurringSeriesDto.toDomain(): Series = Series(
        id = id,
        name = name,
        type = parseType(transactionType) ?: TransactionType.EXPENSE,
        recurrenceDay = recurrenceDay,
        tagIds = tagIds,
    )

    fun CarryForwardResultDto.toDomain(): CarryForwardResult = CarryForwardResult(
        createdCount = createdCount,
        skippedCount = skippedCount,
    )

    /** Serializes a learned rule for create. Only expense tags carry a context (idCategory). */
    fun ClassificationRule.toDto(): ClassificationRuleDto = ClassificationRuleDto(
        patterns = patterns,
        matchType = matchType,
        active = active,
        transactionType = transactionType?.let { "INCOME".takeIf { _ -> it == TransactionType.INCOME } ?: "EXPENSE" },
        paymentMethod = paymentMethod?.name,
        cardId = cardId,
        tagIds = tags.mapNotNull { tag ->
            val context = tag.idContext ?: return@mapNotNull null
            ClassificationRuleTagDto(idTag = tag.id, idCategory = context)
        },
    )

    fun ClassificationRuleDto.toDomain(): ClassificationRule = ClassificationRule(
        id = id,
        patterns = patterns,
        matchType = matchType,
        active = active,
        appliedCount = appliedCount,
        transactionType = parseType(transactionType),
        paymentMethod = parsePaymentMethod(paymentMethod),
        cardId = cardId,
        // Names are resolved against the loaded tag list in the UI layer.
        // Rule tags are always expense (the rule DTO keeps its own idCategory field).
        tags = tagIds.map { Tag(id = it.idTag, name = "", kind = TransactionType.EXPENSE, idContext = it.idCategory) },
    )

    fun CategoryDto.toDomain(): TagContext = TagContext(
        id = id ?: name,
        name = name,
        color = parseColor(color, id ?: name),
    )

    fun TagDto.toDomain(idContextFallback: String? = null): Tag = Tag(
        id = id ?: name,
        name = name,
        kind = parseType(kind) ?: TransactionType.EXPENSE,
        idContext = idCategory ?: idContextFallback,
        color = color?.let { parseColor(it, id ?: name) },
        idSeries = idSeries,
    )

    /** A persisted transaction → the Home history row (amount carries an expense sign). */
    fun TransactionDto.toHistoryItem(): HistoryItem {
        val type = parseType(transactionType) ?: TransactionType.EXPENSE
        val raw = amount ?: BigDecimal.ZERO
        val signed = raw.negate().takeIf { type == TransactionType.EXPENSE } ?: raw
        return HistoryItem(
            id = id.orEmpty(),
            // Ledger order is the backend's (rows arrive ORDER BY displayOrder); `date` only feeds the
            // day-group headers. Use the stable dueDate, not datePaid — marking a row paid sets datePaid
            // (~today), which would shift the row to another day group.
            date = parseDate(dateDue) ?: parseDate(datePaid) ?: LocalDate.now(),
            amount = signed,
            type = type,
            // Preserve null = inherit vs non-null = override (drop .orEmpty()).
            tagIds = tags?.mapNotNull { it.id },
            statusPayment = PaymentStatus.PENDING.takeIf { statusPayment?.uppercase() == "PENDING" }
                ?: PaymentStatus.PAID,
            displayOrder = displayOrder,
            paymentMethod = parsePaymentMethod(paymentMethod),
            cardId = cardId,
            seriesId = idSeries,
            name = name,
            description = description,
        )
    }

    /**
     * Captured message + on-device parse → POST /notifications body. The backend stores the
     * parsed* fields verbatim. Note: [NotificationChannel.PUSH] serializes as "Push" via its
     * @SerialName, so we send the enum *name* ("SMS"/"PUSH") as a plain string instead.
     */
    fun CapturedMessage.toRequestDto(parsed: ParsedNotification): NotificationRequestDto =
        NotificationRequestDto(
            app = app,
            channel = channel.name,
            text = text,
            receivedAt = receivedAt.toString(),
            parsedType = parsed.type?.name,
            parsedAmount = parsed.amount,
            parsedDate = parsed.date?.toString(),
            parsedMerchant = parsed.merchantRaw,
            parsedPaymentHint = parsed.paymentHint,
            parsedInstallments = parsed.installments,
            parsedInstallmentValue = parsed.installmentValue,
        )

    /**
     * GET /notifications/pending returns the parsed fields but no per-notification
     * suggestions or source-text tokens (those come from POST /classify). We map what
     * exists and degrade the rest to empty so the review queue and wizard still work.
     */
    fun NotificationDto.toDomain(): NotificationItem {
        val received = receivedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val localTime = received?.atZone(ZoneId.systemDefault())
        return NotificationItem(
            id = id,
            app = app,
            channel = NotificationChannel.PUSH.takeIf { channel.equals("PUSH", ignoreCase = true) }
                ?: NotificationChannel.SMS,
            time = localTime?.toLocalTime()?.format(timeFormatter).orEmpty(),
            received = receivedAt.orEmpty(),
            text = text,
            status = NotificationStatus.AUTO.takeIf { status.uppercase() == "CLASSIFIED" }
                ?: NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = parseType(parsedType),
                amount = parsedAmount,
                date = parseDate(parsedDate),
                merchantRaw = parsedMerchant,
                paymentHint = parsedPaymentHint,
                installments = parsedInstallments,
                installmentValue = parsedInstallmentValue,
            ),
            suggestions = ClassificationSuggestion(
                tagIds = emptyList(),
                paymentMethod = null,
                cardId = null,
            ),
            tokens = emptyList(),
        )
    }

    /**
     * POST /notifications/classify enriches an already-loaded [NotificationItem] (we pass the
     * base item in so we don't re-fetch the text) with the classifier's parsed fields,
     * source/tag suggestions and pre-assigned source-text tokens.
     */
    fun ClassifyResponseDto.toClassified(base: NotificationItem): ClassifiedNotification {
        val parsedDomain = ParsedNotification(
            type = parseType(parsed.type),
            amount = parsed.amount,
            date = parseDate(parsed.date),
            merchantRaw = parsed.merchantRaw,
            paymentHint = parsed.paymentHint,
            installments = parsed.installments,
            installmentValue = parsed.installmentValue,
        )
        val enriched = base.copy(
            status = parseStatus(status),
            parsed = parsedDomain,
            suggestions = ClassificationSuggestion(
                tagIds = suggestions.tagIds,
                paymentMethod = parsePaymentMethod(suggestions.paymentMethod),
                cardId = suggestions.cardId,
            ),
            tokens = NotificationTokenizer.tokenize(base.text, parsedDomain),
        )
        return ClassifiedNotification(
            notification = enriched,
            pendingTransactionId = pendingTransactionId,
        )
    }

    private fun parseStatus(value: String): NotificationStatus = run {
        when (value.uppercase()) {
            "AUTO", "CLASSIFIED" -> return@run NotificationStatus.AUTO
            "NEEDS_TAGS" -> return@run NotificationStatus.NEEDS_TAGS
        }
        NotificationStatus.NEEDS_REVIEW
    }
}
