package com.example.steelbikerunmobile.presentation.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerHomeScreen
import com.example.steelbikerunmobile.presentation.screen.driver.home.DriverHomeScreen
import com.example.steelbikerunmobile.presentation.theme.AppThemeMode
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    when (session?.role) {
        UserRole.DRIVER -> {
            // Driver app uses its own dark/orange theme
            SteelBikeTheme(mode = AppThemeMode.DRIVER) {
                DriverHomeScreen(onLogout = onLogout)
            }
        }
        UserRole.CUSTOMER, UserRole.ADMIN -> {
            CustomerHomeScreen(onLogout = onLogout)
        }
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
