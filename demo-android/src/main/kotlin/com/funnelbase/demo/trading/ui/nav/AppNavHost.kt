package com.funnelbase.demo.trading.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.funnelbase.demo.trading.AppState
import com.funnelbase.demo.trading.ui.dashboard.DashboardScreen
import com.funnelbase.demo.trading.ui.instrument.InstrumentDetailScreen
import com.funnelbase.demo.trading.ui.markets.MarketsScreen
import com.funnelbase.demo.trading.ui.news.NewsScreen
import com.funnelbase.demo.trading.ui.onboarding.SignInScreen
import com.funnelbase.demo.trading.ui.onboarding.SignUpFlow
import com.funnelbase.demo.trading.ui.onboarding.WelcomeScreen
import com.funnelbase.demo.trading.ui.orders.OrdersScreen
import com.funnelbase.demo.trading.ui.push.PushScreen
import com.funnelbase.demo.trading.ui.push.TopicsScreen

/**
 * The whole navigation graph.
 *
 * One `NavHost` covering both onboarding and the signed-in app, with the start destination
 * chosen from the persisted session, rather than two hosts swapped by a boolean: a swap
 * recreates the host and loses the tab back stacks every time the signed-in flag changes.
 */
@Composable
fun AppNavHost(
    appState: AppState,
    navController: NavHostController = rememberNavController(),
    /**
     * Where a tapped push wants to go, as `PushRouting` read it, or null. The SDK reports the
     * open itself; this is only the navigation, which it cannot know.
     */
    pushRoute: String? = null,
    onPushRouted: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(pushRoute) {
        val route = pushRoute ?: return@LaunchedEffect
        // A push that arrives before anyone has signed in leads nowhere: the screens behind it
        // need an account, and the open has been recorded either way.
        if (appState.isSignedIn) {
            val tab = Tab.entries.firstOrNull { it.route == route }
            if (tab != null) navController.switchTab(tab) else navController.navigate(route)
        }
        onPushRouted()
    }

    Scaffold(
        bottomBar = {
            // Hidden on onboarding and on the instrument detail, which is a full-screen push.
            // Animated rather than toggled so the detail screen does not appear to jump upward
            // as the bar disappears from under it.
            AnimatedVisibility(
                visible = currentRoute in Tab.routes,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                BottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab -> navController.switchTab(tab) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (appState.isSignedIn) Routes.DASHBOARD else Routes.WELCOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onSignIn = { navController.navigate(Routes.SIGN_IN) },
                    onSignUp = { navController.navigate(Routes.SIGN_UP) },
                )
            }

            composable(Routes.SIGN_IN) {
                SignInScreen(
                    onBack = navController::popBackStack,
                    onSignedIn = { username, pro ->
                        appState.signIn(username, pro)
                        navController.enterApp()
                    },
                )
            }

            composable(Routes.SIGN_UP) {
                SignUpFlow(
                    onBack = navController::popBackStack,
                    onSignedUp = { username ->
                        appState.signUp(username)
                        navController.enterApp()
                    },
                )
            }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    appState = appState,
                    onInstrumentSelected = { navController.navigate(Routes.instrumentDetail(it)) },
                    onSeeAllNews = { navController.switchTab(Tab.NEWS) },
                    onPush = { navController.navigate(Routes.PUSH) },
                    onSignOut = {
                        appState.signOut()
                        navController.exitApp()
                    },
                )
            }

            composable(Routes.PUSH) {
                PushScreen(
                    onBack = navController::popBackStack,
                    onTopics = { navController.navigate(Routes.TOPICS) },
                )
            }

            composable(Routes.TOPICS) { TopicsScreen(onBack = navController::popBackStack) }

            composable(Routes.MARKETS) {
                MarketsScreen(
                    appState = appState,
                    onInstrumentSelected = { navController.navigate(Routes.instrumentDetail(it)) },
                )
            }

            composable(Routes.ORDERS) { OrdersScreen(appState = appState) }

            composable(Routes.NEWS) { NewsScreen(appState = appState) }

            composable(Routes.INSTRUMENT_DETAIL) { entry ->
                val symbol = entry.arguments?.getString(Routes.ARG_SYMBOL).orEmpty()
                InstrumentDetailScreen(
                    appState = appState,
                    symbol = symbol,
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}

/**
 * Switches tabs the way a bottom bar is expected to behave: one entry per tab on the back stack,
 * each tab's own state saved and restored, and back from any tab leaving the app rather than
 * walking the history of tabs visited.
 */
private fun NavHostController.switchTab(tab: Tab) {
    if (currentDestination?.route == tab.route) return
    navigate(tab.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Onboarding is finished: clear it so back from the dashboard does not return to a login form. */
private fun NavHostController.enterApp() {
    navigate(Routes.DASHBOARD) {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** Sign-out, in the same shape — the signed-in screens must not remain behind the welcome page. */
private fun NavHostController.exitApp() {
    navigate(Routes.WELCOME) {
        popUpTo(graph.id) { inclusive = true }
    }
}
