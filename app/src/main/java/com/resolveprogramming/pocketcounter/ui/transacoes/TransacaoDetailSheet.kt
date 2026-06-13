package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TransacaoDetailSheet(
    item: HistoryItem,
    sourceName: String,
    paymentName: String,
    tagNames: List<String>,
    onDismiss: () -> Unit,
    onMarkPaid: () -> Unit,
    onMarkPending: () -> Unit,
    onEdit: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    PocketBottomSheet(onDismissRequest = onDismiss) {
        AmountText(
            amount = item.amount,
            type = item.type,
            showSign = true,
            style = PocketTheme.typography.monoBalance,
        )
        Spacer(Modifier.height(16.dp))

        DetailRow("Tipo", if (item.type == TransactionType.INCOME) "Receita" else "Despesa")
        DetailDivider()

        // Status is a two-way toggle (Efetuada ⇄ Pendente).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Status", style = PocketTheme.typography.body, color = PocketTheme.colors.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusToggle(
                    label = "Efetuada",
                    selected = item.statusPayment == PaymentStatus.PAID,
                    dotColor = PocketTheme.colors.income,
                    onClick = onMarkPaid,
                )
                StatusToggle(
                    label = "Pendente",
                    selected = item.statusPayment == PaymentStatus.PENDING,
                    dotColor = PocketTheme.colors.warn,
                    onClick = onMarkPending,
                )
            }
        }
        DetailDivider()

        DetailRow("Data", item.date.format(dateFormat))
        DetailDivider()
        DetailRow("Fonte", sourceName)
        DetailDivider()
        DetailRow("Meio", paymentName)

        DetailDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tags", style = PocketTheme.typography.body, color = PocketTheme.colors.text2)
            Text(
                "Editar",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onEditTags),
            )
        }
        if (tagNames.isEmpty()) {
            Text("sem tags", style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text3)
        } else {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tagNames.forEach { PocketChip(label = it) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PocketButton(
                text = "Editar",
                onClick = onEdit,
                variant = PocketButtonVariant.SOFT,
                fillMaxWidth = true,
                modifier = Modifier.weight(1f),
            )
            PocketButton(
                text = "Excluir",
                onClick = onDelete,
                variant = PocketButtonVariant.SOFT,
                fillMaxWidth = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = PocketTheme.typography.body, color = PocketTheme.colors.text2)
        Text(
            value,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = PocketTheme.colors.text,
        )
    }
}

@Composable
private fun DetailDivider() {
    androidx.compose.material3.HorizontalDivider(color = PocketTheme.colors.line)
}

@Composable
private fun StatusToggle(
    label: String,
    selected: Boolean,
    dotColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) PocketTheme.colors.accentBg else PocketTheme.colors.surface
    val border = if (selected) PocketTheme.colors.accent else PocketTheme.colors.line
    Row(
        modifier = Modifier
            .border(1.dp, border, PocketTheme.shapes.pill)
            .background(bg, PocketTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, PocketTheme.shapes.pill),
        )
        Text(
            label,
            style = PocketTheme.typography.bodySm,
            color = if (selected) PocketTheme.colors.text else PocketTheme.colors.text2,
        )
    }
}
