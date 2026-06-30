package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Conservative, on-device parser for Brazilian bank SMS/push text. Pure Kotlin so it stays
 * unit-testable. It only emits fields it is confident about; everything else stays null and
 * the backend stores the parsed* fields verbatim (no server-side parsing).
 *
 * [ParseResult.isFinancial] is the relevance gate: a message without a detectable BRL amount
 * is not financial and callers must not POST it.
 */
object BrNotificationParser {

    data class ParseResult(
        val isFinancial: Boolean,
        val parsed: ParsedNotification,
    )

    fun parse(text: String, now: LocalDate = LocalDate.now()): ParseResult {
        val amount = parseAmount(text)
        val type = parseType(text)
        val installments = parseInstallments(text)
        val installmentValue = installmentValue(text, amount, installments)
        val merchant = parseMerchant(text)
        val date = parseDate(text, now) ?: now
        val paymentHint = parsePaymentHint(text)

        val parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = date,
            merchantRaw = merchant,
            paymentHint = paymentHint,
            installments = installments,
            installmentValue = installmentValue,
        )
        return ParseResult(isFinancial = amount != null, parsed = parsed)
    }

    private fun parseAmount(text: String): BigDecimal? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        return NotificationTokenizer.parseBrAmount(match.value)
    }

    private fun parseType(text: String): TransactionType? {
        val lower = text.lowercase()
        val expense = EXPENSE_PHRASES.any { lower.contains(it) } ||
            EXPENSE_WORDS.any { lower.containsWord(it) }
        val income = INCOME_PHRASES.any { lower.contains(it) } ||
            INCOME_WORDS.any { lower.containsWord(it) }
        if (income && !expense) return TransactionType.INCOME
        if (expense && !income) return TransactionType.EXPENSE
        return null
    }

    private fun parseInstallments(text: String): Int? {
        // Strip date spans first so "12/06/2026" is never read as a "12/06" installment fraction.
        val lower = DATE_REGEX.replace(text.lowercase(), " ")
        TIMES_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        FRACTION_REGEX.find(lower)?.let { match ->
            return match.groupValues[2].toIntOrNull()
        }
        VEZES_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun installmentValue(
        text: String,
        amount: BigDecimal?,
        installments: Int?,
    ): BigDecimal? {
        if (installments == null || installments <= 1 || amount == null) return null
        if (hasExplicitParcelValue(text)) return null
        return amount.divide(BigDecimal(installments), 2, RoundingMode.HALF_UP)
    }

    private fun hasExplicitParcelValue(text: String): Boolean =
        PARCEL_VALUE_REGEX.containsMatchIn(text.lowercase())

    /**
     * Merchant extraction, tried most-specific first:
     *  1. Card format "... final NNNN - <merchant> valor ..." (the dominant credit-card push). The
     *     merchant is the delimited descriptor (e.g. "DL*UberRides", "IFD*IFOOD CLUB") and may carry
     *     lowercase, so it can't be captured by the uppercase-run heuristic below.
     *  2. Card format "... aprovada em <merchant> para o cartão ...".
     *  3. Fallback heuristic: an UPPERCASE-led run after "compra em"/"em".
     *
     * The numeric-mask guard in [cleanMerchant] stops the fallback from capturing a date's day digits
     * (e.g. "em 29/06" → "29"), which would normalize to nothing and make a learned rule un-matchable.
     */
    private fun parseMerchant(text: String): String? =
        (CARD_FINAL_MERCHANT_REGEX.find(text)?.groupValues?.get(1)
            ?: APROVADA_EM_MERCHANT_REGEX.find(text)?.groupValues?.get(1)
            ?: MERCHANT_REGEX.find(text)?.groupValues?.get(1))
            ?.let(::cleanMerchant)

    private fun cleanMerchant(raw: String): String? {
        // Drop the card-acquirer prefix ("DL*UberRides" → "UberRides", "IFD*IFOOD CLUB" → "IFOOD CLUB")
        // so the merchant is the name the user actually recognizes, both on screen and in learned rules.
        val stripped = ACQUIRER_PREFIX_REGEX.replaceFirst(raw.trim(), "")
        val candidate = stripped.trim().trimEnd('.', ',', ';', ':', '-').trim()
        if (candidate.length !in 2..40) return null
        // A capture with no letters is a date/amount fragment ("29", "27/06"), never a merchant.
        if (candidate.none { it.isLetter() }) return null
        return candidate
    }

    private fun parseDate(text: String, now: LocalDate): LocalDate? {
        val match = DATE_REGEX.find(text) ?: return null
        val value = match.value
        val formatter = DATE_FORMAT_FULL.takeIf { match.groupValues[3].isNotEmpty() } ?: shortDateFormat(now)
        return runCatching { LocalDate.parse(value, formatter) }.getOrNull()
    }

    private fun shortDateFormat(now: LocalDate): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("dd/MM")
        .parseDefaulting(ChronoField.YEAR, now.year.toLong())
        .toFormatter()

    private fun parsePaymentHint(text: String): String? {
        val lower = text.lowercase()
        FINAL_REGEX.find(lower)?.let { return it.value.trim() }
        if (lower.containsWord("cartão") || lower.containsWord("cartao")) return "cartão"
        if (lower.containsWord("conta")) return "conta"
        return null
    }

    private fun String.containsWord(word: String): Boolean =
        Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])").containsMatchIn(this)

    // R$ is unambiguous, so it matches with or without decimals and even glued to a preceding word
    // (e.g. "CompraR$50,00"). Bare "RS"/"BRL" are ambiguous (RS is also a state code), so they
    // require a letter-boundary AND two decimal places to avoid matching things like a CEP
    // ("Porto Alegre RS 90000-000").
    private val AMOUNT_REGEX = Regex(
        """R\$\s?\d{1,3}(\.\d{3})*(,\d{2})?|(?<![\p{L}])(RS|BRL)\s?\d{1,3}(\.\d{3})*,\d{2}""",
    )

    private val EXPENSE_PHRASES = listOf("pix enviado", "transferência enviada", "transferencia enviada", "você pagou", "voce pagou")
    private val EXPENSE_WORDS = listOf("compra", "débito", "debito", "pagamento", "saque", "fatura")

    private val INCOME_PHRASES = listOf(
        "pix recebido", "transferência recebida", "transferencia recebida",
        "crédito em conta", "credito em conta", "você recebeu", "voce recebeu",
    )
    private val INCOME_WORDS = listOf("recebido", "recebida", "salário", "salario", "depósito", "deposito")

    private val TIMES_REGEX = Regex("""\b(\d+)\s?x\b""")
    private val FRACTION_REGEX = Regex("""\b(\d+)/(\d+)\b""")
    private val VEZES_REGEX = Regex("""em\s+(\d+)\s+vezes""")
    private val PARCEL_VALUE_REGEX = Regex("""(parcela|parcelas|cada|de)\s+(de\s+)?r\$""")

    // A short acquirer/aggregator code glued to the merchant by a star ("DL*", "IFD*"). Stripped from
    // the front of a captured merchant so only the recognizable name remains.
    private val ACQUIRER_PREFIX_REGEX = Regex("""^[\p{L}\p{N}]{1,6}\*""")

    // Credit-card push: "... final 3685 - DL*UberRides valor RS 26,74 ...". The merchant is the run
    // between the "final NNNN -" delimiter and " valor"; non-greedy so it stops at the first " valor".
    private val CARD_FINAL_MERCHANT_REGEX = Regex(
        """final\s+\d+\s*[-–]\s*(.+?)\s+valor\b""",
        RegexOption.IGNORE_CASE,
    )

    // Credit-card push: "... APROVADA em DL*GOOGLE YouTub para o cartão ...". Merchant sits between
    // "aprovad(a/o) em" and " para"; non-greedy so a trailing "para o cartão ..." is excluded.
    private val APROVADA_EM_MERCHANT_REGEX = Regex(
        """aprovad[ao]\s+em\s+(.+?)\s+para\b""",
        RegexOption.IGNORE_CASE,
    )

    // Prefix is case-insensitive ((?i:...)); the merchant capture stays case-sensitive so it only
    // grabs an UPPERCASE-led run and stops at the first lowercase-led context word ("aprovada", "em").
    private val MERCHANT_REGEX = Regex(
        """(?i:\bcompra\s+em|\bem)\s+(?!R\$)([\p{Lu}\d][\p{Lu}\d&.*\-]*(?:\s+(?!R\$)[\p{Lu}\d][\p{Lu}\d&.*\-]*)*)""",
    )

    private val DATE_REGEX = Regex("""\b(\d{2})/(\d{2})(/\d{4})?\b""")
    private val DATE_FORMAT_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val FINAL_REGEX = Regex("""final\s+\d{4}""")
}
