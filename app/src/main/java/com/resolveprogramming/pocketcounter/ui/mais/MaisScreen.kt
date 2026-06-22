package com.resolveprogramming.pocketcounter.ui.mais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import kotlinx.coroutines.launch

@Composable
fun MaisScreen(
    onNav: (TabId) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val toastState = remember { PocketToastState() }
    val scope = rememberCoroutineScope()
    fun soon() = scope.launch { toastState.show("Em breve") }

    data class Entry(val label: String, val sub: String, val glyph: String, val onClick: () -> Unit)

    val entries = listOf(
        Entry("Fontes", "Templates de receita e despesa", "◆") { onOpenRoute("fontes") },
        Entry("Contas Fixas", "Lançamentos que se repetem", "↻") { onOpenRoute("contas-fixas") },
        Entry("Meios de pagamento", "Cartões e contas", "▢") { onOpenRoute("meios") },
        Entry("Relatório", "Tendências por mês, trimestre, ano", "▦") { onOpenRoute("relatorio") },
        Entry("Regras aprendidas", "Classificação automática", "✦") { onOpenRoute("regras") },
        Entry("Contextos & Tags", "Organize suas análises", "#") { onOpenRoute("contextos") },
        Entry("Assistente", "Tire dúvidas sobre suas finanças", "✦") { onOpenRoute("assistente") },
        Entry("Configurações", "Tema, densidade, conta", "⚙") { soon() },
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            bottomBar = { PocketTabBar(active = TabId.MAIS, onNav = onNav) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "Mais",
                        style = PocketTheme.typography.screenH1,
                        color = PocketTheme.colors.text,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(entries) { entry ->
                    MaisRow(label = entry.label, sub = entry.sub, glyph = entry.glyph, onClick = entry.onClick)
                }
            }
        }
        PocketToastHost(state = toastState)
    }
}

@Composable
private fun MaisRow(label: String, sub: String, glyph: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = PocketTheme.colors.text2)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        }
        Text("›", color = PocketTheme.colors.text3)
    }
}
