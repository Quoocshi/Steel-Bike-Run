package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.remote.NetworkErrorMapper
import com.example.steelbikerunmobile.data.remote.api.UserApiService
import com.example.steelbikerunmobile.data.remote.dto.UserProfileDto
import com.example.steelbikerunmobile.domain.model.UserProfile
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.domain.repository.UserRepository
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService
) : UserRepository {

    override suspend fun getProfile(): Result<UserProfile> = NetworkErrorMapper.safeCall {
        val envelope = userApiService.getProfile()
        envelope.data?.toDomain()
            ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Không thể tải thông tin hồ sơ")
    }

    private fun UserProfileDto.toDomain(): UserProfile {
        return UserProfile(
            id = id,
            email = email.orEmpty(),
            phone = phone,
            fullName = fullName.orEmpty(),
            avatarUrl = avatarUrl,
            role = runCatching { UserRole.valueOf(role.orEmpty()) }.getOrDefault(UserRole.CUSTOMER),
            isActive = isActive == true,
            createdAt = createdAt
        )
    }
}
