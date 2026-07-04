package com.resolveprogramming.pocketcounter.ui.regras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.rules.DedupePlan
import com.resolveprogramming.pocketcounter.domain.rules.RuleDedupePlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegraDeleteTarget(val id: String, val patternLabel: String)

data class DedupePreview(
    val mergedRules: Int,
    val deletedRules: Int,
) {
    val isEmpty: Boolean get() = mergedRules == 0 && deletedRules == 0
}

data class RegrasUiState(
    val rules: List<ClassificationRule> = emptyList(),
    val cardsById: Map<String, CreditCard> = emptyMap(),
    val tagsById: Map<String, Tag> = emptyMap(),
    val contextsById: Map<String, TagContext> = emptyMap(),
    val confirmDelete: RegraDeleteTarget? = null,
    /** The rule whose payment method/card is being edited, or null when the edit sheet is closed. */
    val editTarget: ClassificationRule? = null,
    val savingEdit: Boolean = false,
    val dedupePreview: DedupePreview? = null,
    val isMerging: Boolean = false,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class RegrasViewModel @Inject constructor(
    private val ruleRepository: ClassificationRuleRepository,
    private val cardRepository: CardRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegrasUiState())
    val state: StateFlow<RegrasUiState> = _state.asStateFlow()

    /** The plan backing [state]'s [DedupePreview]; applied verbatim so counts and effect match. */
    private var pendingPlan: DedupePlan? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val rules = ruleRepository.getAll().getOrDefault(emptyList())
            val cards = cardRepository.getCards().getOrDefault(emptyList())
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = tagRepository.getAllContexts().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    rules = rules,
                    cardsById = cards.associateBy { c -> c.id },
                    tagsById = tags.associateBy { t -> t.id },
                    contextsById = contexts.associateBy { c -> c.id },
                    isLoading = false,
                )
            }
        }
    }

    fun openEdit(id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(editTarget = rule) }
    }

    fun cancelEdit() = _state.update { it.copy(editTarget = null) }

    /**
     * Persists a new payment method (and card, for CREDIT) onto the edited rule, then reloads. The
     * card is kept only for CREDIT, mirroring the wizard's payment invariant. Everything else on the
     * rule (patterns, type, tags) is carried through unchanged so the update never drops it.
     */
    fun saveEdit(paymentMethod: PaymentMethod?, cardId: String?) {
        val rule = _state.value.editTarget ?: return
        if (_state.value.savingEdit) return
        _state.update { it.copy(savingEdit = true) }
        viewModelScope.launch {
            val updated = rule.copy(
                paymentMethod = paymentMethod,
                cardId = cardId.takeIf { paymentMethod == PaymentMethod.CREDIT },
            )
            ruleRepository.update(updated)
                .onSuccess {
                    _state.update { it.copy(editTarget = null, savingEdit = false, toastMessage = "Regra atualizada") }
                    load()
                }
                .onFailure {
                    _state.update { it.copy(savingEdit = false, toastMessage = "Não foi possível atualizar") }
                }
        }
    }

    fun requestDelete(id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == id } ?: return
        val label = rule.patterns.firstOrNull().orEmpty().take(40)
        _state.update {
            it.copy(confirmDelete = RegraDeleteTarget(id, label))
        }
    }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val target = _state.value.confirmDelete ?: return
        viewModelScope.launch {
            ruleRepository.delete(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Regra excluída") }
                    load()
                }
                .onFailure {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Não foi possível excluir") }
                }
        }
    }

    /**
     * Computes a merge preview without mutating anything: reloads the rules and runs the pure
     * planner. The resulting [DedupePlan] is held so [applyDedupe] persists exactly what was shown.
     */
    fun previewDedupe() {
        if (_state.value.isMerging) return
        viewModelScope.launch {
            val all = ruleRepository.getAll().getOrDefault(emptyList())
            val plan = RuleDedupePlanner.plan(all)
            pendingPlan = plan
            _state.update {
                it.copy(dedupePreview = DedupePreview(plan.updates.size, plan.deletes.size))
            }
        }
    }

    fun dismissDedupe() {
        pendingPlan = null
        _state.update { it.copy(dedupePreview = null) }
    }

    /**
     * Persists the pending plan: every merged canonical is updated BEFORE any redundant rule is
     * deleted, so the surviving rule keeps the merged patterns before extras disappear.
     *
     * Crucially, if ANY canonical update fails, no deletes run at all — otherwise a rule whose
     * patterns weren't absorbed into its canonical would be deleted, silently losing those patterns.
     * The whole operation is idempotent/re-runnable, so withholding deletes just leaves the cleanup
     * partially done rather than destroying data.
     */
    fun applyDedupe() {
        val plan = pendingPlan ?: return
        if (_state.value.isMerging) return
        if (plan.updates.isEmpty() && plan.deletes.isEmpty()) {
            dismissDedupe()
            return
        }
        _state.update { it.copy(isMerging = true) }
        viewModelScope.launch {
            val updatesOk = plan.updates.all { rule -> ruleRepository.update(rule).isSuccess }
            var deletesOk = true
            if (updatesOk) {
                plan.deletes.forEach { id ->
                    ruleRepository.delete(id).onFailure { deletesOk = false }
                }
            }
            pendingPlan = null
            _state.update {
                it.copy(
                    isMerging = false,
                    dedupePreview = null,
                    toastMessage = if (updatesOk && deletesOk) "Regras mescladas"
                    else "Não foi possível mesclar todas as regras",
                )
            }
            load()
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
