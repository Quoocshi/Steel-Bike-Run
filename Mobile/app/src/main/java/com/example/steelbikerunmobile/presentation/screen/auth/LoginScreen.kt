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
import androidx.compose.material.icons.outlined.Person
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
import com.example.steelbikerunmobile.presentation.component.atom.SbDividerWithText
import com.example.steelbikerunmobile.presentation.component.atom.SbPasswordField
import com.example.steelbikerunmobile.presentation.component.atom.SbPrimaryButton
import com.example.steelbikerunmobile.presentation.component.atom.SbTextField
import com.example.steelbikerunmobile.presentation.component.atom.SbTextButton
import com.example.steelbikerunmobile.presentation.component.molecule.AuthHeader
import com.example.steelbikerunmobile.presentation.component.molecule.SocialLoginRow
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

@Composable
fun LoginScreen(
    onNavigateRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState: LoginUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            keyboard?.hide()
            onLoginSuccess()
            viewModel.consumeSuccess()
        }
    }

    LoginContent(
        uiState = uiState,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLogin = {
            keyboard?.hide()
            viewModel.login()
        },
        onNavigateRegister = onNavigateRegister,
        onGoogleLogin = { /* TODO: Google OAuth */ },
        onAppleLogin = { /* TODO: Apple Sign-In */ },
    )
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onNavigateRegister: () -> Unit,
    onGoogleLogin: () -> Unit,
    onAppleLogin: () -> Unit,
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
                title = "Đăng nhập",
                subtitle = "Chào mừng bạn quay trở lại",
            )

            Spacer(modifier = Modifier.height(36.dp))

            SbTextField(
                value = uiState.identifier,
                onValueChange = onIdentifierChange,
                label = "Email hoặc số điện thoại",
                leadingIcon = Icons.Outlined.Person,
                errorMessage = uiState.identifierError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(14.dp))

            SbPasswordField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = "Mật khẩu",
                errorMessage = uiState.passwordError,
                imeAction = ImeAction.Done,
                onImeAction = onLogin,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SbTextButton(
                    text = "Quên mật khẩu?",
                    onClick = { /* TODO */ },
                )
            }

            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SbPrimaryButton(
                text = "Đăng nhập",
                onClick = onLogin,
                isLoading = uiState.isLoading,
                enabled = uiState.identifier.isNotBlank() && uiState.password.isNotBlank(),
            )

            Spacer(modifier = Modifier.height(28.dp))

            SbDividerWithText(text = "hoặc tiếp tục với")

            Spacer(modifier = Modifier.height(20.dp))

            SocialLoginRow(
                onGoogleLogin = onGoogleLogin,
                onAppleLogin = onAppleLogin,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chưa có tài khoản?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SbTextButton(
                    text = "Đăng ký ngay",
                    onClick = onNavigateRegister,
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LoginPreview() {
    SteelBikeTheme {
        LoginContent(
            uiState = LoginUiState(),
            onIdentifierChange = {},
            onPasswordChange = {},
            onLogin = {},
            onNavigateRegister = {},
            onGoogleLogin = {},
            onAppleLogin = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Login – Error state")
@Composable
private fun LoginErrorPreview() {
    SteelBikeTheme {
        LoginContent(
            uiState = LoginUiState(
                identifier = "wrong@email",
                identifierError = "Định dạng email không hợp lệ",
                password = "abc",
                passwordError = "Mật khẩu tối thiểu 6 ký tự",
            ),
            onIdentifierChange = {},
            onPasswordChange = {},
            onLogin = {},
            onNavigateRegister = {},
            onGoogleLogin = {},
            onAppleLogin = {},
        )
    }
}
