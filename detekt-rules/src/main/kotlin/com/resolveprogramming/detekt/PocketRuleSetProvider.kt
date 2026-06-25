package com.resolveprogramming.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** Registers PocketCounter's custom detekt rules. */
class PocketRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "pocket"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ForbiddenElse(config),
        ),
    )
}
