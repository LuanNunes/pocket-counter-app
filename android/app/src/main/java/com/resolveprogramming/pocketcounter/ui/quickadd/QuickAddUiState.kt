package com.resolveprogramming.pocketcounter.ui.quickadd

import com.resolveprogramming.pocketcounter.data.repository.IntentReadFailure
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.FieldProvenance
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.IntentProvenance
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.model.canCreateTag
import com.resolveprogramming.pocketcounter.domain.model.isSatisfiedBy
import java.math.BigDecimal

enum class QuickAddStage { INPUT, ASK, PREVIEW, SAVED }

/** The backend refused the create as a repeat; [existingTransactionId] is only for a later "ver". */
data class DuplicateConflict(val existingTransactionId: String?)

/** The sentence cap the backend's 413 sits behind. */
const val QUICK_ADD_MAX_CHARS = 500

private const val MIN_SENTENCE_CHARS = 2

/**
 * Whether the draft carries a value for [field] — the draft, never `intent.reading`, so an answer or
 * a correction is what the preview badges and renders.
 */
internal fun hasValue(draft: WizardDraft, field: IntentField): Boolean = when (field) {
    IntentField.TYPE -> draft.type != null
    IntentField.AMOUNT -> draft.amount != null && draft.amount > BigDecimal.ZERO
    IntentField.DATE -> draft.date != null
    IntentField.NAME -> draft.hasUsableName()
    IntentField.PAYMENT_METHOD -> draft.paymentMethod != null
    IntentField.CARD -> draft.cardId != null
    IntentField.TAG -> draft.tagIds.isNotEmpty()
    // A draft always carries a status, so the row is never empty: it badges *assumido* until picked.
    IntentField.STATUS -> true
}

data class QuickAddUiState(
    val stage: QuickAddStage = QuickAddStage.INPUT,
    val text: String = "",
    val isReading: Boolean = false,
    val readFailure: IntentReadFailure? = null,
    /** Ticks down from the 429's `Retry-After`; null when no wait is pending. */
    val retrySeconds: Int? = null,
    /** The read, for [TransactionIntent.source], [TransactionIntent.cardCandidates] and `missing` only. */
    val intent: TransactionIntent? = null,
    /** The trimmed sentence [intent] was read from, so submitting it unchanged costs no read. */
    val readText: String? = null,
    val draft: WizardDraft = WizardDraft(),
    /** Fields the user answered or corrected; they badge *definido* whatever the server said. */
    val defined: Set<IntentField> = emptySet(),
    val askIndex: Int = 0,
    val expandedRow: IntentField? = null,
    val cards: List<CreditCard> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    /** A lookup fetch failed: the empty pills mean "unknown", not "none exist". */
    val lookupsFailed: Boolean = false,
    val enabledMethods: Set<PaymentMethod> = PaymentMethodPreferences.default,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val duplicate: DuplicateConflict? = null,
    val teachEnabled: Boolean = false,
    val teachPattern: String = "",
    val teachNote: String? = null,
    val savedTransactionId: String? = null,
    /** The inline create-tag form, open inside the Categoria row. */
    val isCreatingTag: Boolean = false,
    /** The context the form opens with selected; set when the create came from a drill-down. */
    val newTagContextId: String? = null,
    val isSavingTag: Boolean = false,
    val tagFormError: String? = null,
) {
    val missing: List<MissingField> get() = intent?.missing.orEmpty()

    val canCreateTag: Boolean get() = canCreateTag(draft.type, contexts)

    val currentAsk: MissingField? get() = missing.getOrNull(askIndex)

    val canSubmitSentence: Boolean
        get() = text.trim().length >= MIN_SENTENCE_CHARS && !isReading && retrySeconds == null

    val canConfirmAsk: Boolean get() = currentAsk?.isSatisfiedBy(draft) == true

    /**
     * The category the read matched, when it still exists. Null for income by design: a tag of that
     * kind cannot carry a context, so there is nothing to suggest and nothing to fall back to.
     */
    val suggestedContext: TagContext?
        get() {
            if (draft.type != TransactionType.EXPENSE) return null
            val id = intent?.idCategory ?: return null
            return contexts.firstOrNull { it.id == id }
        }

    /** `.qa-rev-hint`: a guess is only worth saying while the row carries no tag to contradict it. */
    val categoryHint: String?
        get() {
            if (draft.tagIds.isNotEmpty()) return null
            return suggestedContext?.let { "parece ${it.name}" }
        }

    /** The one tag a rule could carry, or null when this draft can teach nothing. */
    val teachableTag: Tag? get() = draft.teachableTag(tags)

    /** Only offered when the server did not already classify the sentence. */
    val canTeach: Boolean get() = intent?.idTag == null && teachableTag != null

    val teachPatternValid: Boolean
        get() = teachPattern.trim().length >= ClassificationRule.MIN_PATTERN_LENGTH

    /**
     * Payment method, tag and situação never gate this. The pattern does: silently dropping a rule
     * the user deliberately switched on would falsify the switch.
     */
    val canSave: Boolean
        get() {
            if (isSaving) return false
            // Mid-create the tag would land on an already-written transaction, attached to nothing.
            if (isSavingTag) return false
            if (teachEnabled && canTeach && !teachPatternValid) return false
            return hasValue(draft, IntentField.AMOUNT) &&
                hasValue(draft, IntentField.NAME) &&
                hasValue(draft, IntentField.TYPE)
        }

    /** The Cartão row exists for a cardless credit row too — never branch on `cardStatus`. */
    val showsCardRow: Boolean
        get() = draft.paymentMethod == PaymentMethod.CREDIT || intent?.resolvedCard != null

    fun provenanceOf(field: IntentField): FieldProvenance = IntentProvenance.of(
        field = field,
        source = intent?.source.orEmpty(),
        defined = defined,
        hasValue = hasValue(draft, field),
    )

    fun tagOf(tagId: String): Tag? = tags.firstOrNull { it.id == tagId }

    fun cardName(cardId: String): String? = cards.firstOrNull { it.id == cardId }?.name

    val selectableMethods: List<PaymentMethod>
        get() = PaymentMethodPreferences.selectable(enabledMethods, draft.paymentMethod, draft.type)

    override fun toString(): String =
        "QuickAddUiState(stage=$stage, missing=${missing.size}, askIndex=$askIndex, saving=$isSaving)"
}
