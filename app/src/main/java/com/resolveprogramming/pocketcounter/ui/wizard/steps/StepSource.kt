package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StepSource(
    sources: List<Source>,
    selectedId: String?,
    suggestedId: String?,
    merchantRaw: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onCreateNew: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val filteredSources = if (searchQuery.isBlank()) sources
    else sources.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append("Em qual ")
                withStyle(SpanStyle(color = PocketTheme.colors.accent, fontWeight = FontWeight.Bold)) {
                    append("source")
                }
                append("?")
            },
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        if (merchantRaw != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append("Texto identificou ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(merchantRaw)
                    }
                    append(". Escolha uma existente ou crie uma nova.")
                },
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text2,
            )
        }

        Spacer(Modifier.height(16.dp))

        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            textStyle = PocketTheme.typography.body.copy(color = PocketTheme.colors.text),
            singleLine = true,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                        .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar source...",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.text3,
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        if (filteredSources.isEmpty() && sources.isEmpty()) {
            Text(
                text = "Nenhuma Source cadastrada para esse meio + tipo. Crie uma nova abaixo.",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        filteredSources.forEach { source ->
            SourceOptionCard(
                source = source,
                isSelected = selectedId == source.id,
                isSuggested = suggestedId == source.id,
                onClick = { onSelect(source.id) },
                formatter = formatter,
            )
            Spacer(Modifier.height(10.dp))
        }

        val newName = searchQuery.ifBlank { merchantRaw ?: "" }
        if (newName.isNotBlank()) {
            NewSourceCard(
                previewName = newName,
                onClick = { onCreateNew(newName) },
            )
        }
    }
}

@Composable
private fun SourceOptionCard(
    source: Source,
    isSelected: Boolean,
    isSuggested: Boolean,
    onClick: () -> Unit,
    formatter: NumberFormat,
) {
    val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line
    val bgColor = if (isSelected) PocketTheme.colors.accentBg else PocketTheme.colors.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, PocketTheme.shapes.card)
            .background(bgColor, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (source.isRecurring) "↻" else "◆",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            val subline = if (source.isRecurring) {
                val amountStr = source.amount?.let { " · ${formatter.format(it)}" } ?: ""
                "recorrente · dia ${source.refDayRecurring}$amountStr"
            } else {
                "ocasional"
            }
            Text(
                text = subline,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }

        if (isSuggested) {
            Text(
                text = "SUGESTÃO",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.accent,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(PocketTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.accentInk)
            }
        }
    }
}

@Composable
private fun NewSourceCard(
    previewName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Nova Source",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = "\"$previewName\"",
                style = PocketTheme.typography.monoSm,
                color = PocketTheme.colors.text3,
            )
        }
    }
}
