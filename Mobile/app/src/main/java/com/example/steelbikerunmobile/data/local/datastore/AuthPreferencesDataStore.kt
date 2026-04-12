package com.example.steelbikerunmobile.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun saveAuthSession(token: String, email: String, fullName: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[emailKey] = email
            prefs[fullNameKey] = fullName
            prefs[roleKey] = role
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
