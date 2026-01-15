package org.syndim.bilisleep.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
        private const val MAX_HISTORY_COUNT = 10
        private const val SEPARATOR = "\u0000" // Null character as separator
    }

    /**
     * Get search history as a Flow
     */
    val searchHistory: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val historyString = preferences[SEARCH_HISTORY_KEY] ?: ""
        if (historyString.isEmpty()) {
            emptyList()
        } else {
            historyString.split(SEPARATOR).filter { it.isNotEmpty() }
        }
    }

    /**
     * Add a query to search history
     */
    suspend fun addToHistory(query: String) {
        if (query.isBlank()) return
        
        context.dataStore.edit { preferences ->
            val historyString = preferences[SEARCH_HISTORY_KEY] ?: ""
            val history = if (historyString.isEmpty()) {
                mutableListOf()
            } else {
                historyString.split(SEPARATOR).filter { it.isNotEmpty() }.toMutableList()
            }
            
            // Remove if already exists (to move to front)
            history.remove(query)
            // Add to front
            history.add(0, query)
            // Keep only MAX_HISTORY_COUNT items
            val trimmedHistory = history.take(MAX_HISTORY_COUNT)
            
            preferences[SEARCH_HISTORY_KEY] = trimmedHistory.joinToString(SEPARATOR)
        }
    }

    /**
     * Remove a query from search history
     */
    suspend fun removeFromHistory(query: String) {
        context.dataStore.edit { preferences ->
            val historyString = preferences[SEARCH_HISTORY_KEY] ?: ""
            val history = if (historyString.isEmpty()) {
                mutableListOf()
            } else {
                historyString.split(SEPARATOR).filter { it.isNotEmpty() }.toMutableList()
            }
            
            history.remove(query)
            preferences[SEARCH_HISTORY_KEY] = history.joinToString(SEPARATOR)
        }
    }

    /**
     * Clear all search history
     */
    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_KEY] = ""
        }
    }
}
