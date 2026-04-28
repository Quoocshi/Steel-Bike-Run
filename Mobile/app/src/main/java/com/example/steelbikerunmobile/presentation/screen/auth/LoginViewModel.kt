package com.example.steelbikerunmobile.presentation.screen.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.domain.usecase.auth.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val identifier: String = "",
    val identifierError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdentifierChange(value: String) {
        _uiState.update { it.copy(identifier = value, identifierError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun login() {
        val current = _uiState.value
        val identifierError = validateIdentifier(current.identifier)
        val passwordError = if (current.password.length < 6) "Mật khẩu tối thiểu 6 ký tự" else null

        if (identifierError != null || passwordError != null) {
            _uiState.update {
                it.copy(identifierError = identifierError, passwordError = passwordError)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = loginUseCase(current.identifier.trim(), current.password)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, loginSuccess = true)
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Đăng nhập thất bại",
                    )
                }
            }
        }
    }

    fun consumeSuccess() {
        _uiState.update { it.copy(loginSuccess = false) }
    }

    private fun validateIdentifier(value: String): String? {
        if (value.isBlank()) return "Vui lòng nhập email hoặc số điện thoại"
        val looksLikeEmail = value.contains('@')
        return when {
            looksLikeEmail && !Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() ->
                "Địa chỉ email không hợp lệ"
            !looksLikeEmail && !isValidVietnamesePhone(value.trim()) ->
                "Số điện thoại không hợp lệ (VD: 0912345678)"
            else -> null
        }
    }

    private fun isValidVietnamesePhone(phone: String): Boolean {
        val normalized = phone.replace(" ", "").replace("-", "")
        return Regex("^(\\+84|84|0)[3-9]\\d{8}$").matches(normalized)
    }
}
