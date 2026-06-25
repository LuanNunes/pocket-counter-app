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
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.steps.StepTags

/** Edits a single transaction's tags. */
@Composable
fun TagEditSheet(
    type: TransactionType,
    initialTagIds: List<String>,
    tags: List<Tag>,
    contexts: List<TagContext>,
    onSave: (selectedTagIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialTagIds) }
    var search by remember { mutableStateOf("") }

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text("Tags", style = PocketTheme.typography.stepQuestion, color = PocketTheme.colors.text)
        Spacer(Modifier.height(12.dp))

        StepTags(
            type = type,
            tags = tags,
            contexts = contexts,
            selectedTagIds = selected,
            searchQuery = search,
            learnRule = false,
            paymentHint = null,
            merchant = null,
            onSearchChange = { search = it },
            onToggleTag = { id -> selected = (selected - id).takeIf { id in selected } ?: (selected + id) },
            onToggleLearnRule = { },
            showLearnToggle = false,
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
                onClick = { onSave(selected) },
                fillMaxWidth = true,
                modifier = Modifier.weight(1.5f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
