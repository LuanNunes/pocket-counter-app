package com.resolveprogramming.pocketcounter.ui.contextos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.data.repository.ContextInput
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.ui.components.ColorSwatchPicker
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun ContextFormSheet(
    mode: ContextFormMode,
    editing: TagContext?,
    palette: List<Long>,
    onSave: (ContextInput) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(editing?.color ?: palette.first()) }
    val canSave = name.isNotBlank()

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = if (mode is ContextFormMode.Edit) "Editar contexto" else "Novo contexto",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(16.dp))

        FormLabel("Nome")
        Spacer(Modifier.height(8.dp))
        FormTextField(value = name, onValueChange = { name = it }, placeholder = "Ex: Alimentação")
        Spacer(Modifier.height(16.dp))

        FormLabel("Cor")
        Spacer(Modifier.height(8.dp))
        ColorSwatchPicker(colors = palette, selected = color, onSelect = { color = it })

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
                onClick = { onSave(ContextInput(name.trim(), color)) },
                enabled = canSave,
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        if (mode is ContextFormMode.Edit) {
            Spacer(Modifier.height(8.dp))
            PocketButton(
                text = "Excluir contexto",
                onClick = onDelete,
                variant = PocketButtonVariant.GHOST,
                fillMaxWidth = true,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
