package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Approximates the CSS `--shadow-card`
 * (`0 1px 0 …/.02, 0 12px 32px -16px …/.18`): a soft, large, downward-biased drop.
 * Compose's [shadow] can't reproduce the negative spread, so we tune elevation + colors.
 */
@Composable
fun Modifier.pocketCardShadow(shape: Shape = PocketTheme.shapes.card): Modifier =
    this.shadow(
        elevation = 10.dp,
        shape = shape,
        ambientColor = Color(0x0A141428),
        spotColor = Color(0x24141428),
    )

/**
 * Approximates the CSS `--shadow-pop` used by bottom sheets, menus and toasts —
 * a deeper, wider lift than [pocketCardShadow].
 */
@Composable
fun Modifier.pocketPopShadow(shape: Shape = PocketTheme.shapes.sheet): Modifier =
    this.shadow(
        elevation = 24.dp,
        shape = shape,
        ambientColor = Color(0x1F141428),
        spotColor = Color(0x47141428),
    )

/**
 * Press feedback used across interactive surfaces (CSS `:active { scale(.96–.99) }`).
 * Collapses to no-op when the system requests reduced motion.
 *
 * @param interactionSource share the same source the clickable uses so the scale
 *   tracks real presses.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = pressedScale.takeIf { pressed } ?: 1f,
        label = "pressScale",
    )
    return this.scale(scale)
}

private val DashIntervals = floatArrayOf(6f, 4f)

/** A dashed 1dp outline, for surfaces the handoff draws with `border-style: dashed`. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(DashIntervals, 0f),
        ),
    )
}

/** The same dash as [dashedBorder], as a rule along the top edge (`border-top: 1px dashed`). */
fun Modifier.dashedTopRule(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset.Zero,
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(DashIntervals, 0f),
    )
}
