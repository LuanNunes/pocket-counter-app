package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * "· Crédito Nubank" — drawn whole or not at all. It is the drop-first element of a line that must
 * not wrap, and both ellipsis and `weight` leave a stub as narrow as the separator itself.
 */
@Composable
fun RowScope.MetaPaymentSlot(payment: String) {
    if (payment.isBlank()) return
    val text = "· $payment"
    val style = PocketTheme.typography.bodyXs
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
        val needed = measurer.measure(AnnotatedString(text), style, maxLines = 1).size.width
        if (needed > constraints.maxWidth) return@BoxWithConstraints
        Text(text = text, style = style, color = PocketTheme.colors.text3, maxLines = 1)
    }
}
