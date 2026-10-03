package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule

/**
 * Learned classification rules (Gestão → Regras aprendidas). M4 scope is list + delete:
 * the backend `ClassificationRuleDto` has no matchType / times-applied / active fields,
 * so those prototype affordances are intentionally absent.
 */
interface ClassificationRuleRepository {
    suspend fun getAll(): Result<List<ClassificationRule>>
    suspend fun create(rule: ClassificationRule): Result<Unit>

    /** Replaces the rule identified by [ClassificationRule.id] with the given values. */
    suspend fun update(rule: ClassificationRule): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
