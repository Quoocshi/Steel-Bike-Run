package com.example.steelbikerunmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.steelbikerunmobile.presentation.navigation.AppNavGraph
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SteelBikeTheme {
                AppNavGraph()
            }
        }
    }
}