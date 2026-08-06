package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * The read-only category pill on a ledger row's secondary line: a colored dot plus the tag name.
 *
 * It takes neither `onClick` nor `Modifier` on purpose. On a one-tap-confirm card every tappable
 * element is a distinct commitment, so non-tappability is guaranteed by the API surface instead of
 * by remembering not to wrap it at each call site.
 */
@Composable
fun TagPill(name: String, color: Long?) {
    Row(
        modifier = Modifier
            .clip(PocketTheme.shapes.pill)
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.pill)
            .padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    PocketTheme.shapes.pill,
                ),
        )
        Text(
            text = name,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
