package com.resolveprogramming.pocketcounter.ui.meios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceInput
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormSwitchRow
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun MeioFormSheet(
    mode: MeioFormMode,
    editing: PaymentSource?,
    onSave: (PaymentSourceInput) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var typeIndex by remember { mutableIntStateOf(editing?.type?.let { PaymentTypeUi.indexOf(it) } ?: 0) }
    var allowsIncome by remember { mutableStateOf(editing?.allowsIncome ?: false) }
    var allowsExpense by remember { mutableStateOf(editing?.allowsExpense ?: true) }
    var billDay by remember { mutableStateOf(editing?.refDayBill?.toString().orEmpty()) }

    val type = PaymentTypeUi.typeAt(typeIndex)
    val isCredit = PaymentTypeUi.isCredit(type)
    // A credit card needs a closing day so BillingCycle can derive the fatura window.
    val canSave = name.isNotBlank() && (allowsIncome || allowsExpense) &&
        (!isCredit || billDay.toIntOrNull() != null)

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = if (mode is MeioFormMode.Edit) "Editar meio" else "Novo meio",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(16.dp))

        FormLabel("Nome")
        Spacer(Modifier.height(8.dp))
        FormTextField(value = name, onValueChange = { name = it }, placeholder = "Ex: Nubank, Carteira")
        Spacer(Modifier.height(16.dp))

        FormLabel("Tipo")
        Spacer(Modifier.height(8.dp))
        PocketSegmented(
            options = PaymentTypeUi.segments,
            selectedIndex = typeIndex,
            onSelect = { typeIndex = it },
        )
        Spacer(Modifier.height(8.dp))

        FormSwitchRow("Permite despesas", allowsExpense, { allowsExpense = it })
        FormSwitchRow("Permite receitas", allowsIncome, { allowsIncome = it })

        if (isCredit) {
            Spacer(Modifier.height(8.dp))
            FormLabel("Fecha dia")
            Spacer(Modifier.height(8.dp))
            FormTextField(
                value = billDay,
                onValueChange = { v -> billDay = v.filter { it.isDigit() }.take(2) },
                placeholder = "Ex: 8",
                keyboardType = KeyboardType.Number,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        PaymentSourceInput(
                            name = name.trim(),
                            type = type,
                            allowsIncome = allowsIncome,
                            allowsExpense = allowsExpense,
                            refDayBill = if (isCredit) billDay.toIntOrNull()?.coerceIn(1, 31) else null,
                        ),
                    )
                },
                enabled = canSave,
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        if (mode is MeioFormMode.Edit) {
            Spacer(Modifier.height(8.dp))
            PocketButton(
                text = "Excluir meio",
                onClick = onDelete,
                variant = PocketButtonVariant.GHOST,
                fillMaxWidth = true,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
