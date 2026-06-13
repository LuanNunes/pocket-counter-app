package com.resolveprogramming.pocketcounter.ui.resumo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.AnalyticsRepository
import com.resolveprogramming.pocketcounter.domain.model.CompareOption
import com.resolveprogramming.pocketcounter.domain.model.MonthlySummary
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResumoUiState(
    val kind: TransactionType = TransactionType.EXPENSE,
    val monthLabel: String = "",
    val compareKey: String? = null,
    val options: List<CompareOption> = emptyList(),
    val summary: MonthlySummary? = null,
    val showPrev: Boolean = false,
    val openGroupIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ResumoViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {

    private val today = java.time.LocalDate.now()
    private val monthKey = "%04d-%02d".format(today.year, today.monthValue)

    private val _state = MutableStateFlow(ResumoUiState(monthLabel = currentMonthLabel()))
    val state: StateFlow<ResumoUiState> = _state.asStateFlow()

    init {
        loadOptions()
    }

    private fun currentMonthLabel(): String {
        val ptBr = java.util.Locale("pt", "BR")
        val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, ptBr)
        return "$month ${today.year}"
    }

    private fun loadOptions() {
        viewModelScope.launch {
            analyticsRepository.compareOptions(monthKey)
                .onSuccess { options ->
                    // default compareKey = first non-avg3 option (previous month)
                    val defaultKey = options.firstOrNull { it.key != "avg3" }?.key
                        ?: options.firstOrNull()?.key
                    _state.update { it.copy(options = options, compareKey = defaultKey) }
                    loadSummary()
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun loadSummary() {
        viewModelScope.launch {
            val s = _state.value
            analyticsRepository.summary(monthKey, s.kind, s.compareKey)
                .onSuccess { summary ->
                    _state.update { it.copy(summary = summary, isLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    fun setKind(kind: TransactionType) {
        _state.update { it.copy(kind = kind, openGroupIds = emptySet()) }
        loadSummary()
    }

    fun setCompareKey(key: String) {
        _state.update { it.copy(compareKey = key) }
        loadSummary()
    }

    fun toggleShowPrev() {
        _state.update { it.copy(showPrev = !it.showPrev) }
    }

    fun toggleGroup(groupId: String) {
        _state.update { s ->
            val updated = if (groupId in s.openGroupIds) {
                s.openGroupIds - groupId
            } else {
                s.openGroupIds + groupId
            }
            s.copy(openGroupIds = updated)
        }
    }
}
