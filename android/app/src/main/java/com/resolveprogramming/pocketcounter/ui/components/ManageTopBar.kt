package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** Shared management-screen top bar: 36dp back square + title + optional 36dp "+" action. */
@Composable
fun ManageTopBar(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Box(Modifier.padding(start = 12.dp)) {
                Text(title, style = PocketTheme.typography.screenH1, color = PocketTheme.colors.text)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions?.invoke()
            if (onAdd != null) {
                SquareIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Adicionar",
                    onClick = onAdd,
                )
            }
        }
    }
}

@Composable
fun SquareIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text.takeIf { enabled } ?: PocketTheme.colors.text3,
        )
    }
}
