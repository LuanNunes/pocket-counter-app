package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.cache.SuspendCache
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.CategoryApi
import com.resolveprogramming.pocketcounter.data.remote.api.TagApi
import com.resolveprogramming.pocketcounter.data.remote.dto.CategoryDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitTagRepository @Inject constructor(
    private val tagApi: TagApi,
    private val categoryApi: CategoryApi,
) : TagRepository {

    // Lookups rarely change and are read on nearly every screen, so they're cached — but a category/tag
    // added on the web must eventually surface without a process restart. The TTL self-heals stale reads;
    // refreshLookups() (wired to pull-to-refresh) drops them immediately for a user-driven refresh.
    private val tagsCache = SuspendCache<List<Tag>>(ttlMillis = LOOKUP_TTL_MS)
    private val contextsCache = SuspendCache<List<TagContext>>(ttlMillis = LOOKUP_TTL_MS)

    override suspend fun getAllContexts(): Result<List<TagContext>> = runCatching {
        contextsCache.get { categoryApi.getCategories().map { it.toDomain() } }
    }

    /** GET /tags carries kind/idCategory/color per tag, so a single fetch is enough. */
    override suspend fun getAllTags(): Result<List<Tag>> = runCatching {
        tagsCache.get { tagApi.getTags().map { it.toDomain() } }
    }

    override suspend fun createTag(input: TagInput): Result<Tag> = runCatching {
        val id = tagApi.addTag(
            TagDto(
                name = input.name,
                idCategory = input.idContext,
                kind = input.kind.name,
                color = input.color?.let { RemoteMappers.colorToHex(it) },
            ),
        ).trim('"')
        Tag(id = id, name = input.name, kind = input.kind, idContext = input.idContext, color = input.color)
    }.onSuccess { invalidateLookups() }

    override suspend fun updateTag(id: String, input: TagInput): Result<Tag> = runCatching {
        tagApi.update(
            id,
            TagDto(
                id = id,
                name = input.name,
                idCategory = input.idContext,
                kind = input.kind.name,
                color = input.color?.let { RemoteMappers.colorToHex(it) },
            ),
        )
        Tag(id = id, name = input.name, kind = input.kind, idContext = input.idContext, color = input.color)
    }.onSuccess { invalidateLookups() }

    override suspend fun deleteTag(id: String): Result<Unit> = runCatching {
        tagApi.delete(id)
    }.onSuccess { invalidateLookups() }

    override suspend fun createContext(input: ContextInput): Result<TagContext> = runCatching {
        val id = categoryApi.addCategory(
            CategoryDto(name = input.name, color = RemoteMappers.colorToHex(input.color)),
        ).trim('"')
        TagContext(id = id, name = input.name, color = input.color)
    }.onSuccess { invalidateLookups() }

    override suspend fun updateContext(id: String, input: ContextInput): Result<TagContext> = runCatching {
        // displayOrder omitted (null) so the backend preserves order — reorder is a later milestone.
        categoryApi.update(id, CategoryDto(id = id, name = input.name, color = RemoteMappers.colorToHex(input.color)))
        TagContext(id = id, name = input.name, color = input.color)
    }.onSuccess { invalidateLookups() }

    override suspend fun deleteContext(id: String): Result<Unit> = runCatching {
        categoryApi.delete(id)
    }.onSuccess { invalidateLookups() }

    /** Drops both lookup caches so the next read refetches — wired to the user-driven pull-to-refresh. */
    override fun refreshLookups() = invalidateLookups()

    /** A context delete can cascade to its tags on the backend, so both caches drop together. */
    private fun invalidateLookups() {
        tagsCache.invalidate()
        contextsCache.invalidate()
    }

    private companion object {
        // Lookups are re-read constantly, so keep the window generous — pull-to-refresh covers the
        // "I just changed it on the web and want it now" case; this only bounds passive staleness.
        const val LOOKUP_TTL_MS = 5 * 60 * 1000L
    }
}
