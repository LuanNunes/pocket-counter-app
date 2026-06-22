package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.domain.model.CompareOption
import com.resolveprogramming.pocketcounter.domain.model.MonthlySummary
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.SummaryTag
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resumo analytics are DERIVED client-side — the backend has no analytics/summary
 * endpoint. We fetch per-month transactions (GET /transactions/{incomes|expenses}/{ref}),
 * group despesas by Contexto (via tag→context) and receitas by categoria de renda
 * (via the effective kind=INCOME tag), and compare against a prior month or a smoothed
 * 3-month average.
 */
@Singleton
class RetrofitAnalyticsRepository @Inject constructor(
    private val transactionApi: TransactionApi,
    private val tagRepository: TagRepository,
    private val sourceRepository: SourceRepository,
) : AnalyticsRepository {

    private val ptBr = Locale("pt", "BR")
    private val noContextId = "sem-contexto"
    private val noContextColor = 0xFF9AA0A6
    private val noIncomeCategoryId = "sem-categoria"
    private val incomeFallbackColor = 0xFF33AA77

    override suspend fun compareOptions(monthKey: String): Result<List<CompareOption>> = runCatching {
        val ym = monthKey.toYearMonth()
        val months = (1..3).map { ym.minusMonths(it.toLong()) }
        months.map { m ->
            val label = m.month.getDisplayName(TextStyle.FULL, ptBr)
            CompareOption(key = m.key(), label = label, short = label)
        } + CompareOption(key = "avg3", label = "Média 3 meses", short = "média 3m")
    }

    override suspend fun summary(
        monthKey: String,
        kind: TransactionType,
        compareKey: String?,
    ): Result<MonthlySummary> = runCatching {
        val tags = tagRepository.getAllTags().getOrDefault(emptyList())
        val tagToContext: Map<String, String?> = tags.associate { it.id to it.idContext }
        val incomeTagById: Map<String, Tag> =
            tags.filter { it.kind == TransactionType.INCOME }.associateBy { it.id }
        val tagNameById = tags.associate { it.id to it.name }
        val contextById = tagRepository.getAllContexts().getOrDefault(emptyList())
            .associateBy { it.id }
        val sources = sourceRepository.getAll().getOrDefault(emptyList())
        val sourceNames = sources.associate { it.id to it.name }
        // Source default tags, so an expense with no own tags inherits its source's context
        // (matches Transações / Cartões); without this Resumo buckets it as "Sem categoria".
        val sourceTags = sources.associate { it.id to it.tags }

        val ym = monthKey.toYearMonth()
        val current = fetch(kind, ym)
        val grandTotal = current.sumAmount()

        // group id -> (name, color, items)
        data class Bucket(val name: String, val color: Long, val txs: MutableList<TransactionDto>)
        val buckets = LinkedHashMap<String, Bucket>()
        for (tx in current) {
            val (gid, name, color) = classify(tx, kind, tagToContext, contextById, incomeTagById, sourceNames, sourceTags)
            buckets.getOrPut(gid) { Bucket(name, color, mutableListOf()) }.txs.add(tx)
        }

        // baseline (per-group + grand) from the chosen comparison
        val baseline: Map<String, BigDecimal>?
        val baseTotal: BigDecimal?
        when {
            compareKey == null -> { baseline = null; baseTotal = null }
            compareKey == "avg3" -> {
                val months = (1..3).map { ym.minusMonths(it.toLong()) }
                val perMonth = months.map { groupTotals(kind, it, tagToContext, contextById, incomeTagById, sourceNames, sourceTags) }
                baseline = averageGroupTotals(perMonth)
                baseTotal = perMonth.map { it.values.fold(BigDecimal.ZERO, BigDecimal::add) }
                    .fold(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
            }
            else -> {
                baseline = groupTotals(kind, compareKey.toYearMonth(), tagToContext, contextById, incomeTagById, sourceNames, sourceTags)
                baseTotal = baseline.values.fold(BigDecimal.ZERO, BigDecimal::add)
            }
        }

        val groups = buckets.map { (gid, bucket) ->
            val total = bucket.txs.sumAmount()
            val prev = baseline?.get(gid)
            SummaryGroup(
                id = gid,
                name = bucket.name,
                color = bucket.color,
                total = total,
                pct = ratio(total, grandTotal),
                delta = relativeDelta(total, prev),
                prevTotal = prev,
                tags = drillTags(bucket.txs, kind, incomeTagById, sourceTags, tagNameById),
            )
        }.sortedByDescending { it.total }

        MonthlySummary(
            groups = groups,
            total = grandTotal,
            totalDelta = relativeDelta(grandTotal, baseTotal),
            baseTotal = baseTotal,
        )
    }

    override suspend fun report(
        period: com.resolveprogramming.pocketcounter.domain.model.ReportPeriod,
        anchorMonthKey: String,
    ): Result<com.resolveprogramming.pocketcounter.domain.model.ReportData> = runCatching {
        val anchor = anchorMonthKey.toYearMonth()
        val monthsList = com.resolveprogramming.pocketcounter.domain.model.reportPeriodMonths(period, anchor)
        val fromRef = monthsList.first().let { it.year * 100 + it.monthValue }
        val toRef = monthsList.last().let { it.year * 100 + it.monthValue }

        val allTags = tagRepository.getAllTags().getOrDefault(emptyList())
        val tagToContext: Map<String, String?> = allTags.associate { it.id to it.idContext }
        val incomeTagById: Map<String, Tag> =
            allTags.filter { it.kind == TransactionType.INCOME }.associateBy { it.id }
        val contextById = tagRepository.getAllContexts().getOrDefault(emptyList()).associateBy { it.id }
        val sources = sourceRepository.getAll().getOrDefault(emptyList())
        val sourceNames = sources.associate { it.id to it.name }
        val sourceTags = sources.associate { it.id to it.tags }

        val all = transactionApi.getRange(fromRef, toRef)
        val byMonthRef = all.groupBy { it.refYearMonth }

        val reportMonths = monthsList.map { ym ->
            val ref = ym.year * 100 + ym.monthValue
            val txs = byMonthRef[ref].orEmpty()
            val incomes = txs.filter { RemoteMappers.parseType(it.transactionType) == TransactionType.INCOME }
            // Anything not income counts as expense, so an unclassifiable row never silently vanishes.
            val expenses = txs.filter { RemoteMappers.parseType(it.transactionType) != TransactionType.INCOME }
            val expTotal = expenses.sumAmount()
            val incTotal = incomes.sumAmount()
            com.resolveprogramming.pocketcounter.domain.model.ReportMonth(
                key = ym.key(),
                label = ym.month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.').lowercase(ptBr),
                exp = expTotal,
                inc = incTotal,
                saldo = incTotal - expTotal,
                expGroups = buildGroups(expenses, TransactionType.EXPENSE, tagToContext, contextById, incomeTagById, sourceNames, sourceTags),
                incGroups = buildGroups(incomes, TransactionType.INCOME, tagToContext, contextById, incomeTagById, sourceNames, sourceTags),
            )
        }

        val keys = monthsList.map { it.key() }
        val totalExp = reportMonths.fold(BigDecimal.ZERO) { a, m -> a + m.exp }
        val totalInc = reportMonths.fold(BigDecimal.ZERO) { a, m -> a + m.inc }
        com.resolveprogramming.pocketcounter.domain.model.ReportData(
            period = period,
            months = reportMonths,
            expSeries = com.resolveprogramming.pocketcounter.domain.model.seriesFrom(keys, reportMonths) { it.expGroups },
            incSeries = com.resolveprogramming.pocketcounter.domain.model.seriesFrom(keys, reportMonths) { it.incGroups },
            kpis = com.resolveprogramming.pocketcounter.domain.model.ReportKpis(
                exp = totalExp,
                inc = totalInc,
                saldo = totalInc - totalExp,
                expAvg = totalExp.divide(BigDecimal(monthsList.size.coerceAtLeast(1)), 2, RoundingMode.HALF_UP),
                months = monthsList.size,
            ),
            rangeLabel = rangeLabel(period, monthsList),
        )
    }

    private fun rangeLabel(
        period: com.resolveprogramming.pocketcounter.domain.model.ReportPeriod,
        months: List<YearMonth>,
    ): String = when (period) {
        com.resolveprogramming.pocketcounter.domain.model.ReportPeriod.MES ->
            "${months.first().month.getDisplayName(TextStyle.FULL, ptBr)} ${months.first().year}"
        com.resolveprogramming.pocketcounter.domain.model.ReportPeriod.ANO -> "${months.first().year}"
        com.resolveprogramming.pocketcounter.domain.model.ReportPeriod.TRIMESTRE -> {
            val a = months.first().month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.')
            val b = months.last().month.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.')
            "$a – $b ${months.last().year}"
        }
    }

    private fun buildGroups(
        txs: List<TransactionDto>,
        kind: TransactionType,
        tagToContext: Map<String, String?>,
        contextById: Map<String, com.resolveprogramming.pocketcounter.domain.model.TagContext>,
        incomeTagById: Map<String, Tag>,
        sourceNames: Map<String, String>,
        sourceTags: Map<String, List<String>>,
    ): List<SummaryGroup> {
        val grand = txs.sumAmount()
        data class B(val name: String, val color: Long, val txs: MutableList<TransactionDto>)
        val buckets = LinkedHashMap<String, B>()
        for (tx in txs) {
            val (gid, name, color) = classify(tx, kind, tagToContext, contextById, incomeTagById, sourceNames, sourceTags)
            buckets.getOrPut(gid) { B(name, color, mutableListOf()) }.txs.add(tx)
        }
        return buckets.map { (gid, b) ->
            val total = b.txs.sumAmount()
            SummaryGroup(gid, b.name, b.color, total, ratio(total, grand), null, null, emptyList())
        }.sortedByDescending { it.total }
    }

    private suspend fun fetch(kind: TransactionType, ym: YearMonth): List<TransactionDto> {
        val ref = RemoteMappers.monthKeyToRef(ym.key())
        return if (kind == TransactionType.INCOME) transactionApi.getIncomes(ref) else transactionApi.getExpenses(ref)
    }

    private suspend fun groupTotals(
        kind: TransactionType,
        ym: YearMonth,
        tagToContext: Map<String, String?>,
        contextById: Map<String, com.resolveprogramming.pocketcounter.domain.model.TagContext>,
        incomeTagById: Map<String, Tag>,
        sourceNames: Map<String, String>,
        sourceTags: Map<String, List<String>>,
    ): Map<String, BigDecimal> {
        val totals = LinkedHashMap<String, BigDecimal>()
        for (tx in fetch(kind, ym)) {
            val (gid, _, _) = classify(tx, kind, tagToContext, contextById, incomeTagById, sourceNames, sourceTags)
            totals[gid] = (totals[gid] ?: BigDecimal.ZERO) + (tx.amount ?: BigDecimal.ZERO).abs()
        }
        return totals
    }

    private fun averageGroupTotals(perMonth: List<Map<String, BigDecimal>>): Map<String, BigDecimal> {
        val keys = perMonth.flatMap { it.keys }.toSet()
        return keys.associateWith { k ->
            perMonth.fold(BigDecimal.ZERO) { acc, m -> acc + (m[k] ?: BigDecimal.ZERO) }
                .divide(BigDecimal(perMonth.size.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)
        }
    }

    private fun classify(
        tx: TransactionDto,
        kind: TransactionType,
        tagToContext: Map<String, String?>,
        contextById: Map<String, com.resolveprogramming.pocketcounter.domain.model.TagContext>,
        incomeTagById: Map<String, Tag>,
        sourceNames: Map<String, String>,
        sourceTags: Map<String, List<String>>,
    ): Triple<String, String, Long> {
        return if (kind == TransactionType.EXPENSE) {
            // Resolve own-or-inherited tags, then bucket by the first tag's context.
            val tagId = effectiveTagIds(tx.tags?.mapNotNull { it.id }, sourceTags[tx.idSource].orEmpty())
                .firstOrNull()
            val ctxId = tagId?.let { tagToContext[it] }?.takeIf { it.isNotBlank() }
            val ctx = ctxId?.let { contextById[it] }
            if (ctx != null) Triple(ctx.id, ctx.name, ctx.color)
            else Triple(noContextId, "Sem categoria", noContextColor)
        } else {
            // Bucket by the effective kind=INCOME tag (categoria de renda), not by idSource.
            val ids = effectiveTagIds(tx.tags?.mapNotNull { it.id }, sourceTags[tx.idSource].orEmpty())
            val incomeTag = ids.firstNotNullOfOrNull { incomeTagById[it] }
            if (incomeTag != null) Triple(incomeTag.id, incomeTag.name, incomeTag.color ?: incomeFallbackColor)
            else Triple(noIncomeCategoryId, "Sem categoria", noContextColor)
        }
    }

    private fun drillTags(
        txs: List<TransactionDto>,
        kind: TransactionType,
        incomeTagById: Map<String, Tag>,
        sourceTags: Map<String, List<String>>,
        tagNameById: Map<String, String>,
    ): List<SummaryTag> = if (kind == TransactionType.EXPENSE) {
        val byTag = LinkedHashMap<String, BigDecimal>()
        for (tx in txs) {
            // Same effective-tag resolution as the bucket grouping, so an inheriting expense
            // drills under its source-default tag instead of "sem etiqueta".
            val tagId = effectiveTagIds(tx.tags?.mapNotNull { it.id }, sourceTags[tx.idSource].orEmpty())
                .firstOrNull()
            val name = tagId?.let { tagNameById[it] ?: tx.tags?.firstOrNull { t -> t.id == it }?.name }
                ?: "sem etiqueta"
            byTag[name] = (byTag[name] ?: BigDecimal.ZERO) + (tx.amount ?: BigDecimal.ZERO).abs()
        }
        byTag.map { SummaryTag(it.key, it.value) }.sortedByDescending { it.total }
    } else {
        val byTag = LinkedHashMap<String, BigDecimal>()
        for (tx in txs) {
            // Drill by the effective income category tag, mirroring the bucket grouping.
            val ids = effectiveTagIds(tx.tags?.mapNotNull { it.id }, sourceTags[tx.idSource].orEmpty())
            val name = ids.firstNotNullOfOrNull { incomeTagById[it]?.name } ?: "Sem categoria"
            byTag[name] = (byTag[name] ?: BigDecimal.ZERO) + (tx.amount ?: BigDecimal.ZERO).abs()
        }
        byTag.map { SummaryTag(it.key, it.value) }.sortedByDescending { it.total }
    }

    private fun List<TransactionDto>.sumAmount(): BigDecimal =
        fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.amount ?: BigDecimal.ZERO).abs() }

    private fun ratio(part: BigDecimal, whole: BigDecimal): Float =
        if (whole > BigDecimal.ZERO) part.divide(whole, 4, RoundingMode.HALF_UP).toFloat() else 0f

    private fun relativeDelta(current: BigDecimal, base: BigDecimal?): Float? =
        if (base != null && base > BigDecimal.ZERO) {
            current.subtract(base).divide(base, 4, RoundingMode.HALF_UP).toFloat()
        } else {
            null
        }

    private fun String.toYearMonth(): YearMonth {
        val (y, m) = split("-")
        return YearMonth.of(y.toInt(), m.toInt())
    }

    private fun YearMonth.key(): String = "%04d-%02d".format(year, monthValue)
}
