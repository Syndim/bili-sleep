package org.syndim.bilisleep.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sleepTimerDataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_timer_preferences")

@Singleton
class SleepTimerPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val LAST_DURATION_KEY = intPreferencesKey("last_duration_minutes")
        private val FADE_OUT_ENABLED_KEY = booleanPreferencesKey("fade_out_enabled")
        private val FADE_OUT_DURATION_KEY = intPreferencesKey("fade_out_duration_seconds")
        
        private const val DEFAULT_DURATION_MINUTES = 30
        private const val DEFAULT_FADE_OUT_DURATION_SECONDS = 10
    }

    /**
     * Get the last used sleep timer duration as a Flow
     */
    val lastDurationMinutes: Flow<Int> = context.sleepTimerDataStore.data.map { preferences ->
        preferences[LAST_DURATION_KEY] ?: DEFAULT_DURATION_MINUTES
    }

    /**
     * Get fade out enabled setting as a Flow
     */
    val fadeOutEnabled: Flow<Boolean> = context.sleepTimerDataStore.data.map { preferences ->
        preferences[FADE_OUT_ENABLED_KEY] ?: true
    }

    /**
     * Get fade out duration as a Flow
     */
    val fadeOutDurationSeconds: Flow<Int> = context.sleepTimerDataStore.data.map { preferences ->
        preferences[FADE_OUT_DURATION_KEY] ?: DEFAULT_FADE_OUT_DURATION_SECONDS
    }

    /**
     * Get the last used duration synchronously (for initialization)
     */
    suspend fun getLastDurationMinutes(): Int {
        return context.sleepTimerDataStore.data.first()[LAST_DURATION_KEY] ?: DEFAULT_DURATION_MINUTES
    }

    /**
     * Get fade out enabled synchronously
     */
    suspend fun getFadeOutEnabled(): Boolean {
        return context.sleepTimerDataStore.data.first()[FADE_OUT_ENABLED_KEY] ?: true
    }

    /**
     * Get fade out duration synchronously
     */
    suspend fun getFadeOutDurationSeconds(): Int {
        return context.sleepTimerDataStore.data.first()[FADE_OUT_DURATION_KEY] ?: DEFAULT_FADE_OUT_DURATION_SECONDS
    }

    /**
     * Save the last used sleep timer duration
     */
    suspend fun saveLastDuration(durationMinutes: Int) {
        context.sleepTimerDataStore.edit { preferences ->
            preferences[LAST_DURATION_KEY] = durationMinutes
        }
    }

    /**
     * Save fade out enabled setting
     */
    suspend fun saveFadeOutEnabled(enabled: Boolean) {
        context.sleepTimerDataStore.edit { preferences ->
            preferences[FADE_OUT_ENABLED_KEY] = enabled
        }
    }

    /**
     * Save fade out duration
     */
    suspend fun saveFadeOutDuration(durationSeconds: Int) {
        context.sleepTimerDataStore.edit { preferences ->
            preferences[FADE_OUT_DURATION_KEY] = durationSeconds
        }
    }

    /**
     * Save all sleep timer preferences at once
     */
    suspend fun savePreferences(durationMinutes: Int, fadeOutEnabled: Boolean, fadeOutDurationSeconds: Int) {
        context.sleepTimerDataStore.edit { preferences ->
            preferences[LAST_DURATION_KEY] = durationMinutes
            preferences[FADE_OUT_ENABLED_KEY] = fadeOutEnabled
            preferences[FADE_OUT_DURATION_KEY] = fadeOutDurationSeconds
        }
    }
}
