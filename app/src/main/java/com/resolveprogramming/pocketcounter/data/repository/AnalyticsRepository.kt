package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.CompareOption
import com.resolveprogramming.pocketcounter.domain.model.MonthlySummary
import com.resolveprogramming.pocketcounter.domain.model.ReportData
import com.resolveprogramming.pocketcounter.domain.model.ReportPeriod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType

interface AnalyticsRepository {
    suspend fun compareOptions(monthKey: String): Result<List<CompareOption>>
    suspend fun summary(
        monthKey: String,
        kind: TransactionType,
        compareKey: String?,
    ): Result<MonthlySummary>

    /** Multi-month aggregation (Mês = 1 month, Trimestre = 3, Ano = 12) anchored at a month. */
    suspend fun report(period: ReportPeriod, anchorMonthKey: String): Result<ReportData>
}
