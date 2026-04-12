package com.example.steelbikerunmobile.presentation.screen.auth

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
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccess: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(role = role, errorMessage = null) }

    fun register() {
        val current = _uiState.value
        if (current.fullName.isBlank() || current.email.isBlank() || current.phone.isBlank() || current.password.length < 6) {
            _uiState.update {
                it.copy(errorMessage = "Vui lòng nhập đủ thông tin hợp lệ")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = registerUseCase(
                RegisterPayload(
                    email = current.email.trim(),
                    phone = current.phone.trim(),
                    password = current.password,
                    fullName = current.fullName.trim(),
                    role = current.role
                )
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, registerSuccess = true)
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Đăng ký thất bại"
                    )
                }
            }
        }
    }

    fun consumeSuccess() {
        _uiState.update { it.copy(registerSuccess = false) }
    }
}
