package com.resolveprogramming.pocketcounter.ui.relatorio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.ReportPrefsStore
import com.resolveprogramming.pocketcounter.data.repository.AnalyticsRepository
import com.resolveprogramming.pocketcounter.domain.model.ReportChartType
import com.resolveprogramming.pocketcounter.domain.model.ReportData
import com.resolveprogramming.pocketcounter.domain.model.ReportDetailMode
import com.resolveprogramming.pocketcounter.domain.model.ReportPeriod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class RelatorioUiState(
    val period: ReportPeriod = ReportPeriod.MES,
    val kind: TransactionType = TransactionType.EXPENSE,
    val anchorMonthKey: String,
    val chartType: ReportChartType = ReportChartType.BARS,
    val detailMode: ReportDetailMode = ReportDetailMode.CARTOES,
    val report: ReportData? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RelatorioViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val prefsStore: ReportPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RelatorioUiState(anchorMonthKey = YearMonth.now().toString()))
    val state: StateFlow<RelatorioUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { prefsStore.chartType.collect { c -> _state.update { it.copy(chartType = c) } } }
        viewModelScope.launch { prefsStore.detailMode.collect { m -> _state.update { it.copy(detailMode = m) } } }
    }

    fun setChartType(type: ReportChartType) {
        _state.update { it.copy(chartType = type) }
        viewModelScope.launch { prefsStore.setChartType(type) }
    }

    fun setDetailMode(mode: ReportDetailMode) {
        _state.update { it.copy(detailMode = mode) }
        viewModelScope.launch { prefsStore.setDetailMode(mode) }
    }

    fun setPeriod(period: ReportPeriod) {
        _state.update { it.copy(period = period) }
        load()
    }

    /** Kind is a pure display switch — the loaded report already carries both series. */
    fun setKind(kind: TransactionType) = _state.update { it.copy(kind = kind) }

    fun stepPeriod(delta: Int) {
        val anchor = YearMonth.parse(_state.value.anchorMonthKey)
        val next = when (_state.value.period) {
            ReportPeriod.MES -> anchor.plusMonths(delta.toLong())
            ReportPeriod.TRIMESTRE -> anchor.plusMonths(3L * delta)
            ReportPeriod.ANO -> anchor.plusYears(delta.toLong())
        }
        _state.update { it.copy(anchorMonthKey = next.toString()) }
        load()
    }

    private fun load() {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            analyticsRepository.report(s.period, s.anchorMonthKey)
                .onSuccess { report -> _state.update { it.copy(report = report, isLoading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
