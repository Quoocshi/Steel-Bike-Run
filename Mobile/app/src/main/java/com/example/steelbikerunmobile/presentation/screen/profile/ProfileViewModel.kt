package com.example.steelbikerunmobile.presentation.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.UserProfile
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.domain.usecase.auth.LogoutUseCase
import com.example.steelbikerunmobile.domain.usecase.driver.GetDriverProfileUseCase
import com.example.steelbikerunmobile.domain.usecase.user.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val driverProfile: DriverProfile? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getDriverProfileUseCase: GetDriverProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            getUserProfileUseCase().fold(
                onSuccess = { userProfile ->
                    _uiState.update { it.copy(userProfile = userProfile, isLoading = false) }

                    // If current role is DRIVER, also fetch driver-specific info
                    if (userProfile.role == UserRole.DRIVER) {
                        loadDriverProfile()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Không thể tải hồ sơ"
                        )
                    }
                }
            )
        }
    }

    private fun loadDriverProfile() {
        viewModelScope.launch {
            getDriverProfileUseCase().onSuccess { driverProfile ->
                _uiState.update { it.copy(driverProfile = driverProfile) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
