package com.dosius.smart.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "credentials")

@Singleton
class CredentialPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val EMAIL = stringPreferencesKey("email")
        val PASSWORD = stringPreferencesKey("password")
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_EXPIRES = longPreferencesKey("token_expires")
        val PATIENT_ID = stringPreferencesKey("patient_id")

        val ACCOUNT_ID = stringPreferencesKey("account_id")
    }

    val email: Flow<String> = context.dataStore.data.map { it[EMAIL] ?: "" }
    val password: Flow<String> = context.dataStore.data.map { it[PASSWORD] ?: "" }
    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN] }
    val tokenExpires: Flow<Long> = context.dataStore.data.map { it[TOKEN_EXPIRES] ?: 0L }
    val patientId: Flow<String?> = context.dataStore.data.map { it[PATIENT_ID] }

    val accountId: Flow<String?> = context.dataStore.data.map { it[ACCOUNT_ID] }

    suspend fun saveCredentials(email: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[EMAIL] = email
            prefs[PASSWORD] = password
        }
    }

    suspend fun saveAuthTicket(token: String, expires: Long, patientId: String, accountId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = token
            prefs[TOKEN_EXPIRES] = expires
            prefs[PATIENT_ID] = patientId
            prefs[ACCOUNT_ID] = accountId
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN)
            prefs.remove(TOKEN_EXPIRES)
            prefs.remove(PATIENT_ID)
            prefs.remove(ACCOUNT_ID)
        }
    }
}
