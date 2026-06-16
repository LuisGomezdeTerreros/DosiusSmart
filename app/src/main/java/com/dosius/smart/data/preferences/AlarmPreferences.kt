package com.dosius.smart.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarms")

@Singleton
class AlarmPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HYPO_ENABLED = booleanPreferencesKey("hypo_enabled")
        val HYPER_ENABLED = booleanPreferencesKey("hyper_enabled")
        val HYPO_THRESHOLD = intPreferencesKey("hypo_threshold")
        val HYPER_THRESHOLD = intPreferencesKey("hyper_threshold")
        val HYPO_STATE = stringPreferencesKey("hypo_state")
        val HYPER_STATE = stringPreferencesKey("hyper_state")
    }

    val hypoEnabled: Flow<Boolean> = context.alarmDataStore.data.map { it[HYPO_ENABLED] ?: false }
    val hyperEnabled: Flow<Boolean> = context.alarmDataStore.data.map { it[HYPER_ENABLED] ?: false }
    val hypoThreshold: Flow<Int> = context.alarmDataStore.data.map { it[HYPO_THRESHOLD] ?: 70 }
    val hyperThreshold: Flow<Int> = context.alarmDataStore.data.map { it[HYPER_THRESHOLD] ?: 180 }
    val hypoState: Flow<String> = context.alarmDataStore.data.map { it[HYPO_STATE] ?: "NORMAL" }
    val hyperState: Flow<String> = context.alarmDataStore.data.map { it[HYPER_STATE] ?: "NORMAL" }

    suspend fun saveSettings(
        hypoEnabled: Boolean,
        hyperEnabled: Boolean,
        hypoThreshold: Int,
        hyperThreshold: Int
    ) {
        context.alarmDataStore.edit { prefs ->
            prefs[HYPO_ENABLED] = hypoEnabled
            prefs[HYPER_ENABLED] = hyperEnabled
            prefs[HYPO_THRESHOLD] = hypoThreshold
            prefs[HYPER_THRESHOLD] = hyperThreshold
        }
    }

    suspend fun setHypoState(state: String) {
        context.alarmDataStore.edit { it[HYPO_STATE] = state }
    }

    suspend fun setHyperState(state: String) {
        context.alarmDataStore.edit { it[HYPER_STATE] = state }
    }
}
