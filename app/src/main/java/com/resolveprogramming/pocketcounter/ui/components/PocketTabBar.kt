package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

enum class TabId { INICIO, TRANSACOES, CARTOES, MAIS }

@Composable
fun PocketTabBar(
    active: TabId,
    onNav: (TabId) -> Unit,
) {
    data class TabSpec(val id: TabId, val label: String, val icon: ImageVector)

    val tabs = listOf(
        TabSpec(TabId.INICIO, "Início", Icons.Filled.Home),
        TabSpec(TabId.TRANSACOES, "Transações", Icons.AutoMirrored.Filled.ReceiptLong),
        TabSpec(TabId.CARTOES, "Cartões", Icons.Filled.CreditCard),
        TabSpec(TabId.MAIS, "Mais", Icons.Filled.MoreHoriz),
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
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = PocketTheme.colors.accent.takeIf { selected }
                            ?: PocketTheme.colors.text3,
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
