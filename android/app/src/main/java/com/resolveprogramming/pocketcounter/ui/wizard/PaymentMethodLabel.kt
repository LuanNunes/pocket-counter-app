package com.resolveprogramming.pocketcounter.ui.wizard

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod

/** pt-BR display label for a payment method. */
fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CREDIT -> "Crédito"
    PaymentMethod.DEBIT -> "Débito"
    PaymentMethod.PIX -> "Pix"
    PaymentMethod.CASH -> "Dinheiro"
    PaymentMethod.CRYPTO -> "Cripto"
}
