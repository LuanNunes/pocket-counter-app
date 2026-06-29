package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceTextCard(
    notification: NotificationItem,
    tokens: List<Token>,
    selectionStart: Int?,
    selectionEnd: Int?,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildString {
                    append("SMS".takeIf { notification.channel == NotificationChannel.SMS } ?: "Push")
                    append(" • ")
                    append(notification.app.uppercase())
                    append(" • ")
                    append(notification.received)
                },
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = "toque para marcar",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.height(10.dp))

        val palette = TokenPalette(
            accent = PocketTheme.colors.accent,
            accentBg = PocketTheme.colors.accentBg,
            text2 = PocketTheme.colors.text2,
            text3 = PocketTheme.colors.text3,
            expense = PocketTheme.colors.expense,
            income = PocketTheme.colors.income,
            warn = PocketTheme.colors.warn,
            accent2 = PocketTheme.colors.accent2,
            defaultShape = PocketTheme.shapes.icon,
        )
        val selection = if (selectionStart != null && selectionEnd != null) {
            selectionStart..selectionEnd
        } else {
            null
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tokens.forEachIndexed { index, token ->
                val visual = tokenVisual(index, tokens, selection, palette)

                Text(
                    text = token.text,
                    style = PocketTheme.typography.monoSm.copy(
                        fontWeight = if (visual.emphasized) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = visual.textColor,
                    modifier = Modifier
                        .then(
                            visual.border
                                ?.let { Modifier.border(it.width, it.color, visual.shape) }
                                ?: Modifier,
                        )
                        .background(visual.fillColor, visual.shape)
                        .clickable { onTokenTap(index) }
                        .padding(horizontal = 5.dp, vertical = 5.dp)
                        .semantics {
                            contentDescription = token.text
                            if (visual.stateDesc.isNotEmpty()) stateDescription = visual.stateDesc
                        },
                )
            }
        }
    }
}
