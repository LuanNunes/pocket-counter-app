package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Splits notification text into tappable tokens and conservatively pre-assigns at most one
 * token per role from the backend-parsed fields. Pure Kotlin: no Android dependencies.
 */
object NotificationTokenizer {

    /** Normalizes a BR-formatted currency token ("R$ 1.234,56", "RS 30,96", "BRL 123,45") to a [BigDecimal]. */
    fun parseBrAmount(text: String): BigDecimal? =
        text.replace(Regex("R\\$|RS|BRL"), "")
            .replace(".", "")
            .replace(",", ".")
            .trim()
            .toBigDecimalOrNull()

    fun tokenize(text: String, parsed: ParsedNotification): List<Token> {
        val rawTokens = text.split(WHITESPACE).filter { it.isNotEmpty() }
        val tokens = rawTokens.map { Token(text = it) }.toMutableList()

        assignAmount(tokens, parsed.amount)
        assignMerchant(tokens, parsed.merchantRaw)
        assignPayment(tokens, parsed.paymentHint)
        assignDate(tokens, parsed.date)

        return tokens
    }

    private fun assignAmount(tokens: MutableList<Token>, amount: BigDecimal?) {
        amount ?: return
        val index = tokens.indexOfFirst { token ->
            token.role == null && parseBrAmount(token.text)?.compareTo(amount) == 0
        }
        if (index >= 0) {
            tokens[index] = tokens[index].copy(role = TokenRole.AMOUNT, value = amount.toPlainString())
        }
    }

    private fun assignMerchant(tokens: MutableList<Token>, merchantRaw: String?) {
        val merchant = merchantRaw?.takeIf { it.isNotBlank() } ?: return
        val words = merchant.split(WHITESPACE).filter { it.isNotEmpty() }

        findRun(tokens, words)?.let { range ->
            for (i in range) tokens[i] = tokens[i].copy(role = TokenRole.MERCHANT, value = merchant)
            return
        }

        // The parser strips acquirer prefixes ("DL*UberRides" → "UberRides"), so a single-word
        // merchant no longer matches its token verbatim. Fall back to a contains-match so the
        // "DL*UberRides" chip still highlights.
        if (words.size != 1) return
        val index = tokens.indexOfFirst { it.role == null && it.text.contains(merchant, ignoreCase = true) }
        if (index >= 0) tokens[index] = tokens[index].copy(role = TokenRole.MERCHANT, value = merchant)
    }

    private fun assignPayment(tokens: MutableList<Token>, paymentHint: String?) {
        val hint = paymentHint?.takeIf { it.isNotBlank() } ?: return

        // "final NNNN": highlight the "final" token and the 4-digit token that follows it.
        val finalIndex = tokens.indexOfFirst { it.role == null && it.text.equals("final", ignoreCase = true) }
        val digitsIndex = finalIndex + 1
        if (finalIndex >= 0 && digitsIndex <= tokens.lastIndex && isDigitsToken(tokens[digitsIndex])) {
            tokens[finalIndex] = tokens[finalIndex].copy(role = TokenRole.PAYMENT, value = hint)
            tokens[digitsIndex] = tokens[digitsIndex].copy(role = TokenRole.PAYMENT, value = hint)
            return
        }

        // "cartão"/"conta": highlight the single word token.
        val index = tokens.indexOfFirst { it.role == null && stripEdgePunctuation(it.text).equals(hint, ignoreCase = true) }
        if (index >= 0) tokens[index] = tokens[index].copy(role = TokenRole.PAYMENT, value = hint)
    }

    private fun assignDate(tokens: MutableList<Token>, date: LocalDate?) {
        date ?: return
        val dd = "%02d".format(date.dayOfMonth)
        val mm = "%02d".format(date.monthValue)
        val index = tokens.indexOfFirst { it.role == null && matchesDate(it.text, dd, mm) }
        if (index >= 0) tokens[index] = tokens[index].copy(role = TokenRole.DATE, value = date.toString())
    }

    private fun isDigitsToken(token: Token): Boolean {
        val cleaned = stripEdgePunctuation(token.text)
        return token.role == null && cleaned.isNotEmpty() && cleaned.all { it.isDigit() }
    }

    private fun matchesDate(tokenText: String, dd: String, mm: String): Boolean {
        val match = DATE_TOKEN_REGEX.matchEntire(stripEdgePunctuation(tokenText)) ?: return false
        return match.groupValues[1] == dd && match.groupValues[2] == mm
    }

    private fun stripEdgePunctuation(text: String): String = text.trim().trim(',', '.', ';', ':')

    /** Locates a contiguous run of unassigned tokens whose text matches [words] case-insensitively. */
    private fun findRun(tokens: List<Token>, words: List<String>): IntRange? {
        if (words.isEmpty()) return null
        for (start in 0..tokens.size - words.size) {
            val matches = words.indices.all { offset ->
                val token = tokens[start + offset]
                token.role == null && matchesMerchantWord(token.text, words[offset], isFirstWord = offset == 0)
            }
            if (matches) return start until (start + words.size)
        }
        return null
    }

    // Requiring the "*" (not a plain suffix match) stops a false match on e.g. word "AS" vs token
    // "CASAS".
    private fun matchesMerchantWord(tokenText: String, word: String, isFirstWord: Boolean): Boolean {
        val cleaned = stripEdgePunctuation(tokenText)
        if (cleaned.equals(word, ignoreCase = true)) return true
        return isFirstWord && cleaned.endsWith("*$word", ignoreCase = true)
    }

    private val WHITESPACE = Regex("\\s+")
    private val DATE_TOKEN_REGEX = Regex("""(\d{2})/(\d{2})(?:/\d{4})?""")
}
