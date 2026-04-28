package com.example.steelbikerunmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.steelbikerunmobile.presentation.navigation.AppNavGraph
import com.example.steelbikerunmobile.presentation.theme.AppThemeMode
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Auth screens (Splash, Login, Register) use Customer/light theme.
            // HomeScreen overrides with DriverTheme when the logged-in user is a driver.
            SteelBikeTheme(mode = AppThemeMode.CUSTOMER) {
                AppNavGraph()
            }
        }
    }
}
