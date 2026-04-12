package com.example.steelbikerunmobile.domain.usecase.auth

import com.example.steelbikerunmobile.domain.model.AuthSession
import com.example.steelbikerunmobile.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(identifier: String, password: String): Result<AuthSession> {
        return authRepository.login(identifier = identifier, password = password)
    }
}
