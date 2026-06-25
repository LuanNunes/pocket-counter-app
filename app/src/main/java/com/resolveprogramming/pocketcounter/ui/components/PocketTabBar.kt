package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

enum class TabId { INICIO, TRANSACOES, CARTOES, MAIS }

@Composable
fun PocketTabBar(
    active: TabId,
    onNav: (TabId) -> Unit,
) {
    data class TabSpec(val id: TabId, val label: String)

    val tabs = listOf(
        TabSpec(TabId.INICIO, "Início"),
        TabSpec(TabId.TRANSACOES, "Transações"),
        TabSpec(TabId.CARTOES, "Cartões"),
        TabSpec(TabId.MAIS, "Mais"),
    )

    NavigationBar(
        containerColor = PocketTheme.colors.surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == active
            NavigationBarItem(
                selected = selected,
                onClick = { onNav(tab.id) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                PocketTheme.colors.accent.takeIf { selected }
                                    ?: PocketTheme.colors.line,
                                PocketTheme.shapes.icon,
                            ),
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = PocketTheme.typography.bodyXs,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = PocketTheme.colors.accent,
                    unselectedTextColor = PocketTheme.colors.text3,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
