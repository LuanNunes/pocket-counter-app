package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext

/** Payload for creating/editing a Context (Gestão → Contextos & Tags). [color] is ARGB Long. */
data class ContextInput(val name: String, val color: Long)

/** Payload for creating/editing a Tag. */
data class TagInput(val name: String, val idContext: String)

interface TagRepository {
    suspend fun getAllTags(): Result<List<Tag>>
    suspend fun getAllContexts(): Result<List<TagContext>>
    suspend fun createTag(name: String, idContext: String): Result<Tag>
    suspend fun updateTag(id: String, input: TagInput): Result<Tag>
    suspend fun deleteTag(id: String): Result<Unit>
    suspend fun createContext(input: ContextInput): Result<TagContext>
    suspend fun updateContext(id: String, input: ContextInput): Result<TagContext>
    suspend fun deleteContext(id: String): Result<Unit>
}
