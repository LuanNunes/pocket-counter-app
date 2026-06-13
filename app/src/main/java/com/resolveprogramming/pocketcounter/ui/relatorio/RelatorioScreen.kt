package com.resolveprogramming.pocketcounter.ui.relatorio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import com.resolveprogramming.pocketcounter.domain.model.ReportChartType
import com.resolveprogramming.pocketcounter.domain.model.ReportDetailMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.ReportData
import com.resolveprogramming.pocketcounter.domain.model.ReportPeriod
import com.resolveprogramming.pocketcounter.domain.model.ReportSeries
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.math.BigDecimal
import kotlin.math.abs

@Composable
fun RelatorioScreen(
    onBack: () -> Unit,
    viewModel: RelatorioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isExpense = state.kind == TransactionType.EXPENSE

    Column(modifier = Modifier.fillMaxSize().background(PocketTheme.colors.bg)) {
        ManageTopBar(title = "Relatório", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PocketSegmented(
                    options = listOf(SegmentOption("Mês"), SegmentOption("Trimestre"), SegmentOption("Ano")),
                    selectedIndex = state.period.ordinal,
                    onSelect = { viewModel.setPeriod(ReportPeriod.entries[it]) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", modifier = Modifier.size(28.dp).clickable { viewModel.stepPeriod(-1) }, color = PocketTheme.colors.text, style = PocketTheme.typography.stepQuestion)
                    Text(
                        state.report?.rangeLabel?.replaceFirstChar { it.uppercase() } ?: "",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    Text("›", modifier = Modifier.size(28.dp).clickable { viewModel.stepPeriod(1) }, color = PocketTheme.colors.text, style = PocketTheme.typography.stepQuestion)
                }
            }
            item {
                PocketSegmented(
                    options = listOf(SegmentOption("Despesas"), SegmentOption("Receitas")),
                    selectedIndex = if (isExpense) 0 else 1,
                    onSelect = { viewModel.setKind(if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME) },
                )
            }

            val report = state.report
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                        CircularProgressIndicator(color = PocketTheme.colors.accent)
                    }
                }
                report == null -> item {
                    Text("Sem dados no período.", style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
                }
                else -> {
                    item { KpiGrid(report) }
                    item { ChartCard(report, isExpense, state.chartType, viewModel::setChartType) }
                    item { FluxoCard(report) }
                    item { SparkrowsCard(report, isExpense) }
                    val series = if (isExpense) report.expSeries else report.incSeries
                    item { DetailHeader(isExpense, state.detailMode, viewModel::setDetailMode) }
                    when (state.detailMode) {
                        ReportDetailMode.CARTOES -> {
                            val maxTotal = series.maxOfOrNull { it.total } ?: BigDecimal.ONE
                            items(series.size, key = { series[it].id }) { i ->
                                SeriesCard(series[i], maxTotal, isExpense)
                            }
                        }
                        ReportDetailMode.TABELA -> item { DetailTable(report, series, isExpense) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun KpiGrid(report: ReportData) {
    val k = report.kpis
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Despesas", k.exp, PocketTheme.colors.expense, Modifier.weight(1f), sub = "média ${money(k.expAvg)} / mês")
            KpiTile("Receitas", k.inc, PocketTheme.colors.income, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("Saldo", k.saldo, if (k.saldo.signum() < 0) PocketTheme.colors.expense else PocketTheme.colors.income, Modifier.weight(1f), showSign = true)
            KpiTile("Meses", null, PocketTheme.colors.text, Modifier.weight(1f), rawValue = "${k.months}")
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    amount: BigDecimal?,
    color: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    rawValue: String? = null,
    showSign: Boolean = false,
) {
    PocketCard(modifier = modifier) {
        Column {
            Text(label.uppercase(), style = PocketTheme.typography.label, color = PocketTheme.colors.text3)
            Spacer(Modifier.height(4.dp))
            if (amount != null) {
                AmountText(amount = amount, color = color, showSign = showSign, style = PocketTheme.typography.monoTotal)
            } else {
                Text(rawValue.orEmpty(), style = PocketTheme.typography.monoTotal, color = color)
            }
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            }
        }
    }
}

@Composable
private fun ChartCard(
    report: ReportData,
    isExpense: Boolean,
    chartType: ReportChartType,
    onChartType: (ReportChartType) -> Unit,
) {
    val series = if (isExpense) report.expSeries else report.incSeries
    val labels = report.months.map { it.label }
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "${if (isExpense) "Despesas" else "Receitas"} por ${if (isExpense) "contexto" else "fonte"}",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Spacer(Modifier.height(10.dp))
            PocketSegmented(
                options = listOf(SegmentOption("Barras"), SegmentOption("Área"), SegmentOption("Linhas"), SegmentOption("Pizza")),
                selectedIndex = chartType.ordinal,
                onSelect = { onChartType(ReportChartType.entries[it]) },
            )
            Spacer(Modifier.height(14.dp))
            when (chartType) {
                ReportChartType.BARS -> StackedMonthlyBars(labels, series)
                ReportChartType.AREA -> AreaChart(labels, series)
                ReportChartType.LINES -> LinesChart(labels, series)
                ReportChartType.PIE -> ReportDonut(series, centerLabel = money(if (isExpense) report.kpis.exp else report.kpis.inc))
            }
            if (chartType != ReportChartType.PIE) Legend(series)
        }
    }
}

@Composable
private fun FluxoCard(report: ReportData) {
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Fluxo de caixa", style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = PocketTheme.colors.text)
            Spacer(Modifier.height(12.dp))
            CashFlowChart(report.months)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot("Receitas", PocketTheme.colors.income)
                LegendDot("Despesas", PocketTheme.colors.expense)
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, PocketTheme.shapes.pill))
        Text(label, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
    }
}

@Composable
private fun SparkrowsCard(report: ReportData, isExpense: Boolean) {
    val opposite = if (isExpense) report.incSeries else report.expSeries
    if (opposite.isEmpty()) return
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isExpense) "Receitas por fonte" else "Despesas por contexto",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            opposite.take(4).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(Color(s.color), PocketTheme.shapes.pill))
                    Text(s.name, style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2, modifier = Modifier.weight(1f))
                    Sparkline(s.vals, Color(s.color))
                    AmountText(amount = s.total, style = PocketTheme.typography.monoSm, color = PocketTheme.colors.text)
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(isExpense: Boolean, mode: ReportDetailMode, onMode: (ReportDetailMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Detalhe por ${if (isExpense) "contexto" else "fonte"}",
            style = PocketTheme.typography.sectionHeader,
            color = PocketTheme.colors.text3,
        )
        Box(modifier = Modifier.width(170.dp)) {
            PocketSegmented(
                options = listOf(SegmentOption("Cartões"), SegmentOption("Tabela")),
                selectedIndex = mode.ordinal,
                onSelect = { onMode(ReportDetailMode.entries[it]) },
            )
        }
    }
}

@Composable
private fun DetailTable(report: ReportData, series: List<ReportSeries>, isExpense: Boolean) {
    val months = report.months
    val totalAll = series.fold(BigDecimal.ZERO) { a, s -> a + s.total }
    val rowH = 40.dp
    val nameW = 116.dp
    val colW = 82.dp
    val pctW = 56.dp
    PocketCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row {
            // Pinned group column.
            Column {
                Cell(if (isExpense) "Contexto" else "Fonte", nameW, rowH, header = true)
                series.forEach { Cell(it.name, nameW, rowH) }
                Cell("Total", nameW, rowH, bold = true)
            }
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    months.forEach { Cell(it.label, colW, rowH, header = true, end = true) }
                    Cell("Δ", colW, rowH, header = true, end = true)
                    Cell("Total", colW, rowH, header = true, end = true)
                    Cell("%", pctW, rowH, header = true, end = true)
                }
                series.forEach { s ->
                    Row {
                        s.vals.forEach { Cell(num(it), colW, rowH, end = true, mono = true) }
                        Box(Modifier.width(colW).height(rowH).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                            DeltaBadge(s.delta, isExpense)
                        }
                        Cell(num(s.total), colW, rowH, end = true, mono = true, bold = true)
                        Cell(pct(s.total, totalAll), pctW, rowH, end = true, mono = true)
                    }
                }
                Row {
                    months.forEach { Cell(num(if (isExpense) it.exp else it.inc), colW, rowH, end = true, mono = true, bold = true) }
                    Cell("", colW, rowH)
                    Cell(num(totalAll), colW, rowH, end = true, mono = true, bold = true)
                    Cell("100%", pctW, rowH, end = true, mono = true)
                }
            }
        }
    }
}

@Composable
private fun Cell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    header: Boolean = false,
    bold: Boolean = false,
    end: Boolean = false,
    mono: Boolean = false,
) {
    Box(
        modifier = Modifier.width(width).height(height).padding(horizontal = 8.dp),
        contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val style = when {
            header -> PocketTheme.typography.label
            mono -> PocketTheme.typography.monoSm
            else -> PocketTheme.typography.bodySm
        }
        Text(
            text,
            style = if (bold) style.copy(fontWeight = FontWeight.SemiBold) else style,
            color = if (header) PocketTheme.colors.text3 else PocketTheme.colors.text2,
            maxLines = 1,
        )
    }
}

@Composable
private fun SeriesCard(series: ReportSeries, maxTotal: BigDecimal, isExpense: Boolean) {
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(Color(series.color), PocketTheme.shapes.pill))
                Text(series.name, style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium), color = PocketTheme.colors.text, modifier = Modifier.weight(1f))
                DeltaBadge(series.delta, isExpense)
                Spacer(Modifier.size(6.dp))
                AmountText(amount = series.total, style = PocketTheme.typography.monoSm, color = PocketTheme.colors.text)
            }
            Spacer(Modifier.height(8.dp))
            val frac = if (maxTotal.signum() > 0) series.total.toFloat() / maxTotal.toFloat() else 0f
            ProportionBar(fraction = frac, color = Color(series.color))
        }
    }
}

@Composable
private fun DeltaBadge(delta: Float?, isExpense: Boolean) {
    val (text, color) = when {
        delta == null -> "novo" to PocketTheme.colors.text3
        abs(delta) < 0.005f -> "—" to PocketTheme.colors.text3
        else -> {
            val up = delta > 0
            val good = if (isExpense) !up else up // for expense, up is bad
            val arrow = if (up) "▲" else "▼"
            "$arrow ${(abs(delta) * 100).toInt()}%" to if (good) PocketTheme.colors.income else PocketTheme.colors.expense
        }
    }
    Text(text, style = PocketTheme.typography.bodyXs, color = color)
}

private fun money(value: BigDecimal): String {
    val f = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
    return f.format(value)
}

private val numberFmt = java.text.NumberFormat.getNumberInstance(java.util.Locale("pt", "BR")).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}

private fun num(value: BigDecimal): String = numberFmt.format(value)

private fun pct(part: BigDecimal, whole: BigDecimal): String =
    if (whole.signum() > 0) "${(part.toFloat() / whole.toFloat() * 100).toInt()}%" else "—"
