package com.example.steelbikerunmobile.data.remote.dto

/**
 * Mirrors backend's `ApiResponse` wrapper.
 *
 * All fields are nullable on the client because Gson uses reflection and would otherwise happily
 * assign `null` to non-null Kotlin properties when a field is missing or explicitly null in the
 * JSON payload — silently producing NPEs the next time the property is accessed.
 */
data class ApiEnvelope<T>(
    val code: Int? = null,
    val message: String? = null,
    val data: T? = null,
)
