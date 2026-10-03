package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resolveprogramming.pocketcounter.domain.model.ReportChartType
import com.resolveprogramming.pocketcounter.domain.model.ReportDetailMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reportDataStore by preferencesDataStore(name = "report_prefs")

/** Persists the Relatório chart-type + detail-mode selections (app prefs, per spec). */
@Singleton
class ReportPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val chartTypeKey = stringPreferencesKey("chart_type")
    private val detailModeKey = stringPreferencesKey("detail_mode")

    val chartType: Flow<ReportChartType> = context.reportDataStore.data.map { prefs ->
        prefs[chartTypeKey]?.let { runCatching { ReportChartType.valueOf(it) }.getOrNull() }
            ?: ReportChartType.BARS
    }

    val detailMode: Flow<ReportDetailMode> = context.reportDataStore.data.map { prefs ->
        prefs[detailModeKey]?.let { runCatching { ReportDetailMode.valueOf(it) }.getOrNull() }
            ?: ReportDetailMode.CARTOES
    }

    suspend fun setChartType(value: ReportChartType) {
        context.reportDataStore.edit { it[chartTypeKey] = value.name }
    }

    suspend fun setDetailMode(value: ReportDetailMode) {
        context.reportDataStore.edit { it[detailModeKey] = value.name }
    }
}
