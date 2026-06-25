package com.resolveprogramming.pocketcounter.ui.contextos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.data.repository.TagInput
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.ColorSwatchPicker
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFormSheet(
    mode: TagFormMode,
    editing: Tag?,
    contexts: List<TagContext>,
    palette: List<Long>,
    onSave: (TagInput) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isIncome = when (mode) {
        is TagFormMode.AddIncome -> true
        is TagFormMode.Add -> false
        is TagFormMode.Edit -> editing?.kind == TransactionType.INCOME
    }

    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var contextId by remember {
        mutableStateOf(editing?.idContext ?: (mode as? TagFormMode.Add)?.idContext.orEmpty())
    }
    var color by remember { mutableLongStateOf(editing?.color ?: palette.first()) }
    val canSave = run {
        if (isIncome) return@run name.isNotBlank()
        name.isNotBlank() && contextId.isNotBlank()
    }

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = run {
                if (mode is TagFormMode.Edit && isIncome) return@run "Editar categoria"
                if (mode is TagFormMode.Edit) return@run "Editar tag"
                if (isIncome) return@run "Nova categoria"
                "Nova tag"
            },
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(16.dp))

        FormLabel("Nome")
        Spacer(Modifier.height(8.dp))
        FormTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Ex: Salário".takeIf { isIncome } ?: "Ex: supermercado",
        )
        Spacer(Modifier.height(16.dp))

        if (isIncome) {
            FormLabel("Cor")
            Spacer(Modifier.height(8.dp))
            ColorSwatchPicker(colors = palette, selected = color, onSelect = { color = it })
        }
        if (!isIncome) {
            FormLabel("Contexto")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contexts.forEach { ctx ->
                    val selected = ctx.id == contextId
                    Row(
                        modifier = Modifier
                            .border(
                                1.dp,
                                PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.line,
                                PocketTheme.shapes.pill,
                            )
                            .background(
                                PocketTheme.colors.accentBg.takeIf { selected } ?: PocketTheme.colors.surface2,
                                PocketTheme.shapes.pill,
                            )
                            .clickable { contextId = ctx.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(Color(ctx.color), PocketTheme.shapes.pill))
                        Text(
                            ctx.name,
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text.takeIf { selected } ?: PocketTheme.colors.text2,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PocketButton(
                text = "Cancelar",
                onClick = onDismiss,
                variant = PocketButtonVariant.SOFT,
                fillMaxWidth = true,
                modifier = Modifier.weight(1f),
            )
            PocketButton(
                text = "Salvar",
                onClick = {
                    val input = run {
                        if (isIncome) {
                            return@run TagInput(name.trim(), kind = TransactionType.INCOME, idContext = null, color = color)
                        }
                        TagInput(name.trim(), kind = TransactionType.EXPENSE, idContext = contextId)
                    }
                    onSave(input)
                },
                enabled = canSave,
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        if (mode is TagFormMode.Edit) {
            Spacer(Modifier.height(8.dp))
            PocketButton(
                text = "Excluir categoria".takeIf { isIncome } ?: "Excluir tag",
                onClick = onDelete,
                variant = PocketButtonVariant.GHOST,
                fillMaxWidth = true,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
