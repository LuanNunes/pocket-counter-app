package com.resolveprogramming.pocketcounter.ui.transacoes

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.WizardStep
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepAmount
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepPayment
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepType
import java.time.LocalDate

/**
 * Manual add / edit form. Reuses the wizard step composables + [WizardDraft] + the same
 * footer/validation pattern, but NOT WizardViewModel (which is notification-coupled).
 * Draft + step index are local UI state; the VM only supplies the card list and save path.
 */
@Composable
fun TransacaoFormSheet(
    mode: FormMode,
    initialItem: HistoryItem?,
    cards: List<CreditCard>,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onSave: (WizardDraft) -> Unit,
    onDismiss: () -> Unit,
    defaultDate: LocalDate? = null,
) {
    var draft by remember { mutableStateOf(seedDraft(initialItem, defaultDate)) }
    var step by remember { mutableStateOf(WizardStep.TYPE) }
    var tagSearch by remember { mutableStateOf("") }
    val context = LocalContext.current

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Editar transação".takeIf { mode is FormMode.Edit } ?: "Nova transação",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Text(
            text = "${step.label} · ${step.subtitle}",
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(16.dp))

        when (step) {
            WizardStep.TYPE -> StepType(
                suggestedType = null,
                selectedType = draft.type,
                onSelect = { draft = draft.withType(it) },
            )

            WizardStep.AMOUNT -> StepAmount(
                amount = draft.amount,
                date = draft.date,
                statusPayment = draft.statusPayment,
                hasInstallments = false,
                installmentsEnabled = false,
                installmentCount = null,
                installmentValue = null,
                onAmountChange = { draft = draft.copy(amount = it) },
                onDateTap = {
                    val current = draft.date ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            draft = draft.copy(date = LocalDate.of(year, month + 1, day))
                        },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth,
                    ).show()
                },
                onStatusChange = { draft = draft.copy(statusPayment = it) },
                onToggleInstallments = { },
                isFixo = draft.isFixo,
                recurrenceDay = draft.recurrenceDay,
                onToggleFixo = { draft = draft.copy(isFixo = it) },
                onRecurrenceDayChange = { draft = draft.copy(recurrenceDay = it) },
            )

            WizardStep.PAYMENT -> StepPayment(
                type = draft.type,
                cards = cards,
                selectedMethod = draft.paymentMethod,
                selectedCardId = draft.cardId,
                onSelectMethod = { draft = draft.withPaymentMethod(it) },
                onSelectCard = { draft = draft.copy(cardId = it) },
            )

            WizardStep.TAGS -> StepTags(
                type = draft.type ?: TransactionType.EXPENSE,
                tags = tags,
                contexts = contexts,
                selectedTagIds = draft.tagIds,
                searchQuery = tagSearch,
                learnRule = false,
                paymentHint = null,
                merchant = draft.merchant,
                onSearchChange = { tagSearch = it },
                onToggleTag = { draft = draft.withTagToggled(it) },
                onToggleLearnRule = { },
                showLearnToggle = false,
            )
        }

        Spacer(Modifier.height(20.dp))

        val canAdvance = when (step) {
            WizardStep.TYPE -> draft.isStep1Valid()
            WizardStep.AMOUNT -> draft.isStep2Valid()
            WizardStep.PAYMENT -> draft.isStep3Valid()
            WizardStep.TAGS -> true
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PocketButton(
                text = "Cancelar".takeIf { step == WizardStep.TYPE } ?: "Voltar",
                onClick = {
                    if (step == WizardStep.TYPE) onDismiss()
                    if (step != WizardStep.TYPE) step = WizardStep.entries[step.index - 1]
                },
                variant = PocketButtonVariant.SOFT,
                fillMaxWidth = true,
                modifier = Modifier.weight(1f),
            )
            PocketButton(
                text = "Salvar transação".takeIf { step == WizardStep.TAGS } ?: "Continuar",
                onClick = {
                    if (step == WizardStep.TAGS) onSave(draft)
                    if (step != WizardStep.TAGS) step = WizardStep.entries[step.index + 1]
                },
                enabled = canAdvance,
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

private fun seedDraft(item: HistoryItem?, defaultDate: LocalDate?): WizardDraft {
    if (item == null) return WizardDraft(date = defaultDate ?: LocalDate.now())
    return WizardDraft(
        type = item.type,
        amount = item.amount.abs(),
        date = item.date,
        statusPayment = item.statusPayment,
        paymentMethod = item.paymentMethod,
        cardId = item.cardId,
        // Preserve series membership across an edit — toDto() sends idSeries, and the backend
        // copies it verbatim, so dropping it here would silently unlink a fixo row.
        seriesId = item.seriesId,
        // Inheriting (null) rows seed empty here; the dedicated tag sheet handles inherit/override.
        tagIds = item.tagIds.orEmpty(),
        displayOrder = item.displayOrder,
    )
}
