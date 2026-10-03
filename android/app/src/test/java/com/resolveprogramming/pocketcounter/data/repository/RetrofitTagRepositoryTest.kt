package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.CategoryApi
import com.resolveprogramming.pocketcounter.data.remote.api.TagApi
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitTagRepositoryTest {

    private val tagApi = mockk<TagApi>()
    private val categoryApi = mockk<CategoryApi>()

    private val repo = RetrofitTagRepository(tagApi = tagApi, categoryApi = categoryApi)

    // -------------------------------------------------------------------------
    // getAllTags / getAllContexts caching
    // -------------------------------------------------------------------------

    @Test
    fun `getAllTags caches the tag list so a second read hits the api once`() = runTest {
        coEvery { tagApi.getTags() } returns emptyList()

        repo.getAllTags()
        repo.getAllTags()

        coVerify(exactly = 1) { tagApi.getTags() }
    }

    @Test
    fun `getAllContexts caches the context list so a second read hits the api once`() = runTest {
        coEvery { categoryApi.getCategories() } returns emptyList()

        repo.getAllContexts()
        repo.getAllContexts()

        coVerify(exactly = 1) { categoryApi.getCategories() }
    }

    @Test
    fun `createTag invalidates both lookup caches so the next reads refetch`() = runTest {
        coEvery { tagApi.getTags() } returns emptyList()
        coEvery { categoryApi.getCategories() } returns emptyList()
        coEvery { tagApi.addTag(any()) } returns "\"tag-1\""

        repo.getAllTags()
        repo.getAllContexts()
        repo.createTag(TagInput(name = "Mercado", kind = TransactionType.EXPENSE, idContext = "cat1"))
        repo.getAllTags()
        repo.getAllContexts()

        coVerify(exactly = 2) { tagApi.getTags() }
        coVerify(exactly = 2) { categoryApi.getCategories() }
    }

    @Test
    fun `deleteContext invalidates both lookup caches so the next reads refetch`() = runTest {
        coEvery { tagApi.getTags() } returns emptyList()
        coEvery { categoryApi.getCategories() } returns emptyList()
        coEvery { categoryApi.delete(any()) } returns Unit

        repo.getAllTags()
        repo.getAllContexts()
        repo.deleteContext("cat1")
        repo.getAllTags()
        repo.getAllContexts()

        coVerify(exactly = 2) { tagApi.getTags() }
        coVerify(exactly = 2) { categoryApi.getCategories() }
    }

    @Test
    fun `refreshLookups drops both lookup caches so the next reads refetch`() = runTest {
        coEvery { tagApi.getTags() } returns emptyList()
        coEvery { categoryApi.getCategories() } returns emptyList()

        repo.getAllTags()
        repo.getAllContexts()
        repo.refreshLookups()
        repo.getAllTags()
        repo.getAllContexts()

        coVerify(exactly = 2) { tagApi.getTags() }
        coVerify(exactly = 2) { categoryApi.getCategories() }
    }

    @Test
    fun `getAllTags does not cache a failed read so the next call retries the api`() = runTest {
        coEvery { tagApi.getTags() } throws RuntimeException("boom") andThen emptyList()

        val first = repo.getAllTags()
        val second = repo.getAllTags()

        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        coVerify(exactly = 2) { tagApi.getTags() }
    }
}
