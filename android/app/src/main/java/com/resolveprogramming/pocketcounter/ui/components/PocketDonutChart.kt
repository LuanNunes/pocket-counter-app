package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Reusable donut ring. [segments] are (ARGB color Long, fraction 0..1) pairs drawn clockwise from
 * the top (-90°) over a neutral track ring. [centerContent] is laid out in the middle of the ring.
 */
@Composable
fun PocketDonutChart(
    segments: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
    centerContent: @Composable () -> Unit,
) {
    val trackColor = PocketTheme.colors.surface2
    Box(
        modifier = modifier.drawBehind {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke),
            )

            var startAngle = -90f
            segments.forEach { (color, fraction) ->
                val sweep = fraction * 360f
                drawArc(
                    color = Color(color),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        centerContent()
    }
}
