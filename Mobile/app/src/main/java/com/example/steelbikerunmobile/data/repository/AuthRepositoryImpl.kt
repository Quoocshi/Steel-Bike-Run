package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import com.example.steelbikerunmobile.data.remote.api.AuthApiService
import com.example.steelbikerunmobile.data.remote.dto.AuthResponseDto
import com.example.steelbikerunmobile.data.remote.dto.LoginRequestDto
import com.example.steelbikerunmobile.data.remote.dto.RegisterRequestDto
import com.example.steelbikerunmobile.domain.model.AuthSession
import com.example.steelbikerunmobile.domain.model.RegisterPayload
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val dataStore: AuthPreferencesDataStore
) : AuthRepository {

    override suspend fun login(identifier: String, password: String): Result<AuthSession> = runCatching {
        val envelope = authApiService.login(LoginRequestDto(identifier, password))
        val session = envelope.data?.toDomain()
            ?: error(envelope.message.ifBlank { "Login failed" })
        dataStore.saveAuthSession(
            token = session.token,
            email = session.email,
            fullName = session.fullName,
            role = session.role.name
        )
        session
    }

    override suspend fun register(payload: RegisterPayload): Result<AuthSession> = runCatching {
        val envelope = authApiService.register(
            RegisterRequestDto(
                email = payload.email,
                phone = payload.phone,
                password = payload.password,
                fullName = payload.fullName,
                role = payload.role.name
            )
        )
        val session = envelope.data?.toDomain()
            ?: error(envelope.message.ifBlank { "Register failed" })
        dataStore.saveAuthSession(
            token = session.token,
            email = session.email,
            fullName = session.fullName,
            role = session.role.name
        )
        session
    }

    override fun observeToken(): Flow<String?> = dataStore.tokenFlow

    override suspend fun logout() {
        dataStore.clear()
    }

    private fun AuthResponseDto.toDomain(): AuthSession {
        return AuthSession(
            token = accessToken,
            userId = userId,
            fullName = fullName,
            email = email,
            role = UserRole.valueOf(role)
        )
    }
}
