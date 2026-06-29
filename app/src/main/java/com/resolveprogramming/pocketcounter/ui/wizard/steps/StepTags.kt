package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.TagPicker
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Step 5 of the wizard (also reused by the manual form + tag-edit sheet). The header copy is
 * wizard-specific; the body delegates to the shared [TagPicker], which owns its own search +
 * drill state. The wizard's "Aprender este padrão" learn toggle is pinned by the host above the
 * footer (see [com.resolveprogramming.pocketcounter.ui.wizard.LearnPatternToggle]), not here.
 *
 * [searchQuery]/[onSearchChange] are kept for call-site compatibility — the redesigned picker
 * manages search internally, so they are no longer consumed here.
 */
@Composable
fun StepTags(
    type: TransactionType,
    tags: List<Tag>,
    contexts: List<TagContext>,
    selectedTagIds: List<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append("Quer adicionar ")
                withStyle(SpanStyle(color = PocketTheme.colors.accent, fontWeight = FontWeight.Bold)) {
                    append("tags")
                }
                append("?")
            },
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = buildAnnotatedString {
                append("Tags ajudam você a agrupar transações nas análises. Esse passo é ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("opcional") }
                append(" — pode pular.")
            },
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text2,
        )

        Spacer(Modifier.height(16.dp))

        TagPicker(
            type = type,
            tags = tags,
            contexts = contexts,
            selectedTagIds = selectedTagIds,
            onToggleTag = onToggleTag,
        )
    }
}
