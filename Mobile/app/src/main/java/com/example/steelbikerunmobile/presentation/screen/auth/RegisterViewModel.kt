package com.example.steelbikerunmobile.presentation.screen.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.domain.model.RegisterPayload
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.domain.usecase.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val fullName: String = "",
    val fullNameError: String? = null,
    val email: String = "",
    val emailError: String? = null,
    val phone: String = "",
    val phoneError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val role: UserRole = UserRole.CUSTOMER,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccess: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) =
        _uiState.update { it.copy(fullName = value, fullNameError = null, errorMessage = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onPhoneChange(value: String) =
        _uiState.update { it.copy(phone = value, phoneError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun onRoleChange(role: UserRole) =
        _uiState.update { it.copy(role = role, errorMessage = null) }

    fun register() {
        val s = _uiState.value
        val fullNameError  = if (s.fullName.isBlank()) "Vui lòng nhập họ và tên" else null
        val emailError     = validateEmail(s.email)
        val phoneError     = validatePhone(s.phone)
        val passwordError  = if (s.password.length < 6) "Mật khẩu tối thiểu 6 ký tự" else null

        if (listOf(fullNameError, emailError, phoneError, passwordError).any { it != null }) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError    = emailError,
                    phoneError    = phoneError,
                    passwordError = passwordError,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = registerUseCase(
                RegisterPayload(
                    email    = s.email.trim(),
                    phone    = s.phone.trim(),
                    password = s.password,
                    fullName = s.fullName.trim(),
                    role     = s.role,
                )
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, registerSuccess = true)
                } else {
                    it.copy(
                        isLoading    = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Đăng ký thất bại",
                    )
                }
            }
        }
    }

    fun consumeSuccess() {
        _uiState.update { it.copy(registerSuccess = false) }
    }

    private fun validateEmail(email: String): String? {
        if (email.isBlank()) return "Vui lòng nhập email"
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) return "Địa chỉ email không hợp lệ"
        return null
    }

    private fun validatePhone(phone: String): String? {
        val normalized = phone.trim().replace(" ", "").replace("-", "")
        if (normalized.isBlank()) return "Vui lòng nhập số điện thoại"
        if (!Regex("^(\\+84|84|0)[3-9]\\d{8}$").matches(normalized)) {
            return "Số điện thoại không hợp lệ (VD: 0912345678)"
        }
        return null
    }
}
