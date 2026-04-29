package com.example.steelbikerunmobile.presentation.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.presentation.component.atom.SbPasswordField
import com.example.steelbikerunmobile.presentation.component.atom.SbPrimaryButton
import com.example.steelbikerunmobile.presentation.component.atom.SbTextField
import com.example.steelbikerunmobile.presentation.component.atom.SbTextButton
import com.example.steelbikerunmobile.presentation.component.molecule.AuthHeader
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

@Composable
fun RegisterScreen(
    onNavigateBackToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState: RegisterUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.registerSuccess) {
        if (uiState.registerSuccess) {
            keyboard?.hide()
            onRegisterSuccess()
            viewModel.consumeSuccess()
        }
    }

    RegisterContent(
        uiState = uiState,
        onFullNameChange = viewModel::onFullNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRegister = {
            keyboard?.hide()
            viewModel.register()
        },
        onNavigateBackToLogin = onNavigateBackToLogin,
    )
}

@Composable
private fun RegisterContent(
    uiState: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onNavigateBackToLogin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHeader(
                title = "Tạo tài khoản",
                subtitle = "Điền thông tin để bắt đầu",
            )

            Spacer(modifier = Modifier.height(32.dp))

            SbTextField(
                value = uiState.fullName,
                onValueChange = onFullNameChange,
                label = "Họ và tên",
                leadingIcon = Icons.Outlined.Person,
                errorMessage = uiState.fullNameError,
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(14.dp))

            SbTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = "Email",
                leadingIcon = Icons.Outlined.Email,
                errorMessage = uiState.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(14.dp))

            SbTextField(
                value = uiState.phone,
                onValueChange = onPhoneChange,
                label = "Số điện thoại",
                leadingIcon = Icons.Outlined.Phone,
                errorMessage = uiState.phoneError,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(14.dp))

            SbPasswordField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = "Mật khẩu (tối thiểu 6 ký tự)",
                errorMessage = uiState.passwordError,
                imeAction = ImeAction.Done,
                onImeAction = onRegister,
            )

            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SbPrimaryButton(
                text = "Tạo tài khoản",
                onClick = onRegister,
                isLoading = uiState.isLoading,
                enabled = uiState.fullName.isNotBlank() &&
                          uiState.email.isNotBlank() &&
                          uiState.phone.isNotBlank() &&
                          uiState.password.isNotBlank(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Đã có tài khoản?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SbTextButton(
                    text = "Đăng nhập",
                    onClick = onNavigateBackToLogin,
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun RegisterPreview() {
    SteelBikeTheme {
        RegisterContent(
            uiState = RegisterUiState(),
            onFullNameChange = {},
            onEmailChange = {},
            onPhoneChange = {},
            onPasswordChange = {},
            onRegister = {},
            onNavigateBackToLogin = {},
        )
    }
}
