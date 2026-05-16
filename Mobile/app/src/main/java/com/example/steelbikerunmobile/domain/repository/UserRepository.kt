package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.UserProfile

interface UserRepository {

    /**
     * Fetch the authenticated user's profile from the backend.
     * Backend caches in Redis (TTL 10 min) — fast subsequent reads.
     */
    suspend fun getProfile(): Result<UserProfile>
}
