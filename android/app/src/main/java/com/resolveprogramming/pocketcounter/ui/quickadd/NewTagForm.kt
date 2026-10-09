package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.NewTagRequest
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.isComplete
import com.resolveprogramming.pocketcounter.ui.components.ColorSwatchPicker
import com.resolveprogramming.pocketcounter.ui.components.FormErrorNote
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.ctaSpinner
import com.resolveprogramming.pocketcounter.ui.contextos.CuratedPalette
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

private const val SWATCH_COUNT = 8

/**
 * `.qa-new`: names a tag and picks the category it goes under, without leaving the summary row.
 * Categories are made in Contextos & Tags, never here. Its fields are local: closing discards them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTagForm(
    type: TransactionType,
    contexts: List<TagContext>,
    isSaving: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onCreate: (NewTagRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialContextId: String? = null,
) {
    val palette = CuratedPalette.argb.take(SWATCH_COUNT)
    val isIncome = type == TransactionType.INCOME

    var name by remember { mutableStateOf("") }
    var contextId by remember { mutableStateOf(initialContextId ?: contexts.firstOrNull()?.id) }
    var color by remember { mutableLongStateOf(palette[contexts.size % palette.size]) }

    val request = NewTagRequest(name = name, color = color, contextId = contextId)
    val canCreate = request.isComplete(type) && !isSaving
    val submit = {
        if (canCreate) onCreate(request)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.cta)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.cta)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        NewTagInput(
            value = name,
            onValueChange = { name = it },
            placeholder = "Nome da categoria de receita".takeIf { isIncome } ?: "Nome da tag",
            onSubmit = submit,
            autoFocus = true,
        )

        if (!isIncome) {
            NewTagLabel("Categoria")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                contexts.forEach { context ->
                    QuickPick(
                        label = context.name,
                        selected = contextId == context.id,
                        onClick = { contextId = context.id },
                        dotColor = Color(context.color),
                    )
                }
            }
        }

        // An income tag has no category to take a colour from, so it carries its own.
        if (isIncome) {
            ColorSwatchPicker(colors = palette, selected = color, onSelect = { color = it })
        }

        errorMessage?.let { message -> FormErrorNote(message) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            PocketButton(
                text = "Cancelar",
                onClick = onCancel,
                variant = PocketButtonVariant.GHOST,
            )
            PocketButton(
                text = "Criar e aplicar",
                onClick = submit,
                enabled = canCreate,
                leading = ctaSpinner().takeIf { isSaving },
            )
        }
    }
}

/** `.qa-new-lbl`. */
@Composable
private fun NewTagLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = PocketTheme.typography.sectionHeader,
        color = PocketTheme.colors.text3,
    )
}

/** `.qa-new-in`: the field the handoff gives this form — 42dp, 12dp radius, accent edge on focus. */
@Composable
private fun NewTagInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    autoFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            // Throws only while the node is still unattached, where there is nothing to focus.
            runCatching { focusRequester.requestFocus() }
        }
    }
    val style = PocketTheme.typography.body.copy(fontSize = 15.sp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = PocketTheme.colors.text),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        cursorBrush = SolidColor(PocketTheme.colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(
                        1.dp,
                        PocketTheme.colors.accent.takeIf { focused } ?: PocketTheme.colors.line,
                        PocketTheme.shapes.chip,
                    )
                    .background(PocketTheme.colors.bg, PocketTheme.shapes.chip)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style,
                        color = PocketTheme.colors.text3,
                    )
                }
                inner()
            }
        },
    )
}
