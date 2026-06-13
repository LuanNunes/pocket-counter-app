package com.resolveprogramming.pocketcounter.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resolveprogramming.pocketcounter.data.local.CaptureSettingsStore
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.ui.assistente.AssistantScreen
import com.resolveprogramming.pocketcounter.ui.auth.AuthScreen
import com.resolveprogramming.pocketcounter.ui.cards.CartoesScreen
import com.resolveprogramming.pocketcounter.ui.home.HomeScreen
import androidx.navigation.NavHostController
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.onboarding.OnboardingScreen
import com.resolveprogramming.pocketcounter.ui.resumo.ResumoScreen
import com.resolveprogramming.pocketcounter.ui.contextos.ContextosTagsScreen
import com.resolveprogramming.pocketcounter.ui.fontes.FontesScreen
import com.resolveprogramming.pocketcounter.ui.mais.MaisScreen
import com.resolveprogramming.pocketcounter.ui.meios.MeiosScreen
import com.resolveprogramming.pocketcounter.ui.regras.RegrasScreen
import com.resolveprogramming.pocketcounter.ui.relatorio.RelatorioScreen
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.transacoes.TransacoesScreen
import com.resolveprogramming.pocketcounter.ui.wizard.WizardScreen

object Routes {
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val WIZARD = "wizard/{notificationId}"
    const val CARTOES = "cartoes"
    const val RESUMO = "resumo"
    const val TRANSACOES = "transacoes"
    const val MAIS = "mais"
    const val FONTES = "fontes"
    const val MEIOS = "meios"
    const val REGRAS = "regras"
    const val CONTEXTOS = "contextos"
    const val RELATORIO = "relatorio"
    const val ASSISTENTE = "assistente"

    fun wizard(notificationId: String) = "wizard/$notificationId"
}

/**
 * Shared bottom-tab navigation. Tabs are siblings rooted at HOME; popping up to HOME +
 * single-top keeps the back stack from growing as the user hops between tabs.
 */
private fun navTab(navController: NavHostController, tab: TabId) {
    val route = when (tab) {
        TabId.INICIO -> Routes.HOME
        TabId.TRANSACOES -> Routes.TRANSACOES
        TabId.CARTOES -> Routes.CARTOES
        TabId.MAIS -> Routes.MAIS
    }
    navController.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun PocketNavHost(
    tokenStore: TokenStore,
    captureSettingsStore: CaptureSettingsStore,
) {
    val isLoggedIn by tokenStore.isLoggedIn.collectAsStateWithLifecycle(initialValue = null)
    val onboardingSeen by captureSettingsStore.onboardingSeen
        .collectAsStateWithLifecycle(initialValue = null)

    if (isLoggedIn == null || onboardingSeen == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    val navController = rememberNavController()

    val loggedInTarget = if (onboardingSeen == false) Routes.ONBOARDING else Routes.HOME
    val startDestination = if (isLoggedIn == true) loggedInTarget else Routes.AUTH

    LaunchedEffect(isLoggedIn) {
        val target = if (isLoggedIn == true) loggedInTarget else Routes.AUTH
        val current = navController.currentDestination?.route
        if (current != null && current != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
    ) {
        composable(Routes.AUTH) {
            AuthScreen()
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNotificationTap = { notificationId ->
                    navController.navigate(Routes.wizard(notificationId))
                },
                onNavigate = { route ->
                    navController.navigate(route)
                },
            )
        }

        composable(
            route = Routes.WIZARD,
            arguments = listOf(navArgument("notificationId") { type = NavType.StringType }),
        ) {
            WizardScreen(
                onDismiss = { navController.popBackStack() },
                onBackToApp = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(Routes.CARTOES) {
            CartoesScreen(
                onBack = { navController.popBackStack() },
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.TRANSACOES) {
            TransacoesScreen(
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.MAIS) {
            MaisScreen(
                onNav = { tab -> navTab(navController, tab) },
                onOpenRoute = { route -> navController.navigate(route) },
            )
        }

        composable(Routes.FONTES) {
            FontesScreen(
                onBack = { navController.popBackStack() },
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.MEIOS) {
            MeiosScreen(
                onBack = { navController.popBackStack() },
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.REGRAS) {
            RegrasScreen(
                onBack = { navController.popBackStack() },
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.CONTEXTOS) {
            ContextosTagsScreen(
                onBack = { navController.popBackStack() },
                onNav = { tab -> navTab(navController, tab) },
            )
        }

        composable(Routes.RELATORIO) {
            RelatorioScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ASSISTENTE) {
            AssistantScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RESUMO) {
            ResumoScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
