package com.resolveprogramming.pocketcounter.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.transacoes.TransacoesContent
import kotlinx.coroutines.launch

/**
 * Hosts Home (page 0) and Transações (page 1) as a native [HorizontalPager]. The shared
 * [PocketTabBar] lives on the single parent [Scaffold]; its active item, Home's swipe cue,
 * and the Transações back button all derive from / drive [pagerState] so the tab, gesture,
 * and affordances stay in sync.
 */
@Composable
fun HomePager(
    initialPage: Int,
    onNavTab: (TabId) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = PocketTheme.colors.bg,
        bottomBar = {
            PocketTabBar(
                active = TabId.INICIO.takeIf { pagerState.currentPage == 0 } ?: TabId.TRANSACOES,
                onNav = { tab ->
                    when (tab) {
                        TabId.INICIO -> scope.launch { pagerState.animateScrollToPage(0) }
                        TabId.TRANSACOES -> scope.launch { pagerState.animateScrollToPage(1) }
                        else -> onNavTab(tab)
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> HomeContent(
                    padding = padding,
                    onOpenTransacoes = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onNavigate = onNavigate,
                )

                else -> TransacoesContent(
                    padding = padding,
                    onBack = {
                        if (pagerState.currentPage == 1) {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                    },
                )
            }
        }
    }
}
