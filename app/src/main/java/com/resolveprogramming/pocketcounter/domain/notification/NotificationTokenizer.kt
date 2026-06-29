package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import java.math.BigDecimal

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

        parsed.amount?.let { amount ->
            val index = tokens.indexOfFirst { token ->
                token.role == null && parseBrAmount(token.text)?.compareTo(amount) == 0
            }
            if (index >= 0) {
                tokens[index] = tokens[index].copy(role = TokenRole.AMOUNT, value = amount.toPlainString())
            }
        }

        parsed.merchantRaw?.takeIf { it.isNotBlank() }?.let { merchant ->
            val merchantWords = merchant.split(WHITESPACE).filter { it.isNotEmpty() }
            val range = findRun(tokens, merchantWords)
            if (range != null) {
                for (i in range) {
                    tokens[i] = tokens[i].copy(role = TokenRole.MERCHANT, value = merchant)
                }
            }
        }

        return tokens
    }

    /** Locates a contiguous run of unassigned tokens whose text matches [words] case-insensitively. */
    private fun findRun(tokens: List<Token>, words: List<String>): IntRange? {
        if (words.isEmpty()) return null
        for (start in 0..tokens.size - words.size) {
            val matches = words.indices.all { offset ->
                val token = tokens[start + offset]
                token.role == null && token.text.equals(words[offset], ignoreCase = true)
            }
            if (matches) return start until (start + words.size)
        }
        return null
    }

    private val WHITESPACE = Regex("\\s+")
}
