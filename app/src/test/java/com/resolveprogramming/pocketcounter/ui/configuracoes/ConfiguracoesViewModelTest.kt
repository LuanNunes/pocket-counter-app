package com.resolveprogramming.pocketcounter.ui.configuracoes

import androidx.fragment.app.FragmentActivity
import com.resolveprogramming.pocketcounter.data.local.BiometricSettingsStore
import com.resolveprogramming.pocketcounter.data.repository.FakePaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguracoesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authenticator: BiometricAuthenticator = mockk()
    private val settingsStore: BiometricSettingsStore = mockk()
    private val activity: FragmentActivity = mockk()

    private val lockEnabledFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authenticator.availability() } returns BiometricAvailability.Available
        every { settingsStore.lockEnabled } returns lockEnabledFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(repo: FakePaymentMethodPrefsRepository) =
        ConfiguracoesViewModel(repo, authenticator, settingsStore)

    @Test
    fun `state reflects repo emission`() = runTest {
        val repo = FakePaymentMethodPrefsRepository(initial = setOf(PaymentMethod.PIX))

        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(PaymentMethod.PIX), vm.state.value.enabledMethods)
    }

    @Test
    fun `setPaymentMethodEnabled delegates to repo`() = runTest {
        val repo = FakePaymentMethodPrefsRepository()

        val vm = makeViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setPaymentMethodEnabled(PaymentMethod.CRYPTO, true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.flow.value.contains(PaymentMethod.CRYPTO))
    }

    @Test
    fun `onToggleLock target false persists disabled`() = runTest {
        val repo = FakePaymentMethodPrefsRepository()
        coEvery { settingsStore.setLockEnabled(false) } returns Unit

        val vm = makeViewModel(repo)
        vm.onToggleLock(activity, target = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsStore.setLockEnabled(false) }
    }
}
