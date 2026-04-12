package com.example.steelbikerunmobile.domain.usecase.auth

import com.example.steelbikerunmobile.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.logout()
}
