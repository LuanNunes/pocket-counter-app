package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.PaymentMethodDictionaryStore
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------------
// Hand-written fake for PaymentMethodDictionaryStore
// ---------------------------------------------------------------------------

class FakePaymentMethodDictionaryStore(
    initial: Map<String, String> = emptyMap(),
) : PaymentMethodDictionaryStore {
    private val map = initial.toMutableMap()

    override suspend fun getRaw(): Map<String, String> = map.toMap()
    override suspend fun put(key: String, methodName: String) { map[key] = methodName }
    override suspend fun remove(key: String) { map.remove(key) }

    fun snapshot(): Map<String, String> = map.toMap()
}

// ---------------------------------------------------------------------------
// Repository tests
// ---------------------------------------------------------------------------

class LocalPaymentMethodDictionaryRepositoryTest {

    @Test
    fun `getMap_emptyStore_returnsEmptyMap`() = runTest {
        val store = FakePaymentMethodDictionaryStore()
        val repo = LocalPaymentMethodDictionaryRepository(store)

        val result = repo.getMap()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `learn_storesUnderNormalizedKey`() = runTest {
        val store = FakePaymentMethodDictionaryStore()
        val repo = LocalPaymentMethodDictionaryRepository(store)

        repo.learn("Débito,", PaymentMethod.DEBIT)

        val snapshot = store.snapshot()
        assertTrue("normalized key 'débito' must be in the store", snapshot.containsKey("débito"))
        assertFalse("raw key 'Débito,' must NOT be stored", snapshot.containsKey("Débito,"))
    }

    @Test
    fun `learn_roundTrip_getMapReturnsTypedMethod`() = runTest {
        val store = FakePaymentMethodDictionaryStore()
        val repo = LocalPaymentMethodDictionaryRepository(store)

        repo.learn("eletronico", PaymentMethod.CREDIT)
        val result = repo.getMap()

        assertEquals(mapOf("eletronico" to PaymentMethod.CREDIT), result)
    }

    @Test
    fun `getMap_corruptStoredName_isDropped`() = runTest {
        val store = FakePaymentMethodDictionaryStore(initial = mapOf("foo" to "INVALID_METHOD_NAME"))
        val repo = LocalPaymentMethodDictionaryRepository(store)

        val result = repo.getMap()

        assertTrue("corrupt entry must be silently dropped", result.isEmpty())
    }

    @Test
    fun `forget_removesEntry`() = runTest {
        val store = FakePaymentMethodDictionaryStore()
        val repo = LocalPaymentMethodDictionaryRepository(store)

        repo.learn("pix", PaymentMethod.PIX)
        repo.forget("pix")
        val result = repo.getMap()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `forget_normalizesKeyBeforeRemoving`() = runTest {
        val store = FakePaymentMethodDictionaryStore()
        val repo = LocalPaymentMethodDictionaryRepository(store)

        repo.learn("pix", PaymentMethod.PIX)
        // Forget using un-normalized form; the repo must normalize before delegating to store.
        repo.forget("PIX,")
        val result = repo.getMap()

        assertTrue(result.isEmpty())
    }
}
