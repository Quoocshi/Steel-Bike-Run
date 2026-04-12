package com.example.steelbikerunmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    val identifier: String,
    val password: String
)

data class RegisterRequestDto(
    val email: String,
    val phone: String,
    val password: String,
    val fullName: String,
    val role: String
)

data class AuthResponseDto(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("tokenType")
    val tokenType: String,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("fullName")
    val fullName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("role")
    val role: String
)
