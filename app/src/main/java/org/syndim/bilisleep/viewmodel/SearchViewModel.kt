package org.syndim.bilisleep.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.syndim.bilisleep.data.model.SearchUiState
import org.syndim.bilisleep.data.model.VideoSearchItem
import org.syndim.bilisleep.data.repository.BiliRepository
import org.syndim.bilisleep.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: BiliRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    val searchHistory: StateFlow<List<String>> = searchHistoryRepository.searchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private var searchJob: Job? = null
    private var currentPage = 1
    private val allResults = mutableListOf<VideoSearchItem>()
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Perform search
     */
    fun search(query: String = _searchQuery.value) {
        if (query.isBlank()) return
        
        searchJob?.cancel()
        currentPage = 1
        allResults.clear()
        
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            
            // Add to search history
            addToSearchHistory(query)
            
            repository.searchVideos(query, page = 1).fold(
                onSuccess = { items ->
                    allResults.addAll(items)
                    _uiState.value = SearchUiState.Success(
                        items = allResults.toList(),
                        query = query,
                        page = currentPage,
                        hasMore = items.size >= 20
                    )
                },
                onFailure = { error ->
                    _uiState.value = SearchUiState.Error(
                        message = error.message ?: "Search failed"
                    )
                }
            )
        }
    }
    
    /**
     * Load more results
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (currentState !is SearchUiState.Success || !currentState.hasMore) return
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            currentPage++
            
            repository.searchVideos(currentState.query, page = currentPage).fold(
                onSuccess = { items ->
                    allResults.addAll(items)
                    _uiState.value = SearchUiState.Success(
                        items = allResults.toList(),
                        query = currentState.query,
                        page = currentPage,
                        hasMore = items.size >= 20
                    )
                },
                onFailure = { error ->
                    // Revert page on failure
                    currentPage--
                    _uiState.value = SearchUiState.Error(
                        message = error.message ?: "Failed to load more"
                    )
                }
            )
        }
    }
    
    /**
     * Clear search results
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Initial
        currentPage = 1
        allResults.clear()
    }
    
    /**
     * Add query to search history
     */
    private fun addToSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.addToHistory(query)
        }
    }
    
    /**
     * Clear search history
     */
    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }
    
    /**
     * Remove item from search history
     */
    fun removeFromHistory(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeFromHistory(query)
        }
    }
}
