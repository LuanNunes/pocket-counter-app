package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toClassified
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ParsedNotificationDto
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Read-side merchant heal. Notifications captured before the parser learned the Itaú
 * "em <merchant>, dd/MM" shape have `parsedMerchant` stored as null, and the backend neither
 * re-parses nor offers an update endpoint — so the mappers must re-parse the text on read.
 */
class RemoteMappersMerchantFallbackTest {

    private val itauPush =
        "29040 Compra aprovada de R$ 23,97 em DL*UberRides, 08/08 as 18:53 no seu Itau final 4842."

    private fun notification(
        text: String = itauPush,
        parsedMerchant: String? = null,
    ) = NotificationDto(
        id = "notif-1",
        app = "com.itau.android",
        channel = "PUSH",
        text = text,
        status = "NEEDS_REVIEW",
        parsedAmount = BigDecimal("23.97"),
        parsedDate = "2026-08-08",
        parsedMerchant = parsedMerchant,
    )

    @Test
    fun `a stored merchant wins over the local re-parse`() {
        val item = notification(parsedMerchant = "MERCEARIA DA ESQUINA").toDomain()

        assertEquals("MERCEARIA DA ESQUINA", item.parsed.merchantRaw)
    }

    @Test
    fun `a null stored merchant is healed from the text`() {
        val item = notification(parsedMerchant = null).toDomain()

        assertEquals("UberRides", item.parsed.merchantRaw)
    }

    @Test
    fun `a blank stored merchant is healed from the text`() {
        val item = notification(parsedMerchant = "   ").toDomain()

        assertEquals("UberRides", item.parsed.merchantRaw)
    }

    @Test
    fun `text with no recognizable merchant stays null`() {
        val item = notification(text = "Seu limite disponivel foi atualizado.").toDomain()

        assertNull(item.parsed.merchantRaw)
    }

    @Test
    fun `classify heals before tokenizing so the MERCHANT chip is produced`() {
        val base = notification(parsedMerchant = null).toDomain()
        val response = ClassifyResponseDto(
            notificationId = base.id,
            status = "NEEDS_REVIEW",
            parsed = ParsedNotificationDto(
                type = "EXPENSE",
                amount = BigDecimal("23.97"),
                date = "2026-08-08",
                merchantRaw = null,
            ),
        )

        val merchants = response.toClassified(base).notification.tokens
            .filter { it.role == TokenRole.MERCHANT }

        assertEquals(1, merchants.size)
        assertEquals("DL*UberRides,", merchants.single().text)
    }

    @Test
    fun `toRequestDto does not heal the merchant from text`() {
        val message = CapturedMessage(
            app = "com.itau.android",
            channel = NotificationChannel.PUSH,
            text = itauPush,
            receivedAt = Instant.parse("2026-08-08T18:53:00Z"),
        )
        val unhealed = ParsedNotification(
            type = null,
            amount = BigDecimal("23.97"),
            date = null,
            merchantRaw = null,
            paymentHint = null,
        )

        val dto = message.toRequestDto(unhealed)

        assertNull(dto.parsedMerchant)
    }
}
