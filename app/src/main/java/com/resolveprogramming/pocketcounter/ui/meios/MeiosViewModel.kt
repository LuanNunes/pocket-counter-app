package com.resolveprogramming.pocketcounter.ui.meios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceInput
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.PaymentSourceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/** Add vs edit for the Meio form sheet. */
sealed interface MeioFormMode {
    data object Add : MeioFormMode
    data class Edit(val id: String) : MeioFormMode
}

/** A delete request resolved against references; [blocked] = cannot delete. */
data class MeioDeleteTarget(
    val id: String,
    val name: String,
    val blocked: Boolean,
    val sourceCount: Int,
    val txCount: Int,
)

data class MeiosUiState(
    val creditMeios: List<PaymentSource> = emptyList(),
    val checkingMeios: List<PaymentSource> = emptyList(),
    val formMode: MeioFormMode? = null,
    val editing: PaymentSource? = null,
    val confirmDelete: MeioDeleteTarget? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class MeiosViewModel @Inject constructor(
    private val paymentSourceRepository: PaymentSourceRepository,
    private val sourceRepository: SourceRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MeiosUiState())
    val state: StateFlow<MeiosUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            paymentSourceRepository.getAll()
                .onSuccess { all ->
                    _state.update {
                        it.copy(
                            creditMeios = all.filter { m -> m.kind == PaymentSourceKind.CREDIT },
                            checkingMeios = all.filter { m -> m.kind == PaymentSourceKind.CHECKING },
                            isLoading = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun openAdd() = _state.update { it.copy(formMode = MeioFormMode.Add, editing = null) }

    fun openEdit(id: String) {
        val target = (_state.value.creditMeios + _state.value.checkingMeios).firstOrNull { it.id == id }
        _state.update { it.copy(formMode = MeioFormMode.Edit(id), editing = target) }
    }

    fun closeForm() = _state.update { it.copy(formMode = null, editing = null) }

    fun save(input: PaymentSourceInput) {
        val mode = _state.value.formMode ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is MeioFormMode.Add -> paymentSourceRepository.create(input)
                is MeioFormMode.Edit -> paymentSourceRepository.update(mode.id, input)
            }
            result
                .onSuccess {
                    _state.update { it.copy(formMode = null, editing = null, toastMessage = "Meio salvo") }
                    load()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar") } }
        }
    }

    /** Pre-check references (current-month fast path); the actual delete also catches the backend RESTRICT. */
    fun requestDelete(id: String) {
        viewModelScope.launch {
            val name = (_state.value.creditMeios + _state.value.checkingMeios).firstOrNull { it.id == id }?.name.orEmpty()
            val sourceCount = sourceRepository.getAll().getOrDefault(emptyList())
                .count { it.idPaymentSource == id }
            val txCount = transactionRepository.getMonth(YearMonth.now().toString()).getOrDefault(emptyList())
                .count { it.idPaymentSource == id }
            _state.update {
                it.copy(
                    confirmDelete = MeioDeleteTarget(
                        id = id,
                        name = name,
                        blocked = sourceCount > 0 || txCount > 0,
                        sourceCount = sourceCount,
                        txCount = txCount,
                    ),
                )
            }
        }
    }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val target = _state.value.confirmDelete ?: return
        if (target.blocked) return
        viewModelScope.launch {
            paymentSourceRepository.delete(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDelete = null, toastMessage = "Meio excluído") }
                    load()
                }
                .onFailure {
                    // Fallback: an older-month transaction still references it (backend RESTRICT).
                    _state.update {
                        it.copy(confirmDelete = null, toastMessage = "Não foi possível excluir: há vínculos")
                    }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
