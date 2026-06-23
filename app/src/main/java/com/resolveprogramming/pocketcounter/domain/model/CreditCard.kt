package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

/**
 * Client-side credit-card display model. Backend mapping (see /pocket-counter):
 * - [id]/[name]/[brand] ← CreditCardDto
 * - [last4], [limit], [billDay], [gradientStart]/[gradientEnd] have NO backend column yet —
 *   they are app-only metadata. The gradient stays client-derived from [brand].
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
