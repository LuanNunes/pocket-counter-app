package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
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

    /**
     * Read-side heal for stored parses that predate a parser fix. It has to happen on read because
     * the backend stores the parsed* fields verbatim and exposes no notification-update endpoint,
     * so already-captured rows can never be backfilled.
     *
     * A blank/null stored value is always re-parsed. A non-blank one is only re-parsed when it
     * looks truncated — every occurrence of it in [text] ends mid-token, immediately followed by a
     * letter or digit, which is a mid-word parser cut rather than a deliberate short name. A stored
     * value never present in [text] at all is left alone, and a stored value is never blanked out
     * by a fresh parse that comes back null.
     */
    fun healMerchant(parsed: ParsedNotification, text: String): ParsedNotification {
        val stored = parsed.merchantRaw
        if (stored.isNullOrBlank()) return parsed.copy(merchantRaw = parseMerchant(text))
        if (!isTruncatedMerchant(stored, text)) return parsed
        val healed = parseMerchant(text) ?: return parsed
        return parsed.copy(merchantRaw = healed)
    }

    private fun isTruncatedMerchant(stored: String, text: String): Boolean {
        val occurrences = Regex(Regex.escape(stored), RegexOption.IGNORE_CASE).findAll(text).toList()
        if (occurrences.isEmpty()) return false
        return occurrences.all { match ->
            val next = match.range.last + 1
            next < text.length && (text[next].isLetter() || text[next].isDigit())
        }
    }

    private fun parseAmount(text: String): BigDecimal? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        return NotificationTokenizer.parseBrAmount(match.value)
    }

    private fun parseType(text: String): TransactionType? {
        if (InvoicePaymentDetector.isInvoicePaymentText(text)) return null
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
     * Merchant extraction, tried most-specific first: card "final NNNN - <merchant> valor", card
     * "aprovada em <merchant> para", the generic run of complete UPPERCASE/digit words (stopping at
     * the first word that isn't fully uppercase/digit, so a Title-Case word's leading capital is
     * never consumed as a false continuation), then the loose Itaú-style "em <merchant>, dd/MM"
     * pattern last — its `[^,\n]` capture is bounded only by the next comma,
     * so it readily over-captures (e.g. trailing context words) and must lose to a tighter pattern
     * whenever one also matches.
     *
     * Priority resolves before match position: a later match of a higher-priority pattern must
     * never lose to an earlier match of a lower-priority one. That's why only the em-comma-date
     * pattern retries past a match whose capture fails validation ([Regex.findAll] + a `guard`); the
     * other three stop at their first match ([Regex.find], no guard) — walking one of them past an
     * invalid match risks reaching an unrelated later clause (e.g. a bank footer's "Avise em ITAU")
     * before this function ever falls through to the next pattern in priority order.
     */
    private fun parseMerchant(text: String): String? =
        MERCHANT_PATTERNS.firstNotNullOfOrNull { (pattern, guard) -> extractMerchant(pattern, text, guard) }

    private fun extractMerchant(pattern: Regex, text: String, guard: ((String) -> Boolean)?): String? {
        if (guard == null) return pattern.find(text)?.groupValues?.get(1)?.let(::cleanMerchant)
        return pattern.findAll(text).firstNotNullOfOrNull { match ->
            match.groupValues[1].takeIf(guard)?.let(::cleanMerchant)
        }
    }

    // Applied only to the em-comma-date pattern, the loosest of the four (a bare "em <text>, dd/MM",
    // no business keyword to anchor it) and the only one retried via findAll — a rejected candidate
    // there can safely fall through to a later match because of the `, dd/MM` anchor plus this guard;
    // the other three keep a single find() so a rejected/short match doesn't walk further into the
    // text (see the bank-footer hazard in parseMerchant's KDoc).
    private fun isPlausibleMerchant(raw: String): Boolean =
        raw.split(WHITESPACE_REGEX).none { word -> word.trim('.', ',', ';', ':', '-').lowercase() in NON_MERCHANT_WORDS }

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

    /**
     * The explicit payment method named in the text ("no débito", "via pix", "em dinheiro"), or null.
     * "cartão"/"conta" stay ambiguous (a card can be credit or debit), so they are NOT mapped here —
     * those keep flowing through the card / "final NNNN" path.
     */
    fun parsePaymentMethod(text: String): PaymentMethod? {
        val lower = text.lowercase()
        return when {
            lower.containsWord("débito") || lower.containsWord("debito") -> PaymentMethod.DEBIT
            lower.containsWord("crédito") || lower.containsWord("credito") -> PaymentMethod.CREDIT
            lower.containsWord("pix") -> PaymentMethod.PIX
            lower.containsWord("dinheiro") || lower.containsWord("espécie") || lower.containsWord("especie") ->
                PaymentMethod.CASH
            else -> null
        }
    }

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
    // "aprovada"/"aprovado" and "autoriza*" cover card purchase & pre-authorization pushes
    // ("Compra aprovada", "Pré-autorização aprovada", "Transação aprovada") that lack the word
    // "compra". Income pushes say "recebido/creditado", and the income guard resolves any overlap.
    private val EXPENSE_WORDS = listOf(
        "compra", "débito", "debito", "pagamento", "saque", "fatura",
        "aprovada", "aprovado", "autorizada", "autorizado", "autorização", "autorizacao",
    )

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

    // Itaú push: "... em DL*UberRides, 08/08 as 18:53 ...". The prefix's case-insensitivity is an
    // inline (?i:...) group, not RegexOption.IGNORE_CASE — the option would also make \p{Lu} match
    // lowercase and evaporate the digit/lowercase-context guard on the capture's first char.
    private val EM_COMMA_DATE_MERCHANT_REGEX = Regex(
        """(?i:\bem)\s+(?!R\$)(\p{Lu}[^,\n]{1,59}),\s*\d{1,2}/\d{1,2}\b""",
    )

    // Shared between the word body and its completion lookahead below so the two classes can
    // never drift apart — a punctuation char added to one but not the other reopens the bug where
    // a failed word backtracks into a truncated acquirer prefix ("DL*UberRides" -> "DL").
    private const val MERCHANT_WORD_PUNCTUATION = "&.*\\-"

    // A complete UPPERCASE/digit word: starts with [\p{Lu}\d], may continue with more of the same
    // plus the punctuation above, and — via the trailing negative lookahead — must not be followed
    // by more letters/digits/punctuation. That lookahead is what turns a Title-Case word's leading
    // capital ("Extra") from a one-character false continuation into a hard stop, without also
    // rejecting a genuine single-letter word ("LOJA A BAHIA") whose next char is whitespace.
    private const val MERCHANT_WORD =
        """[\p{Lu}\d][\p{Lu}\d$MERCHANT_WORD_PUNCTUATION]*(?![\p{L}\p{N}$MERCHANT_WORD_PUNCTUATION])"""

    // Prefix is case-insensitive ((?i:...)); the merchant capture stays case-sensitive. The run of
    // words is wrapped in an optional group behind the same first-char gate the prefix always used
    // (`(?!R\$)(?=[\p{Lu}\d])`): when the first word fails to complete (e.g. "DL*UberRides", where
    // every candidate length is followed by a letter or the shared punctuation), the group matches
    // empty instead of the match position advancing — cleanMerchant then rejects the empty capture
    // on length, with no retry, so a later bank-footer "em ITAU" is never reached by this pattern.
    private val MERCHANT_REGEX = Regex(
        """(?i:\bcompra\s+em|\bem)\s+(?!R\$)(?=[\p{Lu}\d])($MERCHANT_WORD(?:\s+(?!R\$)$MERCHANT_WORD)*)?""",
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    // Words that make a capture rejected if ANY of them is present — see [isPlausibleMerchant]. Not
    // connectors ("de", "da", "do", "com", "para", "pelo"...): real BR merchant names use those
    // ("Padaria da Esquina", "Bar do Zé"), so they'd blank out the population this pattern exists
    // for. Case is folded via lowercase(); accents are not, so both forms are listed explicitly.
    private val NON_MERCHANT_WORDS = setOf(
        "no", "na", "nos", "nas", "seu", "sua", "em", "as", "às", "valor", "compra", "pagamento",
        "aprovada", "aprovado", "parcela", "parcelas", "vezes", "final", "cartao", "cartão",
        "conta", "corrente", "credito", "crédito", "debito", "débito",
        "janeiro", "fevereiro", "março", "marco", "abril", "maio", "junho", "julho",
        "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    private val MERCHANT_PATTERNS: List<Pair<Regex, ((String) -> Boolean)?>> = listOf(
        CARD_FINAL_MERCHANT_REGEX to null,
        APROVADA_EM_MERCHANT_REGEX to null,
        MERCHANT_REGEX to null,
        EM_COMMA_DATE_MERCHANT_REGEX to ::isPlausibleMerchant,
    )

    private val DATE_REGEX = Regex("""\b(\d{2})/(\d{2})(/\d{4})?\b""")
    private val DATE_FORMAT_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val FINAL_REGEX = Regex("""final\s+\d{4}""")
}
