package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Wizard-only "Aprender este padrão" learn toggle. Pinned by the wizard host directly above the
 * footer on the TAGS step so it stays visible regardless of how long the tag list scrolls — it
 * applies to the whole classification, not to any single tag, and is a pre-save decision.
 */
@Composable
fun LearnPatternToggle(
    checked: Boolean,
    hint: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
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
            Text(
                text = "Próximas notificações com \"$hint\" vão pré-preencher as tags automaticamente.",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PocketTheme.colors.accent,
                checkedThumbColor = PocketTheme.colors.accentInk,
            ),
        )
    }
}
