package com.example.steelbikerunmobile.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steelbikerunmobile.domain.usecase.auth.LogoutUseCase
import com.example.steelbikerunmobile.domain.usecase.auth.ObserveAuthTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    observeAuthTokenUseCase: ObserveAuthTokenUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = observeAuthTokenUseCase()
        .map { !it.isNullOrBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
