package com.dosius.smart.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val onboardingComplete: Flow<Boolean> = context.appDataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val onboardingCompleteOrNull: Flow<Boolean?> = context.appDataStore.data.map { prefs ->
        if (prefs.contains(ONBOARDING_COMPLETE)) prefs[ONBOARDING_COMPLETE] else null
    }

    suspend fun setOnboardingComplete() {
        context.appDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETE] = true
        }
    }
}
