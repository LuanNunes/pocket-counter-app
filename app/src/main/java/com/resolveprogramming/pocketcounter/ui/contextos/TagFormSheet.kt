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
    onSave: (TagInput) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var contextId by remember {
        mutableStateOf(editing?.idContext ?: (mode as? TagFormMode.Add)?.idContext.orEmpty())
    }
    val canSave = name.isNotBlank() && contextId.isNotBlank()

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = if (mode is TagFormMode.Edit) "Editar tag" else "Nova tag",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(16.dp))

        FormLabel("Nome")
        Spacer(Modifier.height(8.dp))
        FormTextField(value = name, onValueChange = { name = it }, placeholder = "Ex: supermercado")
        Spacer(Modifier.height(16.dp))

        FormLabel("Contexto")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            contexts.forEach { ctx ->
                val selected = ctx.id == contextId
                Row(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) PocketTheme.colors.accent else PocketTheme.colors.line,
                            PocketTheme.shapes.pill,
                        )
                        .background(
                            if (selected) PocketTheme.colors.accentBg else PocketTheme.colors.surface2,
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
                        color = if (selected) PocketTheme.colors.text else PocketTheme.colors.text2,
                    )
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
                onClick = { onSave(TagInput(name.trim(), contextId)) },
                enabled = canSave,
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        if (mode is TagFormMode.Edit) {
            Spacer(Modifier.height(8.dp))
            PocketButton(
                text = "Excluir tag",
                onClick = onDelete,
                variant = PocketButtonVariant.GHOST,
                fillMaxWidth = true,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
