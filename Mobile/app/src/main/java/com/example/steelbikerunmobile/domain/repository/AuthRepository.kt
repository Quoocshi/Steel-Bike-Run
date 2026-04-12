package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.AuthSession
import com.example.steelbikerunmobile.domain.model.RegisterPayload
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(identifier: String, password: String): Result<AuthSession>
    suspend fun register(payload: RegisterPayload): Result<AuthSession>
    fun observeToken(): Flow<String?>
    suspend fun logout()
}
