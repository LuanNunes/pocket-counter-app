package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * Step 5 of the wizard (also reused by the manual form + tag-edit sheet). The header copy and
 * the "Aprender este padrão" learn toggle are wizard-specific; the body delegates to the shared
 * [TagPicker], which owns its own search + drill state.
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
    learnRule: Boolean,
    paymentHint: String?,
    merchant: String?,
    onSearchChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleLearnRule: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // Hidden for manual entry (Gestão), where "learn from notifications" is meaningless.
    showLearnToggle: Boolean = true,
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

        if (showLearnToggle) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aprender este padrão",
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    val hint = paymentHint ?: merchant ?: "..."
                    Text(
                        text = "Próximas notificações com \"$hint\" vão pré-preencher as tags automaticamente.",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text2,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = learnRule,
                    onCheckedChange = onToggleLearnRule,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PocketTheme.colors.accent,
                        checkedThumbColor = PocketTheme.colors.accentInk,
                    ),
                )
            }
        }
    }
}
