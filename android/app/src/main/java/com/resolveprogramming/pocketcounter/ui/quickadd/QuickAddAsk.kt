package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.MoneyTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.formatLedgerDate
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.dashedBorder
import com.resolveprogramming.pocketcounter.ui.theme.pressScale
import com.resolveprogramming.pocketcounter.ui.wizard.icon
import com.resolveprogramming.pocketcounter.ui.wizard.label
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepType
import java.math.BigDecimal

/** Past this, two 48dp buttons cannot hold their labels side by side at 360dp. */
private const val SIDE_BY_SIDE_MAX_FONT_SCALE = 1.3f

/** One question per screen, strictly in the server's `missing` order. */
@Composable
fun ColumnScope.QuickAddAskStage(
    state: QuickAddUiState,
    onAmount: (BigDecimal?) -> Unit,
    onName: (String) -> Unit,
    onCard: (String) -> Unit,
    onSkipCard: () -> Unit,
    onType: (TransactionType) -> Unit,
    onConfirm: () -> Unit,
    onEditSentence: () -> Unit,
) {
    val ask = state.currentAsk ?: return

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // With a single question the count and the bar say nothing the question does not.
        if (state.missing.size > 1) {
            Text(
                text = "Pergunta ${state.askIndex + 1} de ${state.missing.size}".uppercase(),
                style = PocketTheme.typography.label,
                color = PocketTheme.colors.text3,
            )
            Spacer(Modifier.height(8.dp))
            AskProgressBar(total = state.missing.size, currentIndex = state.askIndex)
            Spacer(Modifier.height(20.dp))
        }

        ReadBackChips(state)

        Spacer(Modifier.height(16.dp))
        AskQuestion(
            ask = ask,
            state = state,
            onAmount = onAmount,
            onName = onName,
            onCard = onCard,
            onSkipCard = onSkipCard,
            onType = onType,
        )
        Spacer(Modifier.height(16.dp))
    }

    QuickAddFooter(
        secondaryText = "Editar frase",
        onSecondary = onEditSentence,
        primaryText = "Confirmar".takeIf { ask.needsConfirm() },
        onPrimary = onConfirm,
        primaryEnabled = state.canConfirmAsk,
    )
}

/** An option question commits on the tap; only the two free-input ones need a primary. */
private fun MissingField.needsConfirm(): Boolean =
    this == MissingField.AMOUNT || this == MissingField.DESCRIPTION

@Composable
private fun AskQuestion(
    ask: MissingField,
    state: QuickAddUiState,
    onAmount: (BigDecimal?) -> Unit,
    onName: (String) -> Unit,
    onCard: (String) -> Unit,
    onSkipCard: () -> Unit,
    onType: (TransactionType) -> Unit,
) {
    when (ask) {
        MissingField.AMOUNT -> {
            QuestionTitle("Qual foi o valor?")
            Spacer(Modifier.height(12.dp))
            AmountBox(state, onAmount)
        }

        MissingField.DESCRIPTION -> {
            QuestionTitle("O que foi?")
            Spacer(Modifier.height(12.dp))
            FormTextField(
                value = state.draft.name.orEmpty(),
                onValueChange = onName,
                placeholder = "Ex.: Supermercado",
                capitalization = KeyboardCapitalization.Sentences,
            )
        }

        MissingField.CARD -> {
            QuestionTitle("Em qual cartão?")
            Spacer(Modifier.height(12.dp))
            CardCandidateOptions(state, onCard, onSkipCard)
        }

        // The wizard owns StepType's own wording; here the handoff asks it differently.
        MissingField.TYPE -> StepType(
            suggestedType = null,
            selectedType = state.draft.type,
            onSelect = onType,
            question = { QuestionTitle("É uma despesa ou uma receita?") },
        )
    }
}

@Composable
private fun QuestionTitle(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = PocketTheme.colors.accent,
        )
        Text(
            text = text,
            style = PocketTheme.typography.stepQuestion.copy(fontSize = 17.sp),
            color = PocketTheme.colors.text,
        )
    }
}

@Composable
private fun AmountBox(state: QuickAddUiState, onAmount: (BigDecimal?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, PocketTheme.colors.accent, PocketTheme.shapes.field)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.field)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoneyTextField(
            amount = state.draft.amount,
            onAmountChange = onAmount,
            textStyle = PocketTheme.typography.monoTotal,
            autoFocus = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardCandidateOptions(
    state: QuickAddUiState,
    onCard: (String) -> Unit,
    onSkipCard: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.intent?.cardCandidates.orEmpty().forEach { candidate ->
            QuickAddOption(label = candidate.name, onClick = { onCard(candidate.id) })
        }
        QuickAddOption(label = "Sem cartão específico", ghost = true, onClick = onSkipCard)
    }
}

/** `.qa-opt`: a stacked full-width button. The ghost's 46px handoff height is raised to the 48dp minimum. */
@Composable
private fun QuickAddOption(label: String, onClick: () -> Unit, ghost: Boolean = false) {
    val colors = PocketTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp.takeIf { ghost } ?: 52.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .then(
                run {
                    if (ghost) return@run Modifier.dashedBorder(colors.line, 15.dp)
                    Modifier.border(1.5.dp, colors.line, PocketTheme.shapes.field)
                },
            )
            .background(colors.surface, PocketTheme.shapes.field)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.button.takeUnless { ghost }
                ?: PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = colors.text.takeUnless { ghost } ?: colors.text3,
        )
    }
}

/** The read-back, one merged node: nine separately focusable chips are nine swipes before the question. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadBackChips(state: QuickAddUiState) {
    val draft = state.draft
    val typeLabel = draft.type?.let(::typeWord)
    val amountLabel = draft.amount?.let { formatBrl(it) }
    val dateLabel = draft.date?.let { formatLedgerDate(it) }
    val chips = listOfNotNull(amountLabel, typeLabel, draft.name, dateLabel)
    if (chips.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Entendi: ${chips.joinToString(", ")}."
            },
    ) {
        Text(
            text = "Entendi".uppercase(),
            style = PocketTheme.typography.label,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (amountLabel != null) {
                PocketChip(label = amountLabel, variant = amountChipVariant(draft.type))
            }
            if (typeLabel != null) PocketChip(label = typeLabel)
            draft.name?.let { PocketChip(label = it) }
            if (dateLabel != null) {
                PocketChip(
                    label = dateLabel,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = PocketTheme.colors.text3,
                        )
                    },
                )
            }
            draft.paymentMethod?.let { method ->
                PocketChip(
                    label = method.label(),
                    leadingIcon = {
                        Icon(
                            imageVector = method.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = PocketTheme.colors.text3,
                        )
                    },
                )
            }
            draft.cardId?.let { id -> state.cardName(id)?.let { PocketChip(label = it) } }
        }
    }
}

/** Only the amount chip is tinted, and only once the type is known — an untyped tint would lie. */
private fun amountChipVariant(type: TransactionType?): PocketChipVariant {
    if (type == TransactionType.INCOME) return PocketChipVariant.INCOME
    if (type == TransactionType.EXPENSE) return PocketChipVariant.EXPENSE
    return PocketChipVariant.DEFAULT
}

/**
 * Shared stage footer: a secondary always, a primary only when the stage writes or advances. Equal
 * halves, and one per line once the font scale stops two labels fitting side by side.
 */
@Composable
internal fun QuickAddFooter(
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String?,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    primaryLeading: (@Composable () -> Unit)? = null,
) {
    val secondary: @Composable (Modifier) -> Unit = { modifier ->
        PocketButton(
            text = secondaryText,
            onClick = onSecondary,
            variant = PocketButtonVariant.SOFT,
            modifier = modifier,
        )
    }
    val primary: @Composable (Modifier) -> Unit = { modifier ->
        primaryText?.let { text ->
            PocketButton(
                text = text,
                onClick = onPrimary,
                enabled = primaryEnabled,
                leading = primaryLeading,
                modifier = modifier,
            )
        }
    }

    if (LocalDensity.current.fontScale > SIDE_BY_SIDE_MAX_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            primary(Modifier.fillMaxWidth())
            secondary(Modifier.fillMaxWidth())
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        secondary(Modifier.weight(1f))
        primary(Modifier.weight(1f))
    }
}
