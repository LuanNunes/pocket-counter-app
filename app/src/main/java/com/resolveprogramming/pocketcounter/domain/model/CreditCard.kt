package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

/**
 * Client-side credit-card display model. Backend mapping (see /pocket-counter):
 * - [id]/[name] ← PaymentSource (a credit card IS a PaymentSource with type CREDIT_CARD)
 * - [billDay]   ← PaymentSourceDto.refDayBill (the only billing field that exists today)
 * - [brand], [last4], [limit], [gradientStart]/[gradientEnd] have NO backend column yet —
 *   they are app-only metadata. Wiring the real Cartões screen needs either new
 *   PaymentSource fields (brand/last4/creditLimit) or a separate card-metadata store;
 *   the gradient can stay client-derived from [brand].
 */
data class CreditCard(
    val id: String,
    val name: String,
    val brand: String,
    val last4: String,
    // ARGB Long — each card stores its own gradient start/end colors
    val gradientStart: Long,
    val gradientEnd: Long,
    val limit: BigDecimal,
    val billDay: Int,
)
