package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class ParsedNotification(
    val type: TransactionType?,
    val amount: BigDecimal?,
    val date: LocalDate?,
    val merchantRaw: String?,
    val paymentHint: String?,
    val installments: Int? = null,
    val installmentValue: BigDecimal? = null,
)
