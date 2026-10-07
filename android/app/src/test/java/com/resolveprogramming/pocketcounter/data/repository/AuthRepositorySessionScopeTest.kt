package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.local.ManualEntryRelay
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.api.CategoryApi
import com.resolveprogramming.pocketcounter.data.remote.api.SeriesApi
import com.resolveprogramming.pocketcounter.data.remote.api.TagApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import com.resolveprogramming.pocketcounter.data.session.SessionScopedStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * An account switch must not leave the previous user's data in memory. Every store registered in the
 * `SessionScopedStore` set is cleared on logout, on a successful login and on account deletion.
 */
class AuthRepositorySessionScopeTest {

    private val authApi: AuthApi = mockk()
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val appLockState = AppLockState()

    private val registered = List(3) { RecordingStore() }

    private fun makeRepo(vararg extra: SessionScopedStore) =
        AuthRepository(authApi, tokenStore, json, appLockState, registered.toSet() + extra)

    private fun successResponse() = Response.success(
        TokenResponse(accessToken = "access", refreshToken = "refresh", expiresIn = 3600),
    )

    private fun cleared() = registered.map { it.cleared }

    private val untouched = listOf(0, 0, 0)
    private val allCleared = listOf(1, 1, 1)

    @Test
    fun `logout clears every registered store`() = runTest {
        makeRepo().logout()

        assertEquals(allCleared, cleared())
    }

    @Test
    fun `a successful login clears every registered store`() = runTest {
        coEvery { authApi.login(any()) } returns successResponse()

        makeRepo().login("user@example.com", "password123")

        assertEquals(allCleared, cleared())
    }

    @Test
    fun `a successful Google login clears every registered store`() = runTest {
        coEvery { authApi.googleLogin(any()) } returns successResponse()

        makeRepo().loginWithGoogle("id-token")

        assertEquals(allCleared, cleared())
    }

    @Test
    fun `a successful register clears every registered store`() = runTest {
        coEvery { authApi.register(any()) } returns successResponse()

        makeRepo().register("Alice", "alice@example.com", "password123")

        assertEquals(allCleared, cleared())
    }

    @Test
    fun `a rejected login leaves the stores alone`() = runTest {
        coEvery { authApi.login(any()) } returns Response.error(401, "".toResponseBody())

        makeRepo().login("user@example.com", "wrong")

        assertEquals(untouched, cleared())
    }

    @Test
    fun `deleteAccount clears every registered store`() = runTest {
        coEvery { authApi.deleteAccount() } returns Response.success(Unit)

        makeRepo().deleteAccount()

        assertEquals(allCleared, cleared())
    }

    @Test
    fun `a failed deleteAccount keeps the session, so it keeps the stores`() = runTest {
        coEvery { authApi.deleteAccount() } throws IOException("network error")

        makeRepo().deleteAccount()

        assertEquals(untouched, cleared())
    }

    @Test
    fun `tags read after a logout refetch instead of serving the previous account's cache`() = runTest {
        val tagApi = mockk<TagApi>()
        val categoryApi = mockk<CategoryApi>()
        coEvery { tagApi.getTags() } returns emptyList()
        coEvery { categoryApi.getCategories() } returns emptyList()
        val tagRepository = RetrofitTagRepository(tagApi = tagApi, categoryApi = categoryApi)
        val repo = makeRepo(tagRepository)

        tagRepository.getAllTags()
        tagRepository.getAllContexts()
        repo.logout()
        tagRepository.getAllTags()
        tagRepository.getAllContexts()

        coVerify(exactly = 2) { tagApi.getTags() }
        coVerify(exactly = 2) { categoryApi.getCategories() }
    }

    @Test
    fun `series read after a logout refetch too`() = runTest {
        val seriesApi = mockk<SeriesApi>()
        coEvery { seriesApi.getAll() } returns emptyList()
        val seriesRepository = RetrofitSeriesRepository(seriesApi)
        val repo = makeRepo(seriesRepository)

        seriesRepository.getAll()
        repo.logout()
        seriesRepository.getAll()

        coVerify(exactly = 2) { seriesApi.getAll() }
    }

    @Test
    fun `logout drops the pending manual-entry sentence`() = runTest {
        val relay = ManualEntryRelay()
        relay.seed("mercado 120 no crédito")

        makeRepo(relay).logout()

        assertNull(relay.pending.value)
    }

    private class RecordingStore : SessionScopedStore {
        var cleared = 0
            private set

        override fun clearForSession() {
            cleared += 1
        }
    }
}
