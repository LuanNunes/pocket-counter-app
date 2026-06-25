package com.resolveprogramming.pocketcounter.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Shared two-step tag selector. Pure logic lives in [TagPickerLogic]; this is the UI shell.
 *
 * - Selected pills (with ×) summarize the current selection at the top.
 * - A transversal search field filters the whole universe; a non-blank query short-circuits the
 *   category drill-down and shows flat matches.
 * - Expense (and any kind with contexts) uses step 1 = categories, step 2 = drill into a context.
 *   Income is flat (no step 1) per the spec.
 *
 * State held locally: the search [query] and the open context id [openCtx].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    type: TransactionType,
    tags: List<Tag>,
    contexts: List<TagContext>,
    selectedTagIds: List<String>,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var openCtx by remember { mutableStateOf<String?>(null) }

    val universe = tagUniverse(tags, type)
    val selectedSet = selectedTagIds.toSet()
    val matches = searchMatches(universe, query)
    val isSearching = query.isNotBlank()

    Column(modifier = modifier) {
        SelectedTagPills(
            selectedTagIds = selectedTagIds,
            tags = tags,
            contexts = contexts,
            onRemove = onToggleTag,
        )

        Spacer(Modifier.height(12.dp))

        BasicTextField(
            value = query,
            onValueChange = { query = it },
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
                    if (query.isEmpty()) {
                        Text(
                            text = "Buscar tag…",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.text3,
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        if (isSearching) {
            SearchResults(
                matches = matches,
                contexts = contexts,
                selectedSet = selectedSet,
                onToggleTag = onToggleTag,
            )
        }
        if (!isSearching && type == TransactionType.INCOME) {
            TagChipFlow(
                tags = universe,
                contexts = contexts,
                selectedSet = selectedSet,
                onToggleTag = onToggleTag,
            )
        }
        if (!isSearching && type != TransactionType.INCOME) {
            ExpenseDrill(
                universe = universe,
                contexts = contexts,
                selectedSet = selectedSet,
                openCtx = openCtx,
                onOpenCtx = { openCtx = it },
                onBack = { openCtx = null },
                onToggleTag = onToggleTag,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResults(
    matches: List<Tag>,
    contexts: List<TagContext>,
    selectedSet: Set<String>,
    onToggleTag: (String) -> Unit,
) {
    if (matches.isEmpty()) {
        Text(
            text = "Nada encontrado. Crie tags em Mais › Contextos & Tags.",
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        return
    }
    TagChipFlow(
        tags = matches,
        contexts = contexts,
        selectedSet = selectedSet,
        onToggleTag = onToggleTag,
    )
}

@Composable
private fun ExpenseDrill(
    universe: List<Tag>,
    contexts: List<TagContext>,
    selectedSet: Set<String>,
    openCtx: String?,
    onOpenCtx: (String) -> Unit,
    onBack: () -> Unit,
    onToggleTag: (String) -> Unit,
) {
    if (openCtx == null) {
        val categories = categoriesFor(universe, contexts, selectedSet)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                CategoryRow(category = category, onClick = { onOpenCtx(category.id) })
            }
        }
        return
    }

    val title = categoriesFor(universe, contexts, selectedSet)
        .firstOrNull { it.id == openCtx }?.name
        ?: "Categoria"
    val drillColor = contexts.firstOrNull { it.id == openCtx }?.color

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "‹ $title",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text2,
            )
        }
        Spacer(Modifier.height(12.dp))
        TagChipFlow(
            tags = drillTags(universe, openCtx, contexts),
            contexts = contexts,
            selectedSet = selectedSet,
            onToggleTag = onToggleTag,
            overrideColor = drillColor,
        )
    }
}

@Composable
private fun CategoryRow(
    category: TagPickerCategory,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    category.color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = category.name,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = PocketTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        if (category.selectedCount > 0) {
            Box(
                modifier = Modifier
                    .background(PocketTheme.colors.accent, PocketTheme.shapes.pill)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${category.selectedCount}",
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                    color = PocketTheme.colors.accentInk,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "${category.tagCount}",
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipFlow(
    tags: List<Tag>,
    contexts: List<TagContext>,
    selectedSet: Set<String>,
    onToggleTag: (String) -> Unit,
    overrideColor: Long? = null,
) {
    val contextMap = contexts.associateBy { it.id }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        tags.forEach { tag ->
            val isSelected = tag.id in selectedSet
            val dotArgb = overrideColor
                ?: tag.idContext?.let { contextMap[it]?.color }
                ?: tag.color
            TagOptionChip(
                name = tag.name,
                dotColor = dotArgb?.let { Color(it) },
                selected = isSelected,
                onClick = { onToggleTag(tag.id) },
            )
        }
    }
}

@Composable
fun TagOptionChip(
    name: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.surface
    val textColor = PocketTheme.colors.accentInk.takeIf { selected } ?: PocketTheme.colors.text2
    val borderColor = PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.line

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(1.dp, borderColor, PocketTheme.shapes.chip)
            .background(bg, PocketTheme.shapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = name,
            style = PocketTheme.typography.bodySm,
            color = textColor,
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "✓",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.accentInk,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectedTagPills(
    selectedTagIds: List<String>,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedTagIds.isEmpty()) return
    val contextMap = contexts.associateBy { it.id }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.accentBg, PocketTheme.shapes.chip)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedTagIds.forEach { tagId ->
            val tag = tags.firstOrNull { it.id == tagId } ?: return@forEach
            val dotArgb = tag.idContext?.let { contextMap[it]?.color } ?: tag.color
            val dotColor = dotArgb?.let { Color(it) } ?: PocketTheme.colors.text3
            Row(
                modifier = Modifier
                    .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
                    .clickable { onRemove(tagId) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(dotColor, CircleShape))
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
