package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun StepType(
    suggestedType: TransactionType?,
    selectedType: TransactionType?,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val questionText = if (suggestedType != null) {
            val typeName = if (suggestedType == TransactionType.EXPENSE) "despesa" else "receita"
            buildAnnotatedString {
                append("O texto parece indicar uma\n")
                withStyle(SpanStyle(color = PocketTheme.colors.accent, fontWeight = FontWeight.Bold)) {
                    append(typeName)
                }
                append(". Confirma?")
            }
        } else {
            buildAnnotatedString {
                append("Não consegui identificar o tipo. ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Você me diz?")
                }
            }
        }

        Text(
            text = questionText,
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeCard(
                label = "Despesa",
                helper = "algo que saiu da conta",
                icon = "↑",
                isSelected = selectedType == TransactionType.EXPENSE,
                onClick = { onSelect(TransactionType.EXPENSE) },
                modifier = Modifier.weight(1f),
            )
            TypeCard(
                label = "Receita",
                helper = "algo que entrou na conta",
                icon = "↓",
                isSelected = selectedType == TransactionType.INCOME,
                onClick = { onSelect(TransactionType.INCOME) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TypeCard(
    label: String,
    helper: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line
    val bgColor = if (isSelected) PocketTheme.colors.accentBg else PocketTheme.colors.surface

    Column(
        modifier = modifier
            .border(1.5.dp, borderColor, PocketTheme.shapes.card)
            .background(bgColor, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = icon,
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = helper,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
    }
}
