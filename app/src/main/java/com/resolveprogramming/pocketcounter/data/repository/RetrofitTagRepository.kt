package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.ContextApi
import com.resolveprogramming.pocketcounter.data.remote.api.TagApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ContextDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitTagRepository @Inject constructor(
    private val tagApi: TagApi,
    private val contextApi: ContextApi,
) : TagRepository {

    override suspend fun getAllContexts(): Result<List<TagContext>> = runCatching {
        contextApi.getContexts().map { it.toDomain() }
    }

    /**
     * GET /tags may not carry idContext, which the Resumo grouping needs. We fetch each
     * context's tags (idContext guaranteed) and fold in any context-less tags from /tags.
     */
    override suspend fun getAllTags(): Result<List<Tag>> = runCatching {
        val contexts = contextApi.getContexts()
        val byContext = contexts.flatMap { ctx ->
            val ctxId = ctx.id ?: ctx.name
            tagApi.getTagsByContext(ctxId).map { it.toDomain(idContextFallback = ctxId) }
        }
        val seen = byContext.map { it.id }.toMutableSet()
        val orphans = tagApi.getTags()
            .map { it.toDomain() }
            .filter { seen.add(it.id) }
        byContext + orphans
    }

    override suspend fun createTag(name: String, idContext: String): Result<Tag> = runCatching {
        val id = tagApi.addTag(TagDto(name = name, idContext = idContext)).trim('"')
        Tag(id = id, name = name, idContext = idContext)
    }

    override suspend fun updateTag(id: String, input: TagInput): Result<Tag> = runCatching {
        tagApi.update(id, TagDto(id = id, name = input.name, idContext = input.idContext))
        Tag(id = id, name = input.name, idContext = input.idContext)
    }

    override suspend fun deleteTag(id: String): Result<Unit> = runCatching {
        tagApi.delete(id)
    }

    override suspend fun createContext(input: ContextInput): Result<TagContext> = runCatching {
        val id = contextApi.addContext(
            ContextDto(name = input.name, color = RemoteMappers.colorToHex(input.color)),
        ).trim('"')
        TagContext(id = id, name = input.name, color = input.color)
    }

    override suspend fun updateContext(id: String, input: ContextInput): Result<TagContext> = runCatching {
        // displayOrder omitted (null) so the backend preserves order — reorder is a later milestone.
        contextApi.update(id, ContextDto(id = id, name = input.name, color = RemoteMappers.colorToHex(input.color)))
        TagContext(id = id, name = input.name, color = input.color)
    }

    override suspend fun deleteContext(id: String): Result<Unit> = runCatching {
        contextApi.delete(id)
    }
}
