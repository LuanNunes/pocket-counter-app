package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepTags(
    type: TransactionType,
    tags: List<Tag>,
    contexts: List<TagContext>,
    selectedTagIds: List<String>,
    searchQuery: String,
    learnRule: Boolean,
    paymentHint: String?,
    merchant: String?,
    onSearchChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleLearnRule: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Hidden for manual entry (Gestão), where "learn from notifications" is meaningless.
    showLearnToggle: Boolean = true,
) {
    val contextMap = contexts.associateBy { it.id }
    val kindTags = tags.filter { it.kind == type }
    val filteredTags = if (searchQuery.isBlank()) kindTags
    else kindTags.filter { it.name.contains(searchQuery, ignoreCase = true) }
    val tagsByContext = filteredTags.groupBy { it.idContext }
    // Expense tags whose context is null/blank or points at an unknown/deleted context
    // must still be selectable — surface them in a trailing "Sem contexto" group,
    // mirroring the Gestão screen's buildContextSections orphan bucket.
    val knownContextIds = contexts.map { it.id }.toSet()
    val orphanExpenseTags = if (type == TransactionType.INCOME) emptyList()
        else filteredTags.filter { it.idContext.isNullOrBlank() || it.idContext !in knownContextIds }

    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append("Quer adicionar ")
                withStyle(SpanStyle(color = PocketTheme.colors.accent, fontWeight = FontWeight.Bold)) {
                    append("tags")
                }
                append("?")
            },
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = buildAnnotatedString {
                append("Tags ajudam você a agrupar transações nas análises. Esse passo é ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("opcional") }
                append(" — pode pular.")
            },
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text2,
        )

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
                            text = "Buscar ou criar tag…",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.text3,
                        )
                    }
                    inner()
                }
            },
        )

        if (selectedTagIds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.chip)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedTagIds.forEach { tagId ->
                    val tag = tags.find { it.id == tagId } ?: return@forEach
                    val context = tag.idContext?.let { contextMap[it] }
                    val dotColor = context?.color
                        ?: tag.color
                    val contextColor = dotColor?.let { Color(it) } ?: PocketTheme.colors.text3

                    Row(
                        modifier = Modifier
                            .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
                            .clickable { onToggleTag(tagId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(contextColor, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.accentInk,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "×",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.accentInk.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (type == TransactionType.INCOME) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                filteredTags.forEach { tag ->
                    val isSelected = tag.id in selectedTagIds
                    val bg = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.surface
                    val textColor = if (isSelected) PocketTheme.colors.accentInk else PocketTheme.colors.text2
                    val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line
                    val dotColor = tag.color?.let { Color(it) } ?: PocketTheme.colors.text3

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, borderColor, PocketTheme.shapes.chip)
                            .background(bg, PocketTheme.shapes.chip)
                            .clickable { onToggleTag(tag.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = textColor,
                        )
                    }
                }
            }
        } else {
            contexts.forEach { context ->
                val contextTags = tagsByContext[context.id] ?: return@forEach

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(context.color), CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = context.name.uppercase(),
                        style = PocketTheme.typography.sectionHeader,
                        color = PocketTheme.colors.text3,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    contextTags.forEach { tag ->
                        val isSelected = tag.id in selectedTagIds
                        val bg = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.surface
                        val textColor = if (isSelected) PocketTheme.colors.accentInk else PocketTheme.colors.text2
                        val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line

                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = textColor,
                            modifier = Modifier
                                .border(1.dp, borderColor, PocketTheme.shapes.chip)
                                .background(bg, PocketTheme.shapes.chip)
                                .clickable { onToggleTag(tag.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            if (orphanExpenseTags.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(PocketTheme.colors.text3, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "SEM CONTEXTO",
                        style = PocketTheme.typography.sectionHeader,
                        color = PocketTheme.colors.text3,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    orphanExpenseTags.forEach { tag ->
                        val isSelected = tag.id in selectedTagIds
                        val bg = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.surface
                        val textColor = if (isSelected) PocketTheme.colors.accentInk else PocketTheme.colors.text2
                        val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line

                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = textColor,
                            modifier = Modifier
                                .border(1.dp, borderColor, PocketTheme.shapes.chip)
                                .background(bg, PocketTheme.shapes.chip)
                                .clickable { onToggleTag(tag.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        if (showLearnToggle) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aprender este padrão",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(4.dp))
                val hint = paymentHint ?: merchant ?: "..."
                Text(
                    text = "Próximas notificações com \"$hint\" vão pré-preencher Source + tags automaticamente.",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = learnRule,
                onCheckedChange = onToggleLearnRule,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PocketTheme.colors.accent,
                    checkedThumbColor = PocketTheme.colors.accentInk,
                ),
            )
        }
        }
    }
}
