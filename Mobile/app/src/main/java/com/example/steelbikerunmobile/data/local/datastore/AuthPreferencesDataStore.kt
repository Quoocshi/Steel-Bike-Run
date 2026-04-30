package com.example.steelbikerunmobile.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.steelbikerunmobile.domain.model.AuthSession
import com.example.steelbikerunmobile.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "steelbike_auth")

@Singleton
class AuthPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tokenKey = stringPreferencesKey("jwt_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val emailKey = stringPreferencesKey("email")
    private val fullNameKey = stringPreferencesKey("full_name")
    private val roleKey = stringPreferencesKey("role")

    val tokenFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[tokenKey] }

    val authSessionFlow: Flow<AuthSession?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val token = prefs[tokenKey].orEmpty()
            val userId = prefs[userIdKey].orEmpty()
            val email = prefs[emailKey].orEmpty()
            val fullName = prefs[fullNameKey].orEmpty()
            val role = prefs[roleKey]?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            if (token.isBlank() || role == null) {
                null
            } else {
                AuthSession(
                    token = token,
                    userId = userId,
                    fullName = fullName,
                    email = email,
                    role = role
                )
            }
        }

    suspend fun saveAuthSession(session: AuthSession) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = session.token
            prefs[userIdKey] = session.userId
            prefs[emailKey] = session.email
            prefs[fullNameKey] = session.fullName
            prefs[roleKey] = session.role.name
        }
    }

    /**
     * Rotate just the JWT and the role while preserving identity fields (userId, email, fullName).
     * Used when the server issues a new token after a role switch — we don't want the user to
     * re-login.
     */
    suspend fun updateAccessTokenAndRole(token: String, role: UserRole) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[roleKey] = role.name
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
