package com.example.steelbikerunmobile.data.remote

import android.util.Log
import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that detects HTTP 401 (Unauthorized) responses from the backend.
 *
 * When a 401 is received — meaning the JWT has expired or been revoked — it clears
 * the locally stored auth session (DataStore). This causes [AuthPreferencesDataStore.tokenFlow]
 * to emit `null`, which in turn makes [SessionViewModel.isLoggedIn] flip to `false`.
 *
 * The navigation layer ([AppNavGraph]) observes this transition and automatically redirects
 * the user back to the Login screen.
 *
 * **Important**: Auth endpoints (login/register) are excluded so that a failed login attempt
 * does not accidentally wipe existing session data.
 */
class SessionExpiredInterceptor @Inject constructor(
    private val dataStore: AuthPreferencesDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401 && !isAuthEndpoint(request.url.encodedPath)) {
            Log.w("SessionExpired", "Received 401 from ${request.url.encodedPath} → clearing session")
            runBlocking { dataStore.clear() }
        }

        return response
    }

    /**
     * Login/register endpoints return 401 for wrong credentials — we must NOT
     * interpret those as "session expired".
     */
    private fun isAuthEndpoint(path: String): Boolean {
        return path.contains("/auth/login") || path.contains("/auth/register")
    }
}
