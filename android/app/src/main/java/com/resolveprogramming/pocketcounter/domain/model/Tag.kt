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
