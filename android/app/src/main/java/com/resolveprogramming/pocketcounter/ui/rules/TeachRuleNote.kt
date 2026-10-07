package com.resolveprogramming.pocketcounter.ui.rules

import com.resolveprogramming.pocketcounter.data.repository.RuleWriteOutcome

private const val SAVED_PREFIX = "Lançado ✓"
private const val NOT_CREATED = "$SAVED_PREFIX · regra não criada"

/**
 * What to tell the user about a rule written alongside a transaction, or null when there is nothing
 * to say. The transaction is already saved, so none of these is an error.
 *
 * Shared by the wizard and quick-add: the user flipped the same toggle on both.
 */
fun teachRuleNote(outcome: RuleWriteOutcome): String? = when (outcome) {
    RuleWriteOutcome.Saved -> null
    RuleWriteOutcome.Duplicate -> "$SAVED_PREFIX · a regra já existia."
    is RuleWriteOutcome.Rejected -> rejectedNote(outcome.message)
}

/** The server's own localized reason, e.g. the 500-rule cap, which only a write can discover. */
private fun rejectedNote(message: String?): String {
    val reason = message?.trim()?.takeIf { it.isNotEmpty() } ?: return "$NOT_CREATED."
    return "$NOT_CREATED: $reason"
}
