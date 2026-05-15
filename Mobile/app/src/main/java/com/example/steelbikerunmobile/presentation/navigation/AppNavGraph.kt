package com.example.steelbikerunmobile.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.steelbikerunmobile.presentation.screen.auth.LoginScreen
import com.example.steelbikerunmobile.presentation.screen.auth.RegisterScreen
import com.example.steelbikerunmobile.presentation.screen.auth.SplashScreen
import com.example.steelbikerunmobile.presentation.screen.home.HomeScreen
import com.example.steelbikerunmobile.presentation.screen.profile.ProfileScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@Composable
fun AppNavGraph(
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val isLoggedIn: Boolean by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()

    val logoutAndNavigate: () -> Unit = {
        sessionViewModel.logout()
        navController.navigate(Screen.Login.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // ── Session expiry detection ──────────────────────────────────────────────
    // When the SessionExpiredInterceptor clears the DataStore (due to a 401),
    // isLoggedIn flips from true → false. We observe this transition reactively
    // and redirect the user to Login, clearing the entire backstack.
    //
    // drop(1) skips the initial emission so we only react to actual changes.
    // filter { !it } means we only care about the "logged out" transition.
    LaunchedEffect(Unit) {
        snapshotFlow { isLoggedIn }
            .distinctUntilChanged()
            .drop(1)             // skip initial value
            .filter { !it }      // only react when session becomes invalid
            .collect {
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {

        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    val destination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBackToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onLogout = logoutAndNavigate,
                onNavigateToProfile = { isDriverMode ->
                    navController.navigate(Screen.Profile.createRoute(isDriverMode))
                }
            )
        }

        composable(
            route = Screen.Profile.route,
            arguments = listOf(
                navArgument("isDriverMode") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val isDriverMode = backStackEntry.arguments?.getBoolean("isDriverMode") ?: false
            ProfileScreen(
                isDriverMode = isDriverMode,
                onBack = { navController.popBackStack() },
                onLogout = logoutAndNavigate,
            )
        }
    }
}
