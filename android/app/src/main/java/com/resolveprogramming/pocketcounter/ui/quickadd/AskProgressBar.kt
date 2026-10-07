package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** One segment per question, filled up to and including the current one. The label above says it too. */
@Composable
fun AskProgressBar(total: Int, currentIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val color = PocketTheme.colors.accent.takeIf { index <= currentIndex }
                ?: PocketTheme.colors.line
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(color, PocketTheme.shapes.pill),
            )
        }
    }
}
