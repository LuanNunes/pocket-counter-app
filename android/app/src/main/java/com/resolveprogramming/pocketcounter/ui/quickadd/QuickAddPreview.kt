package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.NewTagRequest
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.FormErrorNote
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.MoneyTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketDatePickerDialog
import com.resolveprogramming.pocketcounter.ui.components.TagPicker
import com.resolveprogramming.pocketcounter.ui.components.ctaSpinner
import com.resolveprogramming.pocketcounter.ui.components.tagUniverse
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.dashedTopRule
import com.resolveprogramming.pocketcounter.ui.wizard.LearnPatternToggle
import com.resolveprogramming.pocketcounter.ui.wizard.icon
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private val brlFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private const val TAG_PICK_LIMIT = 8

internal fun formatBrl(amount: BigDecimal): String = brlFormat.format(amount.abs())

/** The handoff's `16/05 · hoje`; the ledger rows keep their own [formatLedgerDate]. */
internal fun formatQuickAddDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val stamp = "%02d/%02d".format(date.dayOfMonth, date.monthValue)
    if (date == today) return "$stamp · hoje"
    if (date == today.minusDays(1)) return "$stamp · ontem"
    return stamp
}

/** What will be filed, with where every value came from. Nothing here is written until *Lançar*. */
@Composable
fun ColumnScope.QuickAddPreviewStage(
    state: QuickAddUiState,
    callbacks: QuickAddPreviewCallbacks,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        PreviewHero(state)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "O que vou lançar".uppercase(),
            style = PocketTheme.typography.label,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(8.dp))
        PreviewRows(state, callbacks)
        Spacer(Modifier.height(16.dp))
    }

    // Pinned below the scroll: the teach decision and a save failure apply to the whole lançamento,
    // so neither may scroll out of sight of the button it gates.
    TeachBlock(state, callbacks)
    if (state.saveFailed) {
        Spacer(Modifier.height(12.dp))
        FormErrorNote("Não foi possível lançar. Tente de novo.")
    }
    state.duplicate?.let {
        Spacer(Modifier.height(12.dp))
        DuplicateConflictCard(
            amount = state.draft.amount,
            name = state.draft.name,
            date = state.draft.date,
            onConfirm = callbacks.onSaveAnyway,
            onCancel = callbacks.onDismissDuplicate,
        )
    }

    QuickAddFooter(
        secondaryText = "Editar frase",
        onSecondary = callbacks.onEditSentence,
        // The conflict block owns the decision while it is up; two primaries would compete.
        primaryText = savePrimaryText(state).takeIf { state.duplicate == null },
        onPrimary = callbacks.onSave,
        primaryEnabled = state.canSave,
        primaryLeading = ctaSpinner().takeIf { state.isSaving },
    )
}

private fun savePrimaryText(state: QuickAddUiState): String =
    "Lançando...".takeIf { state.isSaving } ?: "Lançar"

/** Every callback the preview needs, so the row set stays one argument list instead of twelve. */
data class QuickAddPreviewCallbacks(
    val onToggleRow: (IntentField) -> Unit,
    val onAmount: (BigDecimal?) -> Unit,
    val onName: (String) -> Unit,
    val onDate: (LocalDate) -> Unit,
    val onType: (TransactionType) -> Unit,
    val onPaymentMethod: (PaymentMethod?) -> Unit,
    val onPaymentStatus: (PaymentStatus) -> Unit,
    val onCard: (String) -> Unit,
    val onToggleTag: (String) -> Unit,
    val onOpenNewTag: (contextId: String?) -> Unit,
    val onCloseNewTag: () -> Unit,
    val onCreateTag: (NewTagRequest) -> Unit,
    val onTeachEnabled: (Boolean) -> Unit,
    val onTeachPattern: (String) -> Unit,
    val onSave: () -> Unit,
    val onSaveAnyway: () -> Unit,
    val onDismissDuplicate: () -> Unit,
    val onEditSentence: () -> Unit,
    val onRetryLookups: () -> Unit,
)

@Composable
private fun PreviewHero(state: QuickAddUiState) {
    val type = state.draft.type
    val status = " · ${statusWord(state.draft.statusPayment).lowercase()}"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.draft.amount?.let { amount ->
            AmountText(
                amount = amount.signedFor(type),
                type = type,
                showSign = true,
                style = PocketTheme.typography.monoBalance.copy(
                    fontSize = 30.sp,
                    letterSpacing = (-0.03f).em,
                ),
                autoSize = true,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.draft.name.orEmpty(),
            style = PocketTheme.typography.body.copy(
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = PocketTheme.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${typeWord(type)}$status",
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )
    }
}

internal fun BigDecimal.signedFor(type: TransactionType?): BigDecimal =
    negate().takeIf { type == TransactionType.EXPENSE } ?: this

internal fun typeWord(type: TransactionType?): String =
    "Receita".takeIf { type == TransactionType.INCOME } ?: "Despesa"

// "Efetuada" is the app's word for PAID (TransacaoDetailSheet's Efetuada / Pendente toggle).
internal fun statusWord(status: PaymentStatus): String =
    "Pago".takeIf { status == PaymentStatus.PAID } ?: "Pendente"

@Composable
private fun PreviewRows(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    val draft = state.draft
    PocketCard(
        shape = PocketTheme.shapes.field,
        borderColor = PocketTheme.colors.line,
        elevated = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ReadingRow(
                label = "Valor",
                value = draft.amount?.let(::formatBrl),
                absentText = "não informado",
                provenance = state.provenanceOf(IntentField.AMOUNT),
                expanded = state.expandedRow == IntentField.AMOUNT,
                onToggle = { callbacks.onToggleRow(IntentField.AMOUNT) },
                mono = true,
                editor = {
                    MoneyTextField(
                        amount = draft.amount,
                        onAmountChange = callbacks.onAmount,
                        textStyle = PocketTheme.typography.monoTotal,
                        autoFocus = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
            RowDivider()
            ReadingRow(
                label = "Descrição",
                value = draft.name?.takeIf { it.isNotBlank() },
                absentText = "não informado",
                provenance = state.provenanceOf(IntentField.NAME),
                expanded = state.expandedRow == IntentField.NAME,
                onToggle = { callbacks.onToggleRow(IntentField.NAME) },
                editor = {
                    FormTextField(
                        value = draft.name.orEmpty(),
                        onValueChange = callbacks.onName,
                        placeholder = "Ex.: Supermercado",
                    )
                },
            )
            RowDivider()
            ReadingRow(
                label = "Tipo",
                value = draft.type?.let(::typeWord),
                absentText = "não informado",
                provenance = state.provenanceOf(IntentField.TYPE),
                expanded = state.expandedRow == IntentField.TYPE,
                onToggle = { callbacks.onToggleRow(IntentField.TYPE) },
                editor = { TypePicks(draft.type, callbacks.onType) },
            )
            RowDivider()
            DateRow(state, callbacks)
            RowDivider()
            ReadingRow(
                label = "Forma de Pagamento",
                value = draft.paymentMethod?.label(),
                absentText = "não informado",
                provenance = state.provenanceOf(IntentField.PAYMENT_METHOD),
                expanded = state.expandedRow == IntentField.PAYMENT_METHOD,
                onToggle = { callbacks.onToggleRow(IntentField.PAYMENT_METHOD) },
                leadingIcon = draft.paymentMethod?.icon(),
                editor = { MethodPicks(state, callbacks.onPaymentMethod) },
            )
            if (state.showsCardRow) {
                RowDivider()
                ReadingRow(
                    label = "Cartão",
                    value = draft.cardId?.let(state::cardName),
                    absentText = "não informado",
                    provenance = state.provenanceOf(IntentField.CARD),
                    expanded = state.expandedRow == IntentField.CARD,
                    onToggle = { callbacks.onToggleRow(IntentField.CARD) },
                    editor = { CardPicks(state, callbacks.onCard) },
                )
            }
            RowDivider()
            StatusRow(state, callbacks)
            RowDivider()
            TagRow(state, callbacks)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = PocketTheme.colors.line)
}

/** `.qa-rev-hint`: supplementary, never a control. */
@Composable
private fun QuickHint(text: String, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        Text(
            text = text,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
        return
    }
    Box(
        modifier = Modifier
            // Full width, or the tappable box is a thin strip beside text that overflows it.
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            // Merged, or the label and the click action are two nodes and neither is usable alone.
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.accent,
        )
    }
}

/** `.qa-rev-opts`: the 48dp target boxes supply the vertical rhythm, so rows need no extra gap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}

/**
 * The only correction path for a date: the server reads `hoje`/`ontem`/`anteontem` and echoes the
 * reference date for everything else, and a date never appears in `missing`. The chips never hide.
 */
@Composable
private fun DateRow(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val date = state.draft.date

    ReadingRow(
        label = "Data",
        value = date?.let { formatQuickAddDate(it, today) },
        absentText = "não informado",
        provenance = state.provenanceOf(IntentField.DATE),
        leadingIcon = Icons.Filled.CalendarMonth,
        pinnedEditor = {
            PickRow {
                QuickPick(
                    label = "Hoje",
                    selected = date == today,
                    onClick = { callbacks.onDate(today) },
                )
                QuickPick(
                    label = "Ontem",
                    selected = date == today.minusDays(1),
                    onClick = { callbacks.onDate(today.minusDays(1)) },
                )
                QuickPick(
                    label = "Anteontem",
                    selected = date == today.minusDays(2),
                    onClick = { callbacks.onDate(today.minusDays(2)) },
                )
                // The handoff's three relative chips cannot reach "dia 5 do mês passado".
                QuickPick(
                    label = "Outra data…",
                    selected = false,
                    onClick = { showPicker = true },
                )
            }
        },
    )

    if (showPicker) {
        PocketDatePickerDialog(
            initialDate = date ?: today,
            onConfirm = callbacks.onDate,
            onDismiss = { showPicker = false },
        )
    }
}

/** No sentence reveals this: *paguei* and *gastei* are past tense, so the status is picked, never read. */
@Composable
private fun StatusRow(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    val status = state.draft.statusPayment
    ReadingRow(
        label = "Situação",
        value = statusWord(status),
        absentText = "não informado",
        provenance = state.provenanceOf(IntentField.STATUS),
        pinnedEditor = {
            PickRow {
                PaymentStatus.entries.forEach { option ->
                    QuickPick(
                        label = statusWord(option),
                        selected = status == option,
                        onClick = { callbacks.onPaymentStatus(option) },
                    )
                }
            }
        },
    )
}

@Composable
private fun TypePicks(selected: TransactionType?, onSelect: (TransactionType) -> Unit) {
    PickRow {
        TransactionType.entries.forEach { type ->
            QuickPick(
                label = typeWord(type),
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun MethodPicks(state: QuickAddUiState, onSelect: (PaymentMethod?) -> Unit) {
    PickRow {
        state.selectableMethods.forEach { method ->
            val isSelected = state.draft.paymentMethod == method
            QuickPick(
                label = method.label(),
                selected = isSelected,
                onClick = { onSelect(method.takeIf { !isSelected }) },
            )
        }
    }
}

@Composable
private fun CardPicks(state: QuickAddUiState, onSelect: (String) -> Unit) {
    if (state.cards.isEmpty()) {
        QuickHint("Nenhum cartão ainda.")
        return
    }
    PickRow {
        state.cards.forEach { card ->
            QuickPick(
                label = card.name,
                selected = state.draft.cardId == card.id,
                onClick = { onSelect(card.id) },
            )
        }
    }
}

@Composable
private fun TagRow(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    val tag = state.draft.tagIds.firstOrNull()?.let(state::tagOf)
    val dotColor = tag?.color ?: tag?.idContext?.let { id ->
        state.contexts.firstOrNull { it.id == id }?.color
    }
    ReadingRow(
        label = "Categoria",
        value = tag?.name,
        absentText = "sem categoria",
        provenance = state.provenanceOf(IntentField.TAG),
        expanded = state.expandedRow == IntentField.TAG,
        onToggle = { callbacks.onToggleRow(IntentField.TAG) },
        dotColor = dotColor?.let { Color(it) },
        hint = state.categoryHint,
        editor = { TagPicks(state, callbacks) },
    )
}

@Composable
private fun TagPicks(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    val kind = state.draft.type ?: TransactionType.EXPENSE
    val picks = state.suggestedTagPicks(kind)

    Column(modifier = Modifier.fillMaxWidth()) {
        PickRow {
            picks.forEach { pick ->
                QuickPick(
                    label = pick.name,
                    selected = pick.id in state.draft.tagIds,
                    onClick = { callbacks.onToggleTag(pick.id) },
                    dotColor = tagDotColor(pick, state),
                )
            }
            if (state.canCreateTag && !state.isCreatingTag) {
                QuickPick(
                    label = newTagLabel(kind),
                    selected = false,
                    onClick = { callbacks.onOpenNewTag(null) },
                    dashed = true,
                )
            }
        }
        // A failed fetch must not claim there are no categories: it does not know.
        if (state.lookupsFailed) {
            QuickHint(
                text = "Não foi possível carregar as categorias. Toque para tentar de novo.",
                onClick = callbacks.onRetryLookups,
            )
        }
        // Categories are made in Contextos & Tags, so with none there is no tag to create here.
        if (!state.lookupsFailed && !state.canCreateTag) {
            QuickHint("Crie categorias em Mais › Contextos & Tags para usar tags.")
        }
        if (state.isCreatingTag) {
            Spacer(Modifier.height(9.dp))
            NewTagForm(
                type = kind,
                contexts = state.contexts,
                isSaving = state.isSavingTag,
                errorMessage = state.tagFormError,
                onCancel = callbacks.onCloseNewTag,
                onCreate = callbacks.onCreateTag,
                initialContextId = state.newTagContextId ?: state.suggestedContext?.id,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .dashedTopRule(PocketTheme.colors.line2)
                .padding(top = 13.dp),
        ) {
            TagPicker(
                type = kind,
                tags = state.tags,
                contexts = state.contexts,
                selectedTagIds = state.draft.tagIds,
                onToggleTag = callbacks.onToggleTag,
                // The browse branches route to the same inline form, under the context they
                // had open; the name they typed is the form's own field.
                onCreateTag = openNewTagFromPicker(callbacks)
                    .takeIf { state.canCreateTag && !state.isCreatingTag },
                // The row already offers "+ Nova tag"; the picker's root chip would duplicate it.
                showRootCreate = false,
            )
        }
    }
}

private fun openNewTagFromPicker(
    callbacks: QuickAddPreviewCallbacks,
): (String?, String) -> Unit = { contextId, _ -> callbacks.onOpenNewTag(contextId) }

/** The suggested category's tags when the read matched one; otherwise the first of that kind. */
private fun QuickAddUiState.suggestedTagPicks(kind: TransactionType): List<Tag> {
    val universe = tagUniverse(tags, kind)
    val suggested = suggestedContext ?: return universe.take(TAG_PICK_LIMIT)
    return universe.filter { it.idContext == suggested.id }
}

private fun newTagLabel(kind: TransactionType): String =
    "+ Nova categoria".takeIf { kind == TransactionType.INCOME } ?: "+ Nova tag"

private fun tagDotColor(tag: Tag, state: QuickAddUiState): Color? {
    val argb = tag.color ?: tag.idContext?.let { id ->
        state.contexts.firstOrNull { it.id == id }?.color
    }
    return argb?.let { Color(it) }
}

@Composable
private fun TeachBlock(state: QuickAddUiState, callbacks: QuickAddPreviewCallbacks) {
    if (!state.canTeach) return
    val tagName = state.teachableTag?.name.orEmpty()
    val pattern = state.teachPattern.trim()
    // canTeach already guarantees a tag, so the hint only ever promises; an unusable pattern is the
    // error note's business.
    val hint = "Próximas frases com \"$pattern\" vão receber a tag $tagName automaticamente."

    Column(modifier = Modifier.fillMaxWidth()) {
        LearnPatternToggle(
            checked = state.teachEnabled,
            hint = hint,
            onCheckedChange = callbacks.onTeachEnabled,
        )
        if (state.teachEnabled) {
            Spacer(Modifier.height(12.dp))
            FormLabel("Padrão")
            Spacer(Modifier.height(8.dp))
            FormTextField(
                value = state.teachPattern,
                onValueChange = callbacks.onTeachPattern,
                placeholder = "supermercado",
            )
            if (!state.teachPatternValid) {
                Spacer(Modifier.height(8.dp))
                FormErrorNote(
                    "O padrão precisa de pelo menos " +
                        "${ClassificationRule.MIN_PATTERN_LENGTH} caracteres.",
                )
            }
        }
    }
}
