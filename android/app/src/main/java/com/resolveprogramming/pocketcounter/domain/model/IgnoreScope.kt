package com.resolveprogramming.pocketcounter.domain.model

/** What "Ignorar" should silence: just this one notification, a merchant pattern, or a whole source app. */
sealed interface IgnoreScope {
    data object ThisOnly : IgnoreScope
    data class Pattern(val pattern: String) : IgnoreScope
    data class Source(val app: String) : IgnoreScope
}
