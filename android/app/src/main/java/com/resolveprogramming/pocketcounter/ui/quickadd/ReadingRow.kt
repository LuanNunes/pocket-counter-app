package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.FieldProvenance
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeSize
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** Holds the longest label, "Forma de Pagamento", on two lines at the row's 13sp. */
private val LABEL_COLUMN_WIDTH = 100.dp

/**
 * The badge word, in the sentence case TalkBack reads; the badge itself renders it uppercased.
 *
 * WRITTEN is deliberately unbadged: naming a value the user just typed states the obvious on the
 * common case, and the mechanism exists to mark what the server *guessed*.
 */
internal fun provenanceBadgeText(provenance: FieldProvenance): String? = when (provenance) {
    FieldProvenance.WRITTEN -> null
    FieldProvenance.INFERRED -> "assumido"
    FieldProvenance.DEFINED -> "definido"
    FieldProvenance.ABSENT -> null
}

internal fun provenanceBadgeVariant(provenance: FieldProvenance): PocketBadgeVariant? = when (provenance) {
    FieldProvenance.WRITTEN -> null
    FieldProvenance.INFERRED -> PocketBadgeVariant.WARN
    FieldProvenance.DEFINED -> PocketBadgeVariant.ACCENT
    FieldProvenance.ABSENT -> null
}

/** A badge read apart from its field says nothing, so the row announces all of it in one node. */
internal fun rowContentDescription(
    label: String,
    value: String,
    badge: String?,
    hint: String? = null,
): String {
    val badged = badge?.let { "$label: $value, $it" } ?: "$label: $value"
    hint ?: return "$badged."
    return "$badged. $hint."
}

/**
 * One preview row: a fixed label column, the value with its single provenance badge, and an inline
 * editor. It takes exactly one [provenance] so the Cartão and Pagamento rows cannot share a badge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingRow(
    label: String,
    value: String?,
    absentText: String,
    provenance: FieldProvenance,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    mono: Boolean = false,
    leadingIcon: ImageVector? = null,
    dotColor: Color? = null,
    hint: String? = null,
    editor: (@Composable () -> Unit)? = null,
    pinnedEditor: (@Composable () -> Unit)? = null,
) {
    val colors = PocketTheme.colors
    val shown = value ?: absentText
    val badge = provenanceBadgeText(provenance)
    val action = run {
        // A pinned editor has no toggle, so the prompt is all that marks the row as still open.
        onToggle ?: return@run "definir".takeIf { value == null && pinnedEditor != null }
        if (expanded) return@run "fechar"
        if (value == null) return@run "definir"
        "alterar"
    }
    val valueStyle = run {
        if (value == null) {
            return@run PocketTheme.typography.body.copy(
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
            )
        }
        if (mono) return@run PocketTheme.typography.monoSm.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(
                    run {
                        onToggle ?: return@run Modifier
                        Modifier.clickable(role = Role.Button, onClick = onToggle)
                    },
                )
                .then(run { if (expanded) return@run Modifier.background(colors.surface2); Modifier })
                .semantics(mergeDescendants = true) {
                    contentDescription = rowContentDescription(label, shown, badge, hint)
                    if (onToggle != null) {
                        stateDescription = "expandido".takeIf { expanded } ?: "recolhido"
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = PocketTheme.typography.bodySm,
                color = colors.text3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Fixed so every value aligns, two lines because "Forma de Pagamento" needs them.
                // The merged contentDescription carries the label in full at any font scale.
                modifier = Modifier.width(LABEL_COLUMN_WIDTH),
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dotColor != null) {
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                }
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.text3,
                    )
                }
                Text(
                    text = shown,
                    style = valueStyle,
                    color = colors.text2.takeIf { value == null } ?: colors.text,
                )
                val variant = provenanceBadgeVariant(provenance)
                if (badge != null && variant != null) {
                    PocketBadge(text = badge, variant = variant, size = PocketBadgeSize.MICRO)
                }
                if (hint != null) {
                    Text(
                        text = hint,
                        style = PocketTheme.typography.bodyXs,
                        color = colors.text3,
                    )
                }
            }
            if (action != null) {
                Text(
                    text = action,
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                    color = colors.accent,
                )
            }
        }
        if (pinnedEditor != null) {
            Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp)) { pinnedEditor() }
        }
        if (editor != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp)) { editor() }
            }
        }
    }
}
