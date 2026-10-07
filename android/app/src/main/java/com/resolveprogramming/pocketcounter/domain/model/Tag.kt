package com.resolveprogramming.pocketcounter.domain.model

data class Tag(
    val id: String,
    val name: String,
    val kind: TransactionType,
    val idContext: String? = null,
    val color: Long? = null,
    /** Set when this tag is owned by a recurring series (Contas Fixas); null otherwise. */
    val idSeries: String? = null,
)

/**
 * An expense tag is only saved under an existing context, so with none there is nothing to create
 * it in; and a kind that is still unknown strands the tag out of the other kind's universe.
 */
fun canCreateTag(type: TransactionType?, contexts: List<TagContext>): Boolean {
    type ?: return false
    if (type == TransactionType.EXPENSE) return contexts.isNotEmpty()
    return true
}

/**
 * A tag being created inline. [contextId] is the context it goes under; [color] is ARGB and only an
 * income category carries one, having no context to take it from.
 */
data class NewTagRequest(
    val name: String,
    val color: Long,
    val contextId: String? = null,
)

/** Income categories are flat; an expense tag is incomplete until a context is picked. */
fun NewTagRequest.isComplete(type: TransactionType): Boolean {
    if (name.isBlank()) return false
    if (type == TransactionType.INCOME) return true
    return contextId != null
}
