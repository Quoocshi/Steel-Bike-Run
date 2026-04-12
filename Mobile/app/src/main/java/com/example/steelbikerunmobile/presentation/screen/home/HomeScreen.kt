package com.example.steelbikerunmobile.presentation.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("SteelBike") }) }
    ) { innerPadding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Đăng nhập thành công",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tuần 1: Login/Register + JWT DataStore đã sẵn sàng.",
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            Button(onClick = onLogout) {
                Text("Đăng xuất")
            }
        }
    }
}
