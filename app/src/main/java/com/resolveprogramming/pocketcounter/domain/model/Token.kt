package com.resolveprogramming.pocketcounter.domain.model

data class Token(
    val text: String,
    val role: TokenRole? = null,
    val value: String? = null,
)

enum class TokenRole {
    TYPE,
    AMOUNT,
    DATE,
    PAYMENT,
    MERCHANT,
    INSTALLMENTS,
}
