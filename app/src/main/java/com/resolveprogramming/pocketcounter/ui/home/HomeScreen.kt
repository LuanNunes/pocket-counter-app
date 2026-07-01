package com.resolveprogramming.pocketcounter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.navigation.Routes
import com.resolveprogramming.pocketcounter.platform.capture.CapturePermissions
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.home.components.BalanceHero
import com.resolveprogramming.pocketcounter.ui.home.components.ConfirmReadyCard
import com.resolveprogramming.pocketcounter.ui.home.components.FlashEffect
import com.resolveprogramming.pocketcounter.ui.home.components.HomeQuickTiles
import com.resolveprogramming.pocketcounter.ui.home.components.MonthNavBar
import com.resolveprogramming.pocketcounter.ui.home.components.NotificationAccessBanner
import com.resolveprogramming.pocketcounter.ui.home.components.RevisarBanner
import com.resolveprogramming.pocketcounter.ui.home.components.SwipeCue
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    padding: PaddingValues,
    onOpenTransacoes: () -> Unit,
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

    // Notification-listener access can be revoked at any time (Auto Blocker, reinstall, the user).
    // Re-read it on every resume so the "capture off" banner appears/disappears without a restart.
    val context = LocalContext.current
    var notificationAccessGranted by remember {
        mutableStateOf(CapturePermissions.isNotificationAccessGranted(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationAccessGranted = CapturePermissions.isNotificationAccessGranted(context)
    }

    // init already loads; only refresh on subsequent resumes (e.g. returning from the wizard).
    // rememberSaveable so the flag survives this destination being disposed during navigation —
    // a plain remember resets to true on return and would suppress the post-wizard refresh.
    val isFirstResume = rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (isFirstResume.value) {
            isFirstResume.value = false
            return@LifecycleEventEffect
        }
        viewModel.refresh()
    }

    if (state.isLoading && state.shownItems.isEmpty() && state.isEmptyMonth.not()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::onManualRefresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isRefreshing,
                    color = PocketTheme.colors.accent,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                item {
                    HeaderSection(
                        state.userName,
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::onManualRefresh,
                        onAssistant = { onNavigate("assistente") },
                    )
                }

                item {
                    MonthNavBar(
                        monthLabel = state.monthLabel,
                        isCurrentMonth = state.isCurrentMonth,
                        onStep = viewModel::selectMonth,
                    )
                }

                if (!notificationAccessGranted) {
                    item {
                        NotificationAccessBanner(
                            onEnable = {
                                context.startActivity(CapturePermissions.notificationAccessSettingsIntent())
                            },
                        )
                    }
                }

                item {
                    BalanceHero(
                        monthLabel = state.monthLabel,
                        kpis = state.kpis,
                        balance = state.balance,
                    )
                }

                if (state.isCurrentMonth && state.confirmReady.isNotEmpty()) {
                    items(state.confirmReady, key = { it.notificationId }) { item ->
                        ConfirmReadyCard(
                            item = item,
                            isConfirming = item.notificationId in state.confirmingIds,
                            tagName = { id -> state.tags[id]?.name },
                            cardName = { id -> state.cards[id]?.name },
                            onConfirm = { viewModel.confirm(item) },
                            onReview = { onNavigate(Routes.wizard(item.notificationId)) },
                        )
                    }
                }

                if (state.isCurrentMonth && state.pendingReviewCount > 0) {
                    item {
                        RevisarBanner(
                            count = state.pendingReviewCount,
                            onClick = {
                                state.pendingReviewFirstId?.let { onNavigate(Routes.wizard(it)) }
                            },
                        )
                    }
                }

                item {
                    HomeQuickTiles(
                        openBillsTotal = state.openBillsTotal,
                        openBillsCount = state.openBillsCount,
                        onResumo = { onNavigate("resumo") },
                        onFaturas = { onNavigate("cartoes") },
                    )
                }

                item {
                    SwipeCue(
                        count = state.monthCount,
                        onClick = onOpenTransacoes,
                    )
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }

        PocketToastHost(state = toastState)
    }
}

@Composable
private fun HeaderSection(
    userName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAssistant: () -> Unit = {},
) {
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
                .minimumInteractiveComponentSize(),
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
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = if (isRefreshing) "Atualizando" else "Atualizar",
                        modifier = Modifier.size(20.dp),
                        // Dim while a refresh is in flight so the disabled icon reads as busy.
                        tint = PocketTheme.colors.accent.copy(alpha = if (isRefreshing) 0.4f else 1f),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
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
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Assistente",
                    modifier = Modifier.size(20.dp),
                    tint = PocketTheme.colors.accent,
                )
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
