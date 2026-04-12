package com.example.steelbikerunmobile.presentation.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    onNavigateRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState: LoginUiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess()
            viewModel.consumeSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SteelBike", style = MaterialTheme.typography.headlineMedium)
        Text("Đăng nhập", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = uiState.identifier,
            onValueChange = viewModel::onIdentifierChange,
            label = { Text("Email hoặc số điện thoại") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::login,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Đăng nhập")
            }
        }

        TextButton(
            onClick = onNavigateRegister,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Chưa có tài khoản? Đăng ký")
        }
    }
}
