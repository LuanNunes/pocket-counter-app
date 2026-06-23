package com.resolveprogramming.pocketcounter.domain.model

/**
 * The effective-tags rule (ported from the tag-workflow spec):
 * a non-null own-tag list is the transaction's own tags (OVERRIDE); a null own-tag list falls
 * back to the supplied inheritance set.
 *
 * - `ownTagIds == null`  → inherit `inheritedTagIds`
 * - `ownTagIds == []`    → explicit empty override (no tags)
 * - `ownTagIds == [...]` → override with those tags
 *
 * NOTE: since the Fontes removal every caller passes an EMPTY inheritance set, so a null
 * own-tag list currently resolves to no tags. Recurring-series tag inheritance (a transaction
 * inheriting its `idSeries` series' tags) is not yet wired through this function — the parameter
 * is the seam for it. Keep the null-vs-`[]`-vs-`[...]` distinction; the wizard/`setTags` flow
 * relies on it.
 */
fun effectiveTagIds(ownTagIds: List<String>?, inheritedTagIds: List<String>): List<String> =
    ownTagIds ?: inheritedTagIds
