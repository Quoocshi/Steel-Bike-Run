package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.example.steelbikerunmobile.data.remote.dto.AuthResponseDto
import com.example.steelbikerunmobile.data.remote.dto.LoginRequestDto
import com.example.steelbikerunmobile.data.remote.dto.RegisterRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): ApiEnvelope<AuthResponseDto>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): ApiEnvelope<AuthResponseDto>
}
