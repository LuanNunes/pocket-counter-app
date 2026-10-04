package com.resolveprogramming.pocketcounter.domain.model

/** What a matched rule does: SUGGEST pre-fills the tag; IGNORE auto-ignores the notification. */
enum class RuleAction { SUGGEST, IGNORE }

/** Why a rule cannot be written; each maps to a 422 the backend would answer. */
enum class RuleWriteBlocker {
    PATTERN_TOO_LONG,
    PATTERN_WITHOUT_SIGNAL,
    SUGGEST_REQUIRES_TAG,
    IGNORE_FORBIDS_TAG,
    TAG_MUST_BE_EXPENSE,
}

/**
 * A rule has no scope: it matches [pattern] (CONTAINS, case-insensitive) on any notification and
 * carries exactly one tag. Identity on the server is `(lower(pattern), action)`.
 */
data class ClassificationRule(
    val id: String?,
    val pattern: String,
    val idTag: String?,
    val active: Boolean?,
    val appliedCount: Int,
    val action: RuleAction = RuleAction.SUGGEST,
) {
    /**
     * The first reason this rule would be refused, or null — reported in the backend's own order,
     * pattern before tag. [tagKind] is the kind of [idTag] when the caller knows it; null means
     * "unknown, not checked". The pattern is trimmed first, as the server trims it before checking.
     */
    fun writeBlocker(tagKind: TransactionType?): RuleWriteBlocker? {
        val trimmed = pattern.trim()
        if (trimmed.length > MAX_PATTERN_LENGTH) return RuleWriteBlocker.PATTERN_TOO_LONG
        if (trimmed.none(Char::isLetterOrDigit)) return RuleWriteBlocker.PATTERN_WITHOUT_SIGNAL
        if (action == RuleAction.SUGGEST && idTag == null) return RuleWriteBlocker.SUGGEST_REQUIRES_TAG
        if (action == RuleAction.IGNORE && idTag != null) return RuleWriteBlocker.IGNORE_FORBIDS_TAG
        if (tagKind == TransactionType.INCOME) return RuleWriteBlocker.TAG_MUST_BE_EXPENSE
        return null
    }

    companion object {
        /** The server's `MAX_PATTERN_LENGTH`, which is also the column width. */
        const val MAX_PATTERN_LENGTH = 500

        fun suggest(pattern: String, idTag: String): ClassificationRule = ClassificationRule(
            id = null,
            pattern = pattern,
            idTag = idTag,
            active = true,
            appliedCount = 0,
            action = RuleAction.SUGGEST,
        )

        /**
         * IGNORE rules carry a pattern only. A pattern the user ignored is a pattern the user does not
         * want to see again, so the rule is learned at the moment of the ignore.
         */
        fun ignore(pattern: String): ClassificationRule = ClassificationRule(
            id = null,
            pattern = pattern,
            idTag = null,
            active = true,
            appliedCount = 0,
            action = RuleAction.IGNORE,
        )
    }
}
