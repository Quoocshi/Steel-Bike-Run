package com.example.steelbikerunmobile.domain.usecase.user

import com.example.steelbikerunmobile.domain.model.UserProfile
import com.example.steelbikerunmobile.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Fetch the authenticated user's profile from backend.
 * Used by both Customer and Driver ProfileScreens.
 */
class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<UserProfile> = userRepository.getProfile()
}
