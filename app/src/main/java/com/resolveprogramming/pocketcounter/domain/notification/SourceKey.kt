package com.resolveprogramming.pocketcounter.domain.notification

import java.text.Normalizer

/**
 * Normalizes a free-text label (an app name, an issuer, a merchant) into a stable comparison key:
 * lowercase, diacritics stripped, everything but letters/digits removed. Shared by every matcher
 * that keys a learned mapping on a label, so "Itaú" and "ITAU" always resolve to the same key.
 */
object SourceKey {

    fun normalize(raw: String): String {
        val normalized = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITIC_REGEX.replace(normalized, "")
        return NON_ALPHANUMERIC_REGEX.replace(withoutDiacritics, "")
    }

    private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]")
}
