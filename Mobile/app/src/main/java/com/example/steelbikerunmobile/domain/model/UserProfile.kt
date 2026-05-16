package com.example.steelbikerunmobile.domain.model

/**
 * Domain model for user profile information.
 * Mapped from backend's GET /api/v1/user/profile response.
 */
data class UserProfile(
    val id: String,
    val email: String,
    val phone: String?,
    val fullName: String,
    val avatarUrl: String?,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: String?
)
