package com.resolveprogramming.pocketcounter.ui.contextos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.ContextInput
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A context (or the null "Sem contexto" bucket) with its tags. */
data class ContextSection(val context: TagContext?, val tags: List<Tag>) {
    val title: String get() = context?.name ?: "Sem contexto"
}

sealed interface ContextFormMode {
    data object Add : ContextFormMode
    data class Edit(val id: String) : ContextFormMode
}

sealed interface TagFormMode {
    data class Add(val idContext: String) : TagFormMode
    data class Edit(val id: String) : TagFormMode
}

data class ContextDeleteTarget(val id: String, val name: String, val tagCount: Int)
data class TagDeleteTarget(val id: String, val name: String)

data class ContextosTagsUiState(
    val sections: List<ContextSection> = emptyList(),
    val contexts: List<TagContext> = emptyList(),
    val palette: List<Long> = CuratedPalette.argb,
    val contextForm: ContextFormMode? = null,
    val editingContext: TagContext? = null,
    val tagForm: TagFormMode? = null,
    val editingTag: Tag? = null,
    val confirmDeleteContext: ContextDeleteTarget? = null,
    val confirmDeleteTag: TagDeleteTarget? = null,
    val toastMessage: String? = null,
    val isLoading: Boolean = true,
)

/** Groups tags under their context (in context order), with a trailing "Sem contexto" bucket. Pure for testability. */
internal fun buildContextSections(contexts: List<TagContext>, tags: List<Tag>): List<ContextSection> {
    val byContext = tags.groupBy { it.idContext }
    val knownIds = contexts.map { it.id }.toSet()
    return buildList {
        contexts.forEach { ctx -> add(ContextSection(ctx, byContext[ctx.id].orEmpty())) }
        val orphans = tags.filter { it.idContext.isBlank() || it.idContext !in knownIds }
        if (orphans.isNotEmpty()) add(ContextSection(null, orphans))
    }
}

@HiltViewModel
class ContextosTagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContextosTagsUiState())
    val state: StateFlow<ContextosTagsUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val contextsResult = tagRepository.getAllContexts()
            val tags = tagRepository.getAllTags().getOrDefault(emptyList())
            val contexts = contextsResult.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    sections = buildContextSections(contexts, tags),
                    contexts = contexts,
                    isLoading = false,
                    // Distinguish a load failure from a genuinely empty list.
                    toastMessage = if (contextsResult.isFailure) "Não foi possível carregar" else it.toastMessage,
                )
            }
        }
    }

    // ── Context CRUD ─────────────────────────────────────────────
    fun openAddContext() = _state.update { it.copy(contextForm = ContextFormMode.Add, editingContext = null) }

    fun openEditContext(id: String) {
        val ctx = _state.value.contexts.firstOrNull { it.id == id }
        _state.update { it.copy(contextForm = ContextFormMode.Edit(id), editingContext = ctx) }
    }

    fun closeContextForm() = _state.update { it.copy(contextForm = null, editingContext = null) }

    fun saveContext(input: ContextInput) {
        val mode = _state.value.contextForm ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is ContextFormMode.Add -> tagRepository.createContext(input)
                is ContextFormMode.Edit -> tagRepository.updateContext(mode.id, input)
            }
            result
                .onSuccess {
                    _state.update { it.copy(contextForm = null, editingContext = null, toastMessage = "Contexto salvo") }
                    load()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar (nome já existe?)") } }
        }
    }

    fun requestDeleteContext(id: String) {
        val section = _state.value.sections.firstOrNull { it.context?.id == id } ?: return
        _state.update {
            it.copy(
                confirmDeleteContext = ContextDeleteTarget(id, section.context?.name.orEmpty(), section.tags.size),
            )
        }
    }

    fun cancelDeleteContext() = _state.update { it.copy(confirmDeleteContext = null) }

    fun confirmDeleteContext() {
        val target = _state.value.confirmDeleteContext ?: return
        viewModelScope.launch {
            tagRepository.deleteContext(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDeleteContext = null, toastMessage = "Contexto excluído") }
                    load()
                }
                .onFailure {
                    _state.update { it.copy(confirmDeleteContext = null, toastMessage = "Não foi possível excluir") }
                }
        }
    }

    // ── Tag CRUD ─────────────────────────────────────────────────
    fun openAddTag(idContext: String) = _state.update { it.copy(tagForm = TagFormMode.Add(idContext), editingTag = null) }

    fun openEditTag(id: String) {
        val tag = _state.value.sections.flatMap { it.tags }.firstOrNull { it.id == id }
        _state.update { it.copy(tagForm = TagFormMode.Edit(id), editingTag = tag) }
    }

    fun closeTagForm() = _state.update { it.copy(tagForm = null, editingTag = null) }

    fun saveTag(input: TagInput) {
        val mode = _state.value.tagForm ?: return
        viewModelScope.launch {
            val result = when (mode) {
                is TagFormMode.Add -> tagRepository.createTag(input.name, input.idContext)
                is TagFormMode.Edit -> tagRepository.updateTag(mode.id, input)
            }
            result
                .onSuccess {
                    _state.update { it.copy(tagForm = null, editingTag = null, toastMessage = "Tag salva") }
                    load()
                }
                .onFailure { _state.update { it.copy(toastMessage = "Não foi possível salvar (nome já existe?)") } }
        }
    }

    fun requestDeleteTag(id: String) {
        val tag = _state.value.sections.flatMap { it.tags }.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(confirmDeleteTag = TagDeleteTarget(id, tag.name)) }
    }

    fun cancelDeleteTag() = _state.update { it.copy(confirmDeleteTag = null) }

    fun confirmDeleteTag() {
        val target = _state.value.confirmDeleteTag ?: return
        viewModelScope.launch {
            tagRepository.deleteTag(target.id)
                .onSuccess {
                    _state.update { it.copy(confirmDeleteTag = null, toastMessage = "Tag excluída") }
                    load()
                }
                .onFailure {
                    _state.update { it.copy(confirmDeleteTag = null, toastMessage = "Não foi possível excluir") }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }
}
