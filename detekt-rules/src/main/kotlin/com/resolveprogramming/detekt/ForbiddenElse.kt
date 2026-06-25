package com.resolveprogramming.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Forbids every `else`: the `else` branch of an `if` (statement or expression form)
 * and the `else ->` arm of a `when`. Prefer guard clauses / early returns, elvis
 * (`?:`), or exhaustive `when` over sealed types and enums.
 *
 * A `when` used as an expression on a `String`/`Int` cannot be made exhaustive — convert
 * it to a function with early-return guard clauses, or a map lookup with an elvis default.
 * Genuinely unavoidable cases may use `@Suppress("ForbiddenElse")`.
 */
class ForbiddenElse(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "ForbiddenElse",
        severity = Severity.Style,
        description = "An `else` branch is not allowed. Use guard clauses / early returns, " +
            "elvis (?:), or an exhaustive `when` over a sealed type or enum.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitIfExpression(expression: KtIfExpression) {
        super.visitIfExpression(expression)
        val elseKeyword = expression.elseKeyword ?: return
        report(
            CodeSmell(
                issue,
                Entity.from(elseKeyword),
                "`else` on an `if` is not allowed. Use a guard clause (early return) or elvis (?:).",
            ),
        )
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        super.visitWhenExpression(expression)
        val elseEntry = expression.entries.firstOrNull { it.isElse } ?: return
        report(
            CodeSmell(
                issue,
                Entity.from(elseEntry),
                "`else ->` in a `when` is not allowed. Use exhaustive branches over a sealed " +
                    "type/enum, or restructure into early-return guard clauses.",
            ),
        )
    }
}
