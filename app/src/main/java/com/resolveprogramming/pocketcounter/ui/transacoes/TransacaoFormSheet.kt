package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketDateField
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.SegmentTone
import com.resolveprogramming.pocketcounter.ui.components.TagPicker
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Manual add / edit form, rendered as a single-page sheet matching screenshot 05. Reuses
 * [WizardDraft] for state plus its step validators, but lays out the fields as compact rows
 * (not the wizard's full-screen steps). The draft is local UI state; the VM only supplies
 * lookups and the save path.
 */
@Composable
fun TransacaoFormSheet(
    mode: FormMode,
    initialItem: HistoryItem?,
    initialType: TransactionType?,
    cards: List<CreditCard>,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onSave: (WizardDraft) -> Unit,
    onDismiss: () -> Unit,
    defaultDate: LocalDate? = null,
) {
    var draft by remember { mutableStateOf(seedDraft(initialItem, initialType, defaultDate)) }

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Editar transação".takeIf { mode is FormMode.Edit } ?: "Nova transação",
                    style = PocketTheme.typography.stepQuestion,
                    color = PocketTheme.colors.text,
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fechar",
                        modifier = Modifier.size(18.dp),
                        tint = PocketTheme.colors.text2,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                PocketSegmented(
                    options = listOf(
                        SegmentOption("Despesa", SegmentTone.EXPENSE),
                        SegmentOption("Receita", SegmentTone.INCOME),
                    ),
                    selectedIndex = when (draft.type) {
                        TransactionType.EXPENSE -> 0
                        TransactionType.INCOME -> 1
                        null -> -1
                    },
                    onSelect = { index ->
                        val type = TransactionType.INCOME.takeIf { index == 1 } ?: TransactionType.EXPENSE
                        draft = draft.withType(type)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                FormLabel("Descrição")
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    value = draft.name.orEmpty(),
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = "Ex.: Salário, Cliente X…".takeIf { draft.type == TransactionType.INCOME }
                        ?: "Ex.: iFood, Aluguel…",
                )

                Spacer(Modifier.height(16.dp))

                FormLabel("Valor")
                Spacer(Modifier.height(8.dp))
                MoneyField(
                    amount = draft.amount,
                    type = draft.type,
                    onAmountChange = { draft = draft.copy(amount = it) },
                )

                Spacer(Modifier.height(16.dp))

                LabelWithHint(label = "Pagamento", hint = "opcional")
                Spacer(Modifier.height(8.dp))
                val methods = PaymentMethod.entries.filterNot {
                    it == PaymentMethod.CREDIT && draft.type == TransactionType.INCOME
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    methods.forEach { method ->
                        PayChip(
                            method = method,
                            isSelected = draft.paymentMethod == method,
                            onClick = {
                                draft = draft.withPaymentMethod(method.takeIf { draft.paymentMethod != method })
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (draft.paymentMethod == PaymentMethod.CREDIT) {
                    Spacer(Modifier.height(10.dp))
                    CardPicker(
                        cards = cards,
                        selectedCardId = draft.cardId,
                        onSelect = { draft = draft.copy(cardId = it) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Data")
                        Spacer(Modifier.height(8.dp))
                        PocketDateField(
                            date = draft.date,
                            onDateChange = { picked ->
                                draft = draft.copy(
                                    date = picked,
                                    recurrenceDay = picked.dayOfMonth.takeIf { draft.isFixo }
                                        ?: draft.recurrenceDay,
                                )
                            },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Status")
                        Spacer(Modifier.height(8.dp))
                        PocketSegmented(
                            options = listOf(
                                SegmentOption("Efetuada", SegmentTone.INCOME),
                                SegmentOption("Pendente", SegmentTone.WARN),
                            ),
                            selectedIndex = 0.takeIf { draft.statusPayment == PaymentStatus.PAID } ?: 1,
                            onSelect = { index ->
                                val status = PaymentStatus.PAID.takeIf { index == 0 } ?: PaymentStatus.PENDING
                                draft = draft.copy(statusPayment = status)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Repete todo mês",
                            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = PocketTheme.colors.text,
                        )
                        Text(
                            text = "Desligado = lançamento avulso",
                            style = PocketTheme.typography.bodyXs,
                            color = PocketTheme.colors.text3,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = draft.isFixo,
                        onCheckedChange = { on ->
                            draft = draft.copy(
                                isFixo = on,
                                recurrenceDay = (draft.date ?: LocalDate.now()).dayOfMonth.takeIf { on },
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = PocketTheme.colors.accent,
                            checkedThumbColor = PocketTheme.colors.accentInk,
                        ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                LabelWithHint(
                    label = "Categoria de renda".takeIf { draft.type == TransactionType.INCOME } ?: "Categoria",
                    hint = "opcional",
                )
                Spacer(Modifier.height(8.dp))
                TagPicker(
                    type = draft.type ?: TransactionType.EXPENSE,
                    tags = tags,
                    contexts = contexts,
                    selectedTagIds = draft.tagIds,
                    onToggleTag = { draft = draft.withTagToggled(it) },
                )

                Spacer(Modifier.height(16.dp))
            }

            val canSave = draft.isStep1Valid() && draft.isStep2Valid() && draft.isStep3Valid()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = PocketTheme.colors.line, shape = PocketTheme.shapes.card)
                    .padding(top = 12.dp),
            ) {
                PocketButton(
                    text = "Salvar transação",
                    onClick = { onSave(draft) },
                    enabled = canSave,
                    fillMaxWidth = true,
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = PocketTheme.colors.accentInk,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LabelWithHint(label: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormLabel(label)
        Spacer(Modifier.width(4.dp))
        Text(
            text = hint,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun MoneyField(
    amount: BigDecimal?,
    type: TransactionType?,
    onAmountChange: (BigDecimal?) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var textValue by remember(amount) {
        mutableStateOf(amount?.let { formatAmountInput(it) } ?: "")
    }
    val valueColor = when (type) {
        TransactionType.INCOME -> PocketTheme.colors.income
        TransactionType.EXPENSE -> PocketTheme.colors.expense
        null -> PocketTheme.colors.text
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(
                width = 1.dp,
                color = PocketTheme.colors.accent.takeIf { isFocused } ?: PocketTheme.colors.line2,
                shape = PocketTheme.shapes.labelPicker,
            )
            .background(PocketTheme.colors.surface, PocketTheme.shapes.labelPicker)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "R$",
            style = PocketTheme.typography.monoBody,
            color = PocketTheme.colors.text3,
        )
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            if (textValue.isEmpty()) {
                Text(
                    text = "0,00",
                    style = PocketTheme.typography.monoBalance,
                    color = PocketTheme.colors.text3,
                )
            }
            BasicTextField(
                value = textValue,
                onValueChange = { newValue ->
                    val cleaned = newValue.filter { it.isDigit() || it == ',' }
                    textValue = cleaned
                    onAmountChange(cleaned.replace(",", ".").toBigDecimalOrNull())
                },
                textStyle = PocketTheme.typography.monoBalance.copy(color = valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }
    }
}

@Composable
private fun PayChip(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line
    val bgColor = PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface
    val inkColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.text3

    Column(
        modifier = modifier
            .heightIn(min = 60.dp)
            .border(1.dp, borderColor, PocketTheme.shapes.chip)
            .background(bgColor, PocketTheme.shapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = method.icon(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = inkColor,
        )
        Text(
            text = method.label(),
            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
            color = inkColor,
        )
    }
}

@Composable
private fun CardPicker(
    cards: List<CreditCard>,
    selectedCardId: String?,
    onSelect: (String) -> Unit,
) {
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
                .padding(16.dp),
        ) {
            Text(
                text = "Nenhum cartão ainda",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text3,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            val isSelected = selectedCardId == card.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line,
                        shape = PocketTheme.shapes.card,
                    )
                    .background(
                        PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface,
                        PocketTheme.shapes.card,
                    )
                    .clickable(role = Role.Button, onClick = { onSelect(card.id) })
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.name,
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    Text(
                        text = "fecha dia ${card.billDay}",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PocketTheme.colors.accent,
                    )
                }
            }
        }
    }
}

private fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CREDIT -> Icons.Filled.CreditCard
    PaymentMethod.DEBIT -> Icons.Filled.AccountBalanceWallet
    PaymentMethod.PIX -> Icons.Filled.Bolt
    PaymentMethod.CASH -> Icons.Filled.Payments
    PaymentMethod.CRYPTO -> Icons.Filled.CurrencyBitcoin
}

private fun formatAmountInput(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString().replace(".", ",")

private fun seedDraft(
    item: HistoryItem?,
    initialType: TransactionType?,
    defaultDate: LocalDate?,
): WizardDraft {
    if (item == null) return WizardDraft(type = initialType, date = defaultDate ?: LocalDate.now())
    return WizardDraft(
        type = item.type,
        amount = item.amount.abs(),
        date = item.date,
        statusPayment = item.statusPayment,
        paymentMethod = item.paymentMethod,
        cardId = item.cardId,
        isFixo = item.isFixo,
        recurrenceDay = item.date.dayOfMonth.takeIf { item.isFixo },
        // Preserve series membership across an edit — toDto() sends idSeries, and the backend
        // copies it verbatim, so dropping it here would silently unlink a fixo row.
        seriesId = item.seriesId,
        // Inheriting (null) rows seed empty here; the dedicated tag sheet handles inherit/override.
        tagIds = item.tagIds.orEmpty(),
        // Fall back to description so a legacy row whose title lives in `description` keeps its
        // title when edited — toDto() only sends `name`, so seeding it from displayTitle's source
        // avoids wiping the visible title on save.
        name = item.name ?: item.description,
        displayOrder = item.displayOrder,
    )
}
