package com.resolveprogramming.pocketcounter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.home.components.BalanceHero
import com.resolveprogramming.pocketcounter.ui.home.components.FlashEffect
import com.resolveprogramming.pocketcounter.ui.home.components.HomeFab
import com.resolveprogramming.pocketcounter.ui.home.components.HomeListSection
import com.resolveprogramming.pocketcounter.ui.home.components.HomeQuickTiles
import com.resolveprogramming.pocketcounter.ui.home.components.MonthNavBar
import com.resolveprogramming.pocketcounter.ui.home.components.RevisarBanner
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import com.resolveprogramming.pocketcounter.ui.transacoes.TransacaoFormSheet
import java.time.LocalDate

@Composable
fun HomeScreen(
    onNotificationTap: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = remember { PocketToastState() }
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.consumeToast()
    }

    FlashEffect(state.flashId, state.flashNonce, reducedMotion, viewModel::consumeFlash)

    if (state.isLoading && state.shownItems.isEmpty() && state.isEmptyMonth.not()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            bottomBar = {
                PocketTabBar(
                    active = TabId.INICIO,
                    onNav = { tab ->
                        when (tab) {
                            TabId.TRANSACOES -> onNavigate("transacoes")
                            TabId.CARTOES -> onNavigate("cartoes")
                            TabId.MAIS -> onNavigate("mais")
                            TabId.INICIO -> Unit
                        }
                    },
                )
            },
            floatingActionButton = { HomeFab(onClick = viewModel::openAdd) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                item { HeaderSection(state.userName, onAssistant = { onNavigate("assistente") }) }

                item {
                    MonthNavBar(
                        monthLabel = state.monthLabel,
                        isCurrentMonth = state.isCurrentMonth,
                        onStep = viewModel::selectMonth,
                    )
                }

                item {
                    BalanceHero(
                        monthLabel = state.monthLabel,
                        kpis = state.kpis,
                        balance = state.balance,
                        automationPct = state.automationPct,
                    )
                }

                if (state.isCurrentMonth && state.pendingReviewCount > 0) {
                    item {
                        RevisarBanner(
                            count = state.pendingReviewCount,
                            onClick = { onNavigate("transacoes") },
                        )
                    }
                }

                item {
                    HomeQuickTiles(
                        monthLabel = state.monthLabel,
                        openBillsTotal = state.openBillsTotal,
                        openBillsCount = state.openBillsCount,
                        onResumo = { onNavigate("resumo") },
                        onFaturas = { onNavigate("cartoes") },
                    )
                }

                item {
                    HomeListSection(
                        state = state,
                        onSelectType = viewModel::setListType,
                        onSelectGroup = viewModel::setGroupBy,
                        onToggleStatus = viewModel::toggleStatus,
                        onEdit = viewModel::openEdit,
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        PocketToastHost(state = toastState)
    }

    val formMode = state.formMode
    if (formMode != null) {
        val today = LocalDate.now()
        val defaultDate = today.takeIf { state.isCurrentMonth } ?: state.month.atDay(1)
        TransacaoFormSheet(
            mode = formMode,
            initialItem = (formMode as? FormMode.Edit)?.let { edit ->
                state.shownItems.firstOrNull { it.id == edit.itemId }
            },
            cards = state.cards.values.toList(),
            tags = state.tags.values.toList(),
            contexts = state.contexts,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeForm,
            defaultDate = defaultDate,
        )
    }
}

@Composable
private fun HeaderSection(userName: String, onAssistant: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Olá",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = userName.ifBlank { "Bem-vindo" },
                style = PocketTheme.typography.body.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = PocketTheme.colors.text,
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onAssistant),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(PocketTheme.shapes.labelPicker)
                    .background(PocketTheme.colors.surface)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.labelPicker),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = PocketTheme.colors.accent)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PocketTheme.colors.accent, PocketTheme.colors.accent2),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "?",
                style = PocketTheme.typography.button,
                color = PocketTheme.colors.accentInk,
            )
        }
    }
}
