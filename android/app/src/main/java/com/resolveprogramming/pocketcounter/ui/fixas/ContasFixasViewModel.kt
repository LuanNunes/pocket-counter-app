package com.resolveprogramming.pocketcounter.ui.fixas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContasFixasUiState(
    val series: List<Series> = emptyList(),
    val tagsById: Map<String, Tag> = emptyMap(),
    val renameTarget: Series? = null,
    val confirmDeleteId: String? = null,
    val tagsTarget: Series? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ContasFixasViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContasFixasUiState())
    val state: StateFlow<ContasFixasUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            // The backend doesn't return a series' tags on RecurringSeriesDto; series ownership
            // lives on the tag side (Tag.idSeries). Enrich each series with the ids of the tags
            // that point at it so the row chips and the tag sheet pre-selection work.
            val tagIdsBySeries = tags.filter { it.idSeries != null }.groupBy { it.idSeries }
            seriesRepository.getAll()
                .onSuccess { series ->
                    _state.update {
                        it.copy(
                            series = series.map { s ->
                                s.copy(tagIds = tagIdsBySeries[s.id]?.map { t -> t.id } ?: emptyList())
                            },
                            tagsById = tags.associateBy { t -> t.id },
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun openRename(series: Series) = _state.update { it.copy(renameTarget = series) }
    fun cancelRename() = _state.update { it.copy(renameTarget = null) }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            seriesRepository.rename(id, name)
                .onSuccess {
                    _state.update { it.copy(renameTarget = null, toastMessage = "Conta renomeada") }
                    load()
                }
                .onFailure { _state.update { it.copy(renameTarget = null, toastMessage = "Não foi possível renomear") } }
        }
    }

    fun requestDelete(id: String) = _state.update { it.copy(confirmDeleteId = id) }
    fun cancelDelete() = _state.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.confirmDeleteId ?: return
        viewModelScope.launch {
            seriesRepository.delete(id)
                .onSuccess {
                    _state.update { it.copy(confirmDeleteId = null, toastMessage = "Conta fixa excluída") }
                    load()
                }
                .onFailure { _state.update { it.copy(confirmDeleteId = null, toastMessage = "Não foi possível excluir") } }
        }
    }

    fun openTags(series: Series) = _state.update { it.copy(tagsTarget = series) }
    fun closeTags() = _state.update { it.copy(tagsTarget = null) }

    fun saveTags(id: String, tagIds: List<String>) {
        viewModelScope.launch {
            seriesRepository.setTags(id, tagIds)
                .onSuccess {
                    _state.update { it.copy(tagsTarget = null, toastMessage = "Tags atualizadas") }
                    load()
                }
                .onFailure { _state.update { it.copy(tagsTarget = null, toastMessage = "Não foi possível salvar as tags") } }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
