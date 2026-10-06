package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule

/** How a rule write ended. */
sealed interface RuleWriteOutcome {
    data object Saved : RuleWriteOutcome

    /** A rule with that pattern and action is already stored. */
    data object Duplicate : RuleWriteOutcome

    /** The server refused the body. [message] is its own localized reason, null when it sent none. */
    data class Rejected(val message: String?) : RuleWriteOutcome
}

/**
 * Learned classification rules. Neither a write that hits the server's `(lower(pattern), action)`
 * unique index nor one it refuses on validation is an error: they come back as
 * [RuleWriteOutcome.Duplicate] and [RuleWriteOutcome.Rejected]. `Result.failure` is reserved for
 * something actually going wrong (refused up front, network, 5xx).
 */
interface ClassificationRuleRepository {
    suspend fun getAll(): Result<List<ClassificationRule>>
    suspend fun create(rule: ClassificationRule): Result<RuleWriteOutcome>

    /** Replaces the rule identified by [ClassificationRule.id] with the given values. */
    suspend fun update(rule: ClassificationRule): Result<RuleWriteOutcome>
    suspend fun delete(id: String): Result<Unit>
}
