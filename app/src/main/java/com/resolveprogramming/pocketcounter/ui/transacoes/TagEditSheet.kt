package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.ui.components.FormSwitchRow
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags

/**
 * Edits a transaction's tags. By default the change updates the SOURCE's defaults (so every
 * still-inheriting transaction reflects it); the switch lets the user scope it to this one.
 */
@Composable
fun TagEditSheet(
    initialTagIds: List<String>,
    inheriting: Boolean,
    sourceName: String,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onSave: (selectedTagIds: List<String>, updateSource: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialTagIds) }
    var updateSource by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Tags", style = PocketTheme.typography.stepQuestion, color = PocketTheme.colors.text)
            if (inheriting) {
                PocketBadge(text = "herda da fonte", variant = PocketBadgeVariant.SOFT)
            }
        }
        Spacer(Modifier.height(12.dp))

        StepTags(
            tags = tags,
            contexts = contexts,
            selectedTagIds = selected,
            searchQuery = search,
            learnRule = false,
            paymentHint = null,
            merchant = null,
            onSearchChange = { search = it },
            onToggleTag = { id -> selected = if (id in selected) selected - id else selected + id },
            onToggleLearnRule = { },
            showLearnToggle = false,
        )

        Spacer(Modifier.height(8.dp))
        FormSwitchRow(
            label = "Atualizar a fonte “$sourceName”",
            checked = updateSource,
            onCheckedChange = { updateSource = it },
        )
        Text(
            text = if (updateSource) {
                "Reflete nos lançamentos que herdam desta fonte."
            } else {
                "Aplica só a esta transação."
            },
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )

        Spacer(Modifier.height(16.dp))
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
                onClick = { onSave(selected, updateSource) },
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
