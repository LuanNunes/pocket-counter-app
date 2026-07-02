package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitClassificationRuleRepositoryTest {

    private val api = mockk<ClassificationRuleApi>()
    private val repo = RetrofitClassificationRuleRepository(api)

    private fun rule(id: String?) = ClassificationRule(
        id = id,
        patterns = listOf("Ifood"),
        matchType = "CONTAINS",
        active = true,
        appliedCount = 3,
        transactionType = TransactionType.EXPENSE,
        paymentMethod = PaymentMethod.CREDIT,
        cardId = "card-9",
        tags = emptyList(),
        action = RuleAction.SUGGEST,
    )

    @Test
    fun `update PUTs the serialized rule under its id`() = runTest {
        coEvery { api.update(any(), any()) } returns "rule-1"

        val result = repo.update(rule("rule-1"))

        assertTrue(result.isSuccess)
        val body = slot<ClassificationRuleDto>()
        coVerify { api.update("rule-1", capture(body)) }
        assertEquals("CREDIT", body.captured.paymentMethod)
        assertEquals("card-9", body.captured.cardId)
    }

    @Test
    fun `update fails without calling the api when the rule has no id`() = runTest {
        val result = repo.update(rule(id = null))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { api.update(any(), any()) }
    }
}
