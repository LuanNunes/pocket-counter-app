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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 *
 * [onCreateTag] is optional: when supplied, each browse region ends with a create affordance that
 * reports the context to create under (null outside a real context) and the name to seed.
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
    onCreateTag: ((contextId: String?, name: String) -> Unit)? = null,
    /** False when the caller already offers a create affordance beside the picker. */
    showRootCreate: Boolean = true,
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
            cursorBrush = SolidColor(PocketTheme.colors.accent),
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
                query = query,
                onToggleTag = onToggleTag,
                onCreateTag = onCreateTag,
            )
        }
        if (!isSearching && type == TransactionType.INCOME) {
            IncomeTagFlow(
                universe = universe,
                contexts = contexts,
                selectedSet = selectedSet,
                onToggleTag = onToggleTag,
                onCreateTag = onCreateTag,
                showRootCreate = showRootCreate,
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
                onCreateTag = onCreateTag,
                showRootCreate = showRootCreate,
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
    query: String,
    onToggleTag: (String) -> Unit,
    onCreateTag: ((String?, String) -> Unit)?,
) {
    val typed = query.trim()
    val createChip = onCreateTag?.let { create ->
        @Composable {
            CreateChip(
                label = "+ Criar tag \"$typed\"",
                a11yLabel = "Criar tag chamada $typed",
                onClick = { create(null, typed) },
            )
        }
    }
    if (matches.isEmpty()) {
        EmptyBrowseState(
            lead = "Nada encontrado.",
            pointer = "Crie tags em Mais › Contextos & Tags.",
            createChip = createChip,
        )
        return
    }
    TagChipFlow(
        tags = matches,
        contexts = contexts,
        selectedSet = selectedSet,
        onToggleTag = onToggleTag,
        trailing = createChip,
    )
}

@Composable
private fun IncomeTagFlow(
    universe: List<Tag>,
    contexts: List<TagContext>,
    selectedSet: Set<String>,
    onToggleTag: (String) -> Unit,
    onCreateTag: ((String?, String) -> Unit)?,
    showRootCreate: Boolean,
) {
    val createChip = onCreateTag?.takeIf { showRootCreate }?.let { create ->
        @Composable {
            CreateChip(
                label = "+ Nova categoria",
                a11yLabel = "Criar nova categoria de renda",
                onClick = { create(null, "") },
            )
        }
    }
    if (universe.isEmpty()) {
        EmptyBrowseState(
            lead = "Nenhuma categoria de renda ainda.",
            pointer = "Crie em Mais › Contextos & Tags.",
            createChip = createChip,
        )
        return
    }
    TagChipFlow(
        tags = universe,
        contexts = contexts,
        selectedSet = selectedSet,
        onToggleTag = onToggleTag,
        trailing = createChip,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = PocketTheme.typography.bodySm,
        color = PocketTheme.colors.text3,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun EmptyBrowseState(lead: String, pointer: String, createChip: (@Composable () -> Unit)?) {
    if (createChip == null) {
        EmptyHint("$lead $pointer")
        return
    }
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = lead,
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(8.dp))
        createChip()
    }
}

@Composable
private fun CreateChip(label: String, a11yLabel: String, onClick: () -> Unit) {
    PocketChip(
        label = label,
        modifier = Modifier.semantics { contentDescription = a11yLabel },
        variant = PocketChipVariant.ADD,
        onClick = onClick,
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
    onCreateTag: ((String?, String) -> Unit)?,
    showRootCreate: Boolean,
) {
    if (openCtx == null) {
        val categories = categoriesFor(universe, contexts, selectedSet)
        val rootCreateChip = onCreateTag?.takeIf { showRootCreate }?.let { create ->
            @Composable {
                CreateChip(
                    label = "+ Nova tag",
                    a11yLabel = "Criar nova tag",
                    onClick = { create(null, "") },
                )
            }
        }
        if (categories.isEmpty()) {
            EmptyBrowseState(
                lead = "Nenhuma tag ainda.",
                pointer = "Crie em Mais › Contextos & Tags.",
                createChip = rootCreateChip,
            )
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                CategoryRow(category = category, onClick = { onOpenCtx(category.id) })
            }
        }
        rootCreateChip?.let { chip ->
            Spacer(Modifier.height(8.dp))
            chip()
        }
        return
    }

    val title = categoriesFor(universe, contexts, selectedSet)
        .firstOrNull { it.id == openCtx }?.name
        ?: "Categoria"
    val drillColor = contexts.firstOrNull { it.id == openCtx }?.color
    // The orphan bucket is synthetic: a tag created from it has no context, not a context named it.
    val drillContextId = openCtx.takeUnless { it == ORPHAN_CONTEXT_ID }
    val drillCreateChip = onCreateTag?.let { create ->
        @Composable {
            val ctxName = drillContextId?.let { id -> contexts.firstOrNull { it.id == id }?.name }
            CreateChip(
                label = "+ Nova tag",
                a11yLabel = ctxName?.let { "Criar nova tag em $it" } ?: "Criar nova tag",
                onClick = { create(drillContextId, "") },
            )
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
                .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PocketTheme.colors.text2,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
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
            trailing = drillCreateChip,
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
            .heightIn(min = 44.dp)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = categoryA11yLabel(category) }
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text3,
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
    trailing: (@Composable () -> Unit)? = null,
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
        trailing?.invoke()
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
            .heightIn(min = 38.dp)
            .border(1.dp, borderColor, PocketTheme.shapes.chip)
            .background(bg, PocketTheme.shapes.chip)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
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
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = PocketTheme.colors.accentInk,
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
    val tagMap = tags.associateBy { it.id }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.accentBg, PocketTheme.shapes.chip)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedTagIds.forEach { tagId ->
            val tag = tagMap[tagId] ?: return@forEach
            val dotArgb = tag.idContext?.let { contextMap[it]?.color } ?: tag.color
            val dotColor = dotArgb?.let { Color(it) } ?: PocketTheme.colors.text3
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
                    .clickable(role = Role.Button) { onRemove(tagId) }
                    .semantics { contentDescription = "Remover ${tag.name}" }
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
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PocketTheme.colors.accentInk.copy(alpha = 0.7f),
                )
            }
        }
    }
}
