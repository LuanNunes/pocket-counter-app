package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod

/** Icon used to represent a payment method across selection UIs. */
fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CREDIT -> Icons.Filled.CreditCard
    PaymentMethod.DEBIT -> Icons.Filled.AccountBalanceWallet
    PaymentMethod.PIX -> Icons.Filled.Bolt
    PaymentMethod.CASH -> Icons.Filled.Payments
    PaymentMethod.CRYPTO -> Icons.Filled.CurrencyBitcoin
}
