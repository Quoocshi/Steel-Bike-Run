package com.example.steelbikerunmobile.domain.model

data class AuthSession(
    val token: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val role: UserRole
)
