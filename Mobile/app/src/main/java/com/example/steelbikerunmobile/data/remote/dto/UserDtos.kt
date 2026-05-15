package com.example.steelbikerunmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors backend's UserProfileResponse record.
 * GET /api/v1/user/profile → ApiEnvelope<UserProfileDto>
 */
data class UserProfileDto(
    val id: String,
    val email: String?,
    val phone: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val role: String?,
    @SerializedName(value = "isActive", alternate = ["active"])
    val isActive: Boolean?,
    val createdAt: String?
)
