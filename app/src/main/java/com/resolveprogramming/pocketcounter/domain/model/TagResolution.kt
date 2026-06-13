package com.resolveprogramming.pocketcounter.domain.model

/**
 * The source-canonical effective-tags rule (ported from the tag-workflow spec):
 * a transaction's own tags OVERRIDE; a null own-tag list INHERITS the source's defaults.
 *
 * - `ownTagIds == null`  → inherit `sourceTagIds`
 * - `ownTagIds == []`    → explicit empty override (no tags)
 * - `ownTagIds == [...]` → override with those tags
 *
 * Resolution is done at render time against the loaded source, so editing a source's
 * defaults is retroactively reflected by every still-inheriting transaction — no rewrite.
 */
fun effectiveTagIds(ownTagIds: List<String>?, sourceTagIds: List<String>): List<String> =
    ownTagIds ?: sourceTagIds
