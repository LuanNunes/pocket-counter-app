package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole

/** Resolved theme values needed to paint a token, captured once so derivation stays Compose-free. */
internal data class TokenPalette(
    val accent: Color,
    val accentBg: Color,
    val text2: Color,
    val text3: Color,
    val expense: Color,
    val income: Color,
    val warn: Color,
    val accent2: Color,
    val defaultShape: Shape,
)

internal data class TokenBorder(val width: Dp, val color: Color)

/** Everything the composable needs to render a single token — no branching left at the call site. */
internal data class TokenVisual(
    val fillColor: Color,
    val textColor: Color,
    val shape: Shape,
    val border: TokenBorder?,
    val emphasized: Boolean,
    val stateDesc: String,
)

/**
 * Pure derivation of a token's visual state from its position, role, and the current selection.
 * Selection wins over role; an unmarked token outside the selection renders flat.
 */
internal fun tokenVisual(
    index: Int,
    tokens: List<Token>,
    selection: IntRange?,
    palette: TokenPalette,
): TokenVisual {
    val role = tokens[index].role
    val inSelection = selection != null && index in selection
    val roleBg = roleBackground(role, palette)

    val fillColor = when {
        inSelection -> palette.accentBg
        role != null -> roleBg.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val shape = when {
        inSelection -> spanShape(isFirst = index == selection.first, isLast = index == selection.last)
        role != null -> spanShape(
            isFirst = index == 0 || tokens[index - 1].role != role,
            isLast = index == tokens.lastIndex || tokens[index + 1].role != role,
        )
        else -> palette.defaultShape
    }

    val border = when {
        inSelection -> TokenBorder(2.dp, palette.accent)
        role != null -> TokenBorder(1.dp, roleBg.copy(alpha = 0.5f))
        else -> null
    }

    val stateDesc = when {
        inSelection -> "selecionado"
        role != null -> "marcado como ${roleLabel(role)}"
        else -> ""
    }

    return TokenVisual(
        fillColor = fillColor,
        textColor = if (inSelection) palette.accent else roleText(role, palette),
        shape = shape,
        border = border,
        emphasized = inSelection || role != null,
        stateDesc = stateDesc,
    )
}

/**
 * Rounds only the outer corners of a multi-word run (10dp end caps, ~2dp interior seams) so a span
 * of consecutive tokens reads as a single connected pill. A length-1 run is fully rounded.
 */
private fun spanShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    val cap = 10.dp
    val seam = 2.dp
    return RoundedCornerShape(
        topStart = cap.takeIf { isFirst } ?: seam,
        bottomStart = cap.takeIf { isFirst } ?: seam,
        topEnd = cap.takeIf { isLast } ?: seam,
        bottomEnd = cap.takeIf { isLast } ?: seam,
    )
}

internal fun roleLabel(role: TokenRole): String = when (role) {
    TokenRole.TYPE -> "Tipo"
    TokenRole.AMOUNT -> "Valor"
    TokenRole.PAYMENT -> "Meio de pgto."
    TokenRole.MERCHANT -> "Estabelecimento"
    TokenRole.DATE -> "Data"
    TokenRole.INSTALLMENTS -> "Parcelas"
}

private fun roleBackground(role: TokenRole?, palette: TokenPalette): Color = when (role) {
    TokenRole.TYPE -> palette.expense
    TokenRole.AMOUNT -> palette.income
    TokenRole.PAYMENT -> palette.accent
    TokenRole.MERCHANT -> palette.warn
    TokenRole.DATE -> palette.text3
    TokenRole.INSTALLMENTS -> palette.accent2
    null -> Color.Transparent
}

private fun roleText(role: TokenRole?, palette: TokenPalette): Color = when (role) {
    TokenRole.TYPE -> palette.expense
    TokenRole.AMOUNT -> palette.income
    TokenRole.PAYMENT -> palette.accent
    TokenRole.MERCHANT -> palette.warn
    TokenRole.DATE -> palette.text3
    TokenRole.INSTALLMENTS -> palette.accent2
    null -> palette.text2
}
