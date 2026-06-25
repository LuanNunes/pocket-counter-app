package com.resolveprogramming.pocketcounter.ui.components

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType

/**
 * Sentinel context ID for the synthetic "Sem categoria" orphan bucket: tags whose
 * [Tag.idContext] is null/blank, or points at a context not in the known [TagContext] list.
 *
 * Mirror of [com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags]'s `orphanExpenseTags`
 * rule.
 */
const val ORPHAN_CONTEXT_ID = "__orphan"

/**
 * A category row in the tag picker's category list. Android-free: [color] uses the same
 * underlying [Long] that [TagContext.color] stores, so no Compose dependency is needed.
 */
data class TagPickerCategory(
    val id: String,
    val name: String,
    val color: Long?,
    val tagCount: Int,
    val selectedCount: Int,
)

/**
 * Returns tags from [tags] whose [Tag.kind] matches [kind].
 * The result is the universe for all subsequent picker operations.
 */
fun tagUniverse(tags: List<Tag>, kind: TransactionType): List<Tag> =
    tags.filter { it.kind == kind }

/**
 * Returns tags from [universe] whose name contains [query] (case-insensitive).
 * A blank [query] returns an empty list — "not searching" state; callers should render
 * the full context/category drill-down when the result is empty.
 */
fun searchMatches(universe: List<Tag>, query: String): List<Tag> {
    val trimmed = query.trim()
    trimmed.takeIf { it.isNotEmpty() } ?: return emptyList()
    return universe.filter { it.name.contains(trimmed, ignoreCase = true) }
}

/**
 * Builds the category list for the tag picker's top-level view.
 *
 * One [TagPickerCategory] is produced for each [TagContext] that has at least one tag in
 * [universe]. A synthetic [ORPHAN_CONTEXT_ID] category named "Sem categoria" is appended
 * for tags whose [Tag.idContext] is null/blank or references a deleted/unknown context —
 * mirroring the rule in [com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags].
 *
 * [selectedTagIds] drives the [TagPickerCategory.selectedCount] badge on each row.
 */
fun categoriesFor(
    universe: List<Tag>,
    contexts: List<TagContext>,
    selectedTagIds: Set<String>,
): List<TagPickerCategory> {
    val knownContextIds = contexts.map { it.id }.toSet()

    val knownCategories = contexts.mapNotNull { ctx ->
        val ctxTags = universe.filter { it.idContext == ctx.id }
        ctxTags.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        TagPickerCategory(
            id = ctx.id,
            name = ctx.name,
            color = ctx.color,
            tagCount = ctxTags.size,
            selectedCount = ctxTags.count { it.id in selectedTagIds },
        )
    }

    val orphanTags = universe.filter { it.idContext.isNullOrBlank() || it.idContext !in knownContextIds }
    val orphanCategory = orphanTags.takeIf { it.isNotEmpty() }?.let { orphans ->
        TagPickerCategory(
            id = ORPHAN_CONTEXT_ID,
            name = "Sem categoria",
            color = null,
            tagCount = orphans.size,
            selectedCount = orphans.count { it.id in selectedTagIds },
        )
    }

    return knownCategories + listOfNotNull(orphanCategory)
}

/**
 * Returns the tags for a single drill-down view.
 *
 * - [contextId] == [ORPHAN_CONTEXT_ID] → orphan tags (null/blank idContext or unknown context)
 * - [contextId] is a known context ID → tags for that context
 * - [contextId] is null → empty list (nothing to drill into)
 */
fun drillTags(universe: List<Tag>, contextId: String?, contexts: List<TagContext>): List<Tag> {
    contextId ?: return emptyList()
    val knownContextIds = contexts.map { it.id }.toSet()
    contextId.takeIf { it == ORPHAN_CONTEXT_ID }
        ?.let { return universe.filter { it.idContext.isNullOrBlank() || it.idContext !in knownContextIds } }
    return universe.filter { it.idContext == contextId }
}
