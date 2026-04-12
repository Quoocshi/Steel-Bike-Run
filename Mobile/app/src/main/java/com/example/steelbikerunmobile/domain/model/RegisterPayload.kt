package com.example.steelbikerunmobile.domain.model

data class RegisterPayload(
    val email: String,
    val phone: String,
    val password: String,
    val fullName: String,
    val role: UserRole
)
