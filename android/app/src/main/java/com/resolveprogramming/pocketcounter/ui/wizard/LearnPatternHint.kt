package com.resolveprogramming.pocketcounter.ui.wizard

import com.resolveprogramming.pocketcounter.domain.model.TransactionType

/** What the "Aprender este padrão" toggle promises, or why it is unavailable. */
internal fun learnPatternHint(state: WizardUiState, pattern: String): String {
    if (state.draft.type == TransactionType.INCOME) {
        return "Regras valem só para despesas."
    }
    val tag = state.draft.teachableTag(state.allTags) ?: return "Escolha uma tag para aprender o padrão."
    if (state.draft.tagIds.size == 1) {
        return "Próximas notificações com \"$pattern\" vão receber a tag ${tag.name} automaticamente."
    }
    return "Próximas notificações com \"$pattern\" vão receber a tag ${tag.name}. " +
        "As outras tags valem só para este lançamento."
}
