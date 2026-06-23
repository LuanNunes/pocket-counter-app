package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ReportData
import com.resolveprogramming.pocketcounter.domain.model.ReportKpis
import com.resolveprogramming.pocketcounter.domain.model.ReportMonth
import com.resolveprogramming.pocketcounter.domain.model.ReportPeriod
import com.resolveprogramming.pocketcounter.domain.model.ReportSeries
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.reportToCsv
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ReportCsvTest {

    // ── fixture ──────────────────────────────────────────────────────────────

    private fun month(key: String, label: String) = ReportMonth(
        key = key,
        label = label,
        exp = BigDecimal.ZERO,
        inc = BigDecimal.ZERO,
        saldo = BigDecimal.ZERO,
        expGroups = emptyList(),
        incGroups = emptyList(),
    )

    private fun series(name: String, vals: List<String>, total: String) = ReportSeries(
        id = name,
        name = name,
        color = 0L,
        vals = vals.map { BigDecimal(it) },
        total = BigDecimal(total),
        delta = null,
    )

    private val months = listOf(
        month("2026-01", "jan"),
        month("2026-02", "fev"),
        month("2026-03", "mar"),
    )

    private val expSeries = listOf(
        series("Alimentação", listOf("100.00", "200.50", "150.75"), "451.25"),
        series("Transporte", listOf("50.00", "75.00", "0.00"), "125.00"),
    )

    private val incSeries = listOf(
        series("Salário", listOf("3000.00", "3000.00", "3000.00"), "9000.00"),
        series("Freelance", listOf("500.00", "0.00", "750.00"), "1250.00"),
    )

    private val kpis = ReportKpis(
        exp = BigDecimal("576.25"),
        inc = BigDecimal("10250.00"),
        saldo = BigDecimal("9673.75"),
        expAvg = BigDecimal("192.08"),
        months = 3,
    )

    private val report = ReportData(
        period = ReportPeriod.TRIMESTRE,
        months = months,
        expSeries = expSeries,
        incSeries = incSeries,
        kpis = kpis,
        rangeLabel = "jan–mar 2026",
    )

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `header has dimension label, month labels, and Total for expense`() {
        val csv = reportToCsv(report, TransactionType.EXPENSE)
        val header = csv.lines().first()

        assertEquals("Contexto;jan;fev;mar;Total", header)
    }

    @Test
    fun `header has dimension label Categoria for income`() {
        val csv = reportToCsv(report, TransactionType.INCOME)
        val header = csv.lines().first()

        assertEquals("Categoria;jan;fev;mar;Total", header)
    }

    @Test
    fun `each expense series becomes a row with per-month values and total`() {
        val csv = reportToCsv(report, TransactionType.EXPENSE)
        val lines = csv.lines()

        assertEquals("Alimentação;100.00;200.50;150.75;451.25", lines[1])
        assertEquals("Transporte;50.00;75.00;0.00;125.00", lines[2])
    }

    @Test
    fun `picks income series when kind is INCOME`() {
        val csv = reportToCsv(report, TransactionType.INCOME)
        val lines = csv.lines()

        assertEquals("Salário;3000.00;3000.00;3000.00;9000.00", lines[1])
        assertEquals("Freelance;500.00;0.00;750.00;1250.00", lines[2])
    }

    @Test
    fun `totals row sums each month column and the grand total`() {
        val csv = reportToCsv(report, TransactionType.EXPENSE)
        val totalsRow = csv.lines().last()

        // Per-month: jan=100+50=150, fev=200.50+75=275.50, mar=150.75+0=150.75
        // Grand total: 451.25+125=576.25
        assertEquals("Total;150.00;275.50;150.75;576.25", totalsRow)
    }

    @Test
    fun `a series name containing the separator is quoted`() {
        val seriesWithSemicolon = series("Alimentação;Saúde", listOf("10.00", "20.00", "30.00"), "60.00")
        val specialReport = report.copy(expSeries = listOf(seriesWithSemicolon))

        val csv = reportToCsv(specialReport, TransactionType.EXPENSE)
        val dataRow = csv.lines()[1]

        assertEquals("\"Alimentação;Saúde\";10.00;20.00;30.00;60.00", dataRow)
    }

    @Test
    fun `empty series yields just header and a zero totals row`() {
        val emptyReport = report.copy(expSeries = emptyList())

        val csv = reportToCsv(emptyReport, TransactionType.EXPENSE)
        val lines = csv.lines()

        assertEquals(2, lines.size)
        assertEquals("Contexto;jan;fev;mar;Total", lines[0])
        assertEquals("Total;0;0;0;0", lines[1])
    }
}
