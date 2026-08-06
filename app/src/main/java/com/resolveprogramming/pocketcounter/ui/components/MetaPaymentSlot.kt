package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * "· Crédito Nubank" — drawn whole or not at all. It is the drop-first element of a line that must
 * not wrap, and both ellipsis and `weight` leave a stub as narrow as the separator itself.
 *
 * Measured with `Modifier.layout`, not `BoxWithConstraints`: rows sit inside `IntrinsicSize.Min`,
 * and asking a `SubcomposeLayout` for intrinsics throws.
 */
@Composable
fun RowScope.MetaPaymentSlot(payment: String) {
    if (payment.isBlank()) return
    Text(
        text = "· $payment",
        style = PocketTheme.typography.bodyXs,
        color = PocketTheme.colors.text3,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .weight(1f, fill = false)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                if (placeable.width > constraints.maxWidth) return@layout layout(0, 0) {}
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            },
    )
}
