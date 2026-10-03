package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.PaymentMethodPrefsStore
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------------
// Hand-written fake for PaymentMethodPrefsStore
// ---------------------------------------------------------------------------

class FakePaymentMethodPrefsStore(initial: Set<String>? = null) : PaymentMethodPrefsStore {
    private val _flow = MutableStateFlow(initial)
    override val enabledRaw: Flow<Set<String>?> = _flow
    override suspend fun setEnabledRaw(names: Set<String>) { _flow.value = names }
    fun currentValue(): Set<String>? = _flow.value
}

// ---------------------------------------------------------------------------
// Repository tests
// ---------------------------------------------------------------------------

class LocalPaymentMethodPrefsRepositoryTest {

    @Test
    fun `unset store emits default`() = runTest {
        val store = FakePaymentMethodPrefsStore(null)
        val repo = LocalPaymentMethodPrefsRepository(store)

        val result = repo.enabledMethods.first()

        assertEquals(PaymentMethodPreferences.default, result)
    }

    @Test
    fun `setEnabled CRYPTO true persists and re-emits with CRYPTO`() = runTest {
        val store = FakePaymentMethodPrefsStore(null)
        val repo = LocalPaymentMethodPrefsRepository(store)

        repo.setEnabled(PaymentMethod.CRYPTO, true)
        val result = repo.enabledMethods.first()

        assertTrue(result.contains(PaymentMethod.CRYPTO))
    }

    @Test
    fun `disabling down to one method then disabling again keeps at least one`() = runTest {
        // Start with exactly one method enabled
        val store = FakePaymentMethodPrefsStore(setOf("PIX"))
        val repo = LocalPaymentMethodPrefsRepository(store)

        // Attempt to disable the last remaining method — the guard must block it
        repo.setEnabled(PaymentMethod.PIX, false)
        val result = repo.enabledMethods.first()

        assertTrue(result.contains(PaymentMethod.PIX))
        assertEquals(1, result.size)
    }

    @Test
    fun `an enabled change is retained by the fake store`() = runTest {
        val store = FakePaymentMethodPrefsStore(null)
        val repo = LocalPaymentMethodPrefsRepository(store)

        repo.setEnabled(PaymentMethod.CRYPTO, true)

        val persisted = store.currentValue()
        assertTrue(persisted != null && "CRYPTO" in persisted)
    }
}
