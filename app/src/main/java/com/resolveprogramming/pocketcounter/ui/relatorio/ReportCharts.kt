package com.resolveprogramming.pocketcounter.ui.relatorio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.ReportMonth
import com.resolveprogramming.pocketcounter.domain.model.ReportSeries
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal

private fun List<ReportSeries>.monthVal(mi: Int): Float =
    fold(0f) { acc, s -> acc + (s.vals.getOrNull(mi)?.toFloat() ?: 0f) }

/** Month labels under a chart, equal-weight columns to roughly align with the bars/points. */
@Composable
private fun MonthLabels(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEach {
            Text(
                it,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Per-month bars stacked by group (each series a colored layer). */
@Composable
fun StackedMonthlyBars(labels: List<String>, series: List<ReportSeries>, modifier: Modifier = Modifier) {
    val maxTotal = labels.indices.maxOfOrNull { series.monthVal(it) }?.takeIf { it > 0f } ?: 1f
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val colW = size.width / n
            val barW = colW * 0.6f
            labels.indices.forEach { mi ->
                val cx = colW * mi + colW / 2
                var yBottom = size.height
                series.forEach { s ->
                    val v = s.vals.getOrNull(mi)?.toFloat() ?: 0f
                    val h = v / maxTotal * size.height
                    if (h > 0f) {
                        drawRect(Color(s.color), topLeft = Offset(cx - barW / 2, yBottom - h), size = Size(barW, h))
                        yBottom -= h
                    }
                }
            }
        }
        MonthLabels(labels)
    }
}

/** Stacked filled area chart. Falls back to bars when there are <2 months. */
@Composable
fun AreaChart(labels: List<String>, series: List<ReportSeries>, modifier: Modifier = Modifier) {
    if (labels.size < 2) { StackedMonthlyBars(labels, series, modifier); return }
    val maxTotal = labels.indices.maxOfOrNull { series.monthVal(it) }?.takeIf { it > 0f } ?: 1f
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size
            val dx = size.width / (n - 1)
            fun y(v: Float) = size.height - (v / maxTotal * size.height)
            // Sum of the layers below series index [si] at month [mi].
            fun below(si: Int, mi: Int): Float =
                series.take(si).fold(0f) { acc, x -> acc + (x.vals.getOrNull(mi)?.toFloat() ?: 0f) }
            series.forEachIndexed { si, s ->
                val closed = Path()
                labels.indices.forEach { mi ->
                    val top = below(si, mi) + (s.vals.getOrNull(mi)?.toFloat() ?: 0f)
                    if (mi == 0) closed.moveTo(0f, y(top))
                    if (mi != 0) closed.lineTo(dx * mi, y(top))
                }
                for (mi in labels.indices.reversed()) closed.lineTo(dx * mi, y(below(si, mi)))
                closed.close()
                drawPath(closed, Color(s.color), alpha = 0.85f)
            }
        }
        MonthLabels(labels)
        Legend(series)
    }
}

/** Multi-series line chart with dots. */
@Composable
fun LinesChart(labels: List<String>, series: List<ReportSeries>, modifier: Modifier = Modifier) {
    val maxV = series.flatMap { it.vals }.maxOfOrNull { it.toFloat() }?.takeIf { it > 0f } ?: 1f
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val dx = (size.width / (n - 1)).takeIf { n > 1 } ?: 0f
            fun y(v: Float) = size.height - (v / maxV * size.height)
            series.take(6).forEach { s ->
                val path = Path()
                labels.indices.forEach { mi ->
                    val x = (dx * mi).takeIf { n > 1 } ?: (size.width / 2)
                    val yy = y(s.vals.getOrNull(mi)?.toFloat() ?: 0f)
                    if (mi == 0) path.moveTo(x, yy)
                    if (mi != 0) path.lineTo(x, yy)
                }
                drawPath(path, Color(s.color), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                labels.indices.forEach { mi ->
                    val x = (dx * mi).takeIf { n > 1 } ?: (size.width / 2)
                    drawCircle(Color(s.color), radius = 3.2.dp.toPx(), center = Offset(x, y(s.vals.getOrNull(mi)?.toFloat() ?: 0f)))
                }
            }
        }
        MonthLabels(labels)
        Legend(series.take(6))
    }
}

/** Donut of the period totals per group + a value legend. */
@Composable
fun ReportDonut(series: List<ReportSeries>, centerLabel: String, modifier: Modifier = Modifier) {
    val total = series.fold(0f) { acc, s -> acc + s.total.toFloat() }.coerceAtLeast(1f)
    val trackColor = PocketTheme.colors.surface2
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(160.dp)) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(trackColor, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                var start = -90f
                series.forEach { s ->
                    val sweep = s.total.toFloat() / total * 360f
                    drawArc(Color(s.color), start, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
            Text(centerLabel, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        }
        Spacer(Modifier.height(8.dp))
        Legend(series)
    }
}

/** Two-line cash-flow chart: receitas (income) vs despesas (expense) per month. */
@Composable
fun CashFlowChart(months: List<ReportMonth>, modifier: Modifier = Modifier) {
    val maxV = months.maxOfOrNull { maxOf(it.inc.toFloat(), it.exp.toFloat()) }?.takeIf { it > 0f } ?: 1f
    val incColor = PocketTheme.colors.income
    val expColor = PocketTheme.colors.expense
    Column(modifier = modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = months.size.coerceAtLeast(1)
            val dx = (size.width / (n - 1)).takeIf { n > 1 } ?: 0f
            fun y(v: Float) = size.height - (v / maxV * size.height)
            listOf(incColor to { m: ReportMonth -> m.inc.toFloat() }, expColor to { m: ReportMonth -> m.exp.toFloat() }).forEach { (color, pick) ->
                val path = Path()
                months.forEachIndexed { i, m ->
                    val x = (dx * i).takeIf { n > 1 } ?: (size.width / 2)
                    val yy = y(pick(m))
                    if (i == 0) path.moveTo(x, yy)
                    if (i != 0) path.lineTo(x, yy)
                }
                drawPath(path, color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                months.forEachIndexed { i, m ->
                    val x = (dx * i).takeIf { n > 1 } ?: (size.width / 2)
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y(pick(m))))
                }
            }
        }
        MonthLabels(months.map { it.label })
    }
}

/** Tiny inline trend line used in detail/sparkrows. */
@Composable
fun Sparkline(values: List<BigDecimal>, color: Color, modifier: Modifier = Modifier) {
    val floats = values.map { it.toFloat() }
    val min = (floats.minOrNull() ?: 0f).coerceAtMost(0f)
    val max = (floats.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val range = (max - min).coerceAtLeast(1f)
    Canvas(modifier.size(width = 64.dp, height = 24.dp)) {
        if (floats.size < 2) return@Canvas
        val dx = size.width / (floats.size - 1)
        fun y(v: Float) = size.height - ((v - min) / range * size.height)
        val path = Path()
        floats.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(0f, y(v))
            if (i != 0) path.lineTo(dx * i, y(v))
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(size.width, y(floats.last())))
    }
}

/** Wrapped dot+name legend shown under bars/area/lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(series: List<ReportSeries>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        series.take(8).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).background(Color(s.color), PocketTheme.shapes.pill))
                Text(s.name, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            }
        }
    }
}

/** A thin horizontal proportion bar used in detail rows. */
@Composable
fun ProportionBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color, PocketTheme.shapes.pill),
        )
    }
}
