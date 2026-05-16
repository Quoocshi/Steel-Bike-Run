package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.example.steelbikerunmobile.data.remote.dto.UserProfileDto
import retrofit2.http.GET

interface UserApiService {

    /**
     * GET /api/v1/user/profile
     * Trả về thông tin profile của user đang đăng nhập (JWT required).
     * Backend dùng Cache-Aside (Redis TTL 10 phút) → latency thấp.
     */
    @GET("api/v1/user/profile")
    suspend fun getProfile(): ApiEnvelope<UserProfileDto>
}
