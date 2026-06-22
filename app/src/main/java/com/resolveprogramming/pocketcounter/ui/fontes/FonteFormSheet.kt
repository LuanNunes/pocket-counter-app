package com.resolveprogramming.pocketcounter.ui.fontes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.data.repository.SourceInput
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormSwitchRow
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags
import java.math.BigDecimal

private val tipoSegments = listOf(SegmentOption("Despesa"), SegmentOption("Receita"), SegmentOption("Ambos"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FonteFormSheet(
    mode: FonteFormMode,
    editing: Source?,
    preselectMeioId: String?,
    paymentSources: List<PaymentSource>,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onSave: (SourceInput) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var meioId by remember { mutableStateOf(editing?.idPaymentSource ?: preselectMeioId) }
    var tipoIndex by remember {
        mutableIntStateOf(
            when {
                editing == null -> 0
                editing.allowsIncome && editing.allowsExpense -> 2
                editing.allowsIncome -> 1
                else -> 0
            },
        )
    }
    var recurring by remember { mutableStateOf(editing?.isRecurring ?: false) }
    var day by remember { mutableStateOf(editing?.refDayRecurring?.toString().orEmpty()) }
    var amount by remember { mutableStateOf(editing?.amount?.let { fmt(it) }.orEmpty()) }
    var tagIds by remember { mutableStateOf(editing?.tags ?: emptyList()) }
    var tagSearch by remember { mutableStateOf("") }

    val allowsExpense = tipoIndex == 0 || tipoIndex == 2
    val allowsIncome = tipoIndex == 1 || tipoIndex == 2
    // A recurring template needs a valid day, else the backend stores null and the "recorrente"
    // intent is silently lost on reload (Source.isRecurring is derived from refDayRecurring).
    val canSave = name.isNotBlank() && meioId != null && (!recurring || day.toIntOrNull() != null)

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = if (mode is FonteFormMode.Edit) "Editar fonte" else "Nova fonte",
                style = PocketTheme.typography.stepQuestion,
                color = PocketTheme.colors.text,
            )
            Spacer(Modifier.height(16.dp))

            FormLabel("Nome")
            Spacer(Modifier.height(8.dp))
            FormTextField(value = name, onValueChange = { name = it }, placeholder = "Ex: Mercado, Salário")
            Spacer(Modifier.height(16.dp))

            FormLabel("Meio")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                paymentSources.forEach { ps ->
                    PocketChip(
                        label = ps.name,
                        variant = if (ps.id == meioId) PocketChipVariant.ON else PocketChipVariant.DEFAULT,
                        onClick = { meioId = ps.id },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            FormLabel("Tipo permitido")
            Spacer(Modifier.height(8.dp))
            PocketSegmented(options = tipoSegments, selectedIndex = tipoIndex, onSelect = { tipoIndex = it })
            Spacer(Modifier.height(8.dp))

            FormSwitchRow("Recorrente", recurring, { recurring = it })
            if (recurring) {
                FormLabel("Dia do mês")
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    value = day,
                    onValueChange = { v -> day = v.filter { it.isDigit() }.take(2) },
                    placeholder = "Ex: 5",
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(12.dp))
                FormLabel("Valor padrão")
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' } },
                    placeholder = "0,00",
                    keyboardType = KeyboardType.Decimal,
                )
            }
            Spacer(Modifier.height(16.dp))

            // Tags padrão — reuse the wizard tag picker without the "learn from notifications" card.
            // Income-only sources pick income categories; otherwise (Despesa/Ambos) expense tags.
            StepTags(
                type = if (tipoIndex == 1) TransactionType.INCOME else TransactionType.EXPENSE,
                tags = tags,
                contexts = contexts,
                selectedTagIds = tagIds,
                searchQuery = tagSearch,
                learnRule = false,
                paymentHint = null,
                merchant = null,
                onSearchChange = { tagSearch = it },
                onToggleTag = { id -> tagIds = if (id in tagIds) tagIds - id else tagIds + id },
                onToggleLearnRule = { },
                showLearnToggle = false,
            )

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PocketButton(
                    text = "Cancelar",
                    onClick = onDismiss,
                    variant = PocketButtonVariant.SOFT,
                    fillMaxWidth = true,
                    modifier = Modifier.weight(1f),
                )
                PocketButton(
                    text = "Salvar",
                    onClick = {
                        onSave(
                            SourceInput(
                                name = name.trim(),
                                idPaymentSource = meioId!!,
                                allowsIncome = allowsIncome,
                                allowsExpense = allowsExpense,
                                refDayRecurring = if (recurring) day.toIntOrNull()?.coerceIn(1, 31) else null,
                                amount = if (recurring) parseAmount(amount) else BigDecimal.ZERO,
                                tagIds = tagIds,
                            ),
                        )
                    },
                    enabled = canSave,
                    fillMaxWidth = true,
                    modifier = Modifier.weight(1.5f),
                )
            }
            if (mode is FonteFormMode.Edit) {
                Spacer(Modifier.height(8.dp))
                PocketButton(
                    text = "Excluir fonte",
                    onClick = onDelete,
                    variant = PocketButtonVariant.GHOST,
                    fillMaxWidth = true,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun fmt(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace(".", ",")

private fun parseAmount(text: String): BigDecimal =
    text.replace(".", "").replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
