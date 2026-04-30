package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import com.example.steelbikerunmobile.data.remote.NetworkErrorMapper
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

    override suspend fun login(identifier: String, password: String): Result<AuthSession> =
        NetworkErrorMapper.safeCall {
            val envelope = authApiService.login(LoginRequestDto(identifier, password))
            val session = envelope.data?.toDomain()
                ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Đăng nhập thất bại")
            dataStore.saveAuthSession(session)
            session
        }

    override suspend fun register(payload: RegisterPayload): Result<AuthSession> =
        NetworkErrorMapper.safeCall {
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
                ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Đăng ký thất bại")
            dataStore.saveAuthSession(session)
            session
        }

    override fun observeToken(): Flow<String?> = dataStore.tokenFlow

    override fun observeSession(): Flow<AuthSession?> = dataStore.authSessionFlow

    override suspend fun logout() {
        dataStore.clear()
    }

    override suspend fun updateAccessToken(token: String, role: UserRole) {
        dataStore.updateAccessTokenAndRole(token, role)
    }

    private fun AuthResponseDto.toDomain(): AuthSession {
        // role coming back from the backend is the name of the enum constant. Defending against
        // an unexpected value avoids a hard crash if the backend introduces a new role.
        val resolvedRole = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.CUSTOMER)
        return AuthSession(
            token = accessToken,
            userId = userId,
            fullName = fullName,
            email = email,
            role = resolvedRole,
        )
    }
}
