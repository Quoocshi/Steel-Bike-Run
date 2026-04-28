package com.example.steelbikerunmobile.domain.usecase.auth

import com.example.steelbikerunmobile.domain.model.AuthSession
import com.example.steelbikerunmobile.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AuthSession?> = authRepository.observeSession()
}
