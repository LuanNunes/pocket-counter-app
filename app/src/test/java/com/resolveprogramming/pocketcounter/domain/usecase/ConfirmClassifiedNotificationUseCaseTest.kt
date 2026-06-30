package com.resolveprogramming.pocketcounter.domain.usecase

import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmClassifiedNotificationUseCaseTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val useCase = ConfirmClassifiedNotificationUseCase(transactionRepository, notificationRepository)

    @Test
    fun `create path saves the draft then marks the notification classified`() = runTest {
        val draft = WizardDraft()
        coEvery { transactionRepository.save(draft) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified("n1", "tx-new") } returns Result.success(Unit)

        val result = useCase("n1", draft, pendingTransactionId = null)

        assertEquals("tx-new", result.getOrNull())
        coVerify(exactly = 1) { transactionRepository.save(draft) }
        coVerify(exactly = 1) { notificationRepository.markClassified("n1", "tx-new") }
    }

    @Test
    fun `pending path marks the existing transaction paid and never creates a new one`() = runTest {
        coEvery { transactionRepository.markPaid("tx-99") } returns Result.success(Unit)
        coEvery { notificationRepository.markClassified("n1", "tx-99") } returns Result.success(Unit)

        val result = useCase("n1", WizardDraft(), pendingTransactionId = "tx-99")

        assertEquals("tx-99", result.getOrNull())
        coVerify(exactly = 1) { transactionRepository.markPaid("tx-99") }
        coVerify(exactly = 1) { notificationRepository.markClassified("n1", "tx-99") }
        coVerify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `markClassified failure is swallowed and the transaction id is still returned`() = runTest {
        val draft = WizardDraft()
        coEvery { transactionRepository.save(draft) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns
            Result.failure(RuntimeException("link failed"))

        val result = useCase("n1", draft, pendingTransactionId = null)

        assertEquals("tx-new", result.getOrNull())
    }

    @Test
    fun `save failure propagates and the notification is not marked classified`() = runTest {
        val draft = WizardDraft()
        coEvery { transactionRepository.save(draft) } returns Result.failure(RuntimeException("save failed"))

        val result = useCase("n1", draft, pendingTransactionId = null)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { notificationRepository.markClassified(any(), any()) }
    }

    @Test
    fun `markPaid failure propagates`() = runTest {
        coEvery { transactionRepository.markPaid("tx-99") } returns Result.failure(RuntimeException("paid failed"))

        val result = useCase("n1", WizardDraft(), pendingTransactionId = "tx-99")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { notificationRepository.markClassified(any(), any()) }
    }
}
