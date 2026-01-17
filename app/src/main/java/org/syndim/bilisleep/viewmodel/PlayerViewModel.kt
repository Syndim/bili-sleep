package org.syndim.bilisleep.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.syndim.bilisleep.data.model.PlaylistItem
import org.syndim.bilisleep.data.model.PlayerState
import org.syndim.bilisleep.data.model.SleepTimerSettings
import org.syndim.bilisleep.data.model.VideoSearchItem
import org.syndim.bilisleep.data.repository.BiliRepository
import org.syndim.bilisleep.data.repository.SleepTimerPreferencesRepository
import org.syndim.bilisleep.player.AudioPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: BiliRepository,
    private val playerManager: AudioPlayerManager,
    private val sleepTimerPreferences: SleepTimerPreferencesRepository
) : ViewModel() {
    
    val playerState: StateFlow<PlayerState> = playerManager.playerState
    
    private val _isPreparingPlaylist = MutableStateFlow(false)
    val isPreparingPlaylist: StateFlow<Boolean> = _isPreparingPlaylist.asStateFlow()
    
    private val _showSleepTimerDialog = MutableStateFlow(false)
    val showSleepTimerDialog: StateFlow<Boolean> = _showSleepTimerDialog.asStateFlow()
    
    init {
        // Load saved sleep timer preferences
        loadSleepTimerPreferences()
    }
    
    /**
     * Load saved sleep timer preferences from DataStore
     */
    private fun loadSleepTimerPreferences() {
        viewModelScope.launch {
            val savedDuration = sleepTimerPreferences.getLastDurationMinutes()
            val savedFadeOutEnabled = sleepTimerPreferences.getFadeOutEnabled()
            val savedFadeOutDuration = sleepTimerPreferences.getFadeOutDurationSeconds()
            
            playerManager.updateSleepTimerSettings(
                SleepTimerSettings(
                    enabled = false,
                    durationMinutes = savedDuration,
                    remainingMillis = 0,
                    pauseAtEnd = true,
                    fadeOutEnabled = savedFadeOutEnabled,
                    fadeOutDurationSeconds = savedFadeOutDuration
                )
            )
        }
    }
    
    /**
     * Play a single video item (plays all parts if multi-part video)
     */
    fun playVideo(item: VideoSearchItem) {
        viewModelScope.launch {
            _isPreparingPlaylist.value = true
            
            repository.preparePlaylistItems(item).fold(
                onSuccess = { playlistItems ->
                    playerManager.setPlaylist(playlistItems, 0)
                },
                onFailure = { error ->
                    // Handle error - could emit to a separate error flow
                }
            )
            
            _isPreparingPlaylist.value = false
        }
    }
    
    /**
     * Play multiple videos as playlist - starts playing immediately,
     * loads remaining items in background.
     * Multi-part videos will have all their parts added to the playlist.
     */
    fun playPlaylist(items: List<VideoSearchItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        
        viewModelScope.launch {
            _isPreparingPlaylist.value = true
            
            // First, prepare and play the selected item immediately (all parts)
            val selectedItem = items[startIndex]
            val preparedItems = repository.preparePlaylistItems(selectedItem).getOrNull()
            
            if (!preparedItems.isNullOrEmpty()) {
                // Start playing immediately with the first video's parts
                playerManager.setPlaylist(preparedItems, 0)
                _isPreparingPlaylist.value = false
                
                // Then load the rest of the playlist in background
                loadRemainingPlaylistItems(items, startIndex)
            } else {
                _isPreparingPlaylist.value = false
                // Handle error - could try next item or show error
            }
        }
    }
    
    /**
     * Load remaining playlist items in background and append to playlist.
     * Each video may have multiple parts, all of which will be added.
     */
    private fun loadRemainingPlaylistItems(items: List<VideoSearchItem>, startIndex: Int) {
        viewModelScope.launch {
            // Items before the start index
            val itemsBefore = items.subList(0, startIndex)
            // Items after the start index
            val itemsAfter = if (startIndex + 1 < items.size) {
                items.subList(startIndex + 1, items.size)
            } else {
                emptyList()
            }
            
            // Load items after the current one first (more likely to be played next)
            for (item in itemsAfter) {
                repository.preparePlaylistItems(item).fold(
                    onSuccess = { playlistItems ->
                        // Append all parts of this video
                        playerManager.appendToPlaylist(playlistItems)
                    },
                    onFailure = {
                        // Skip failed items
                    }
                )
            }
            
            // Then load items before (for "previous" functionality)
            if (itemsBefore.isNotEmpty()) {
                val preparedItemsBefore = mutableListOf<PlaylistItem>()
                for (item in itemsBefore) {
                    repository.preparePlaylistItems(item).fold(
                        onSuccess = { playlistItems ->
                            preparedItemsBefore.addAll(playlistItems)
                        },
                        onFailure = {
                            // Skip failed items
                        }
                    )
                }
                
                // Insert all items before at the beginning of the playlist
                if (preparedItemsBefore.isNotEmpty()) {
                    playerManager.insertAtBeginning(preparedItemsBefore)
                }
            }
        }
    }
    
    /**
     * Add items to current playlist (includes all parts for multi-part videos)
     */
    fun addToPlaylist(items: List<VideoSearchItem>) {
        viewModelScope.launch {
            val currentPlaylist = playerState.value.playlist.toMutableList()
            
            for (item in items) {
                repository.preparePlaylistItems(item).fold(
                    onSuccess = { playlistItems ->
                        currentPlaylist.addAll(playlistItems)
                    },
                    onFailure = {
                        // Skip failed items
                    }
                )
            }
            
            playerManager.setPlaylist(currentPlaylist, playerState.value.currentIndex)
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }
    
    /**
     * Play
     */
    fun play() {
        playerManager.play()
    }
    
    /**
     * Pause
     */
    fun pause() {
        playerManager.pause()
    }
    
    /**
     * Play next track
     */
    fun playNext() {
        playerManager.playNext()
    }
    
    /**
     * Play previous track
     */
    fun playPrevious() {
        playerManager.playPrevious()
    }
    
    /**
     * Seek to position
     */
    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }
    
    /**
     * Seek to percentage
     */
    fun seekToPercent(percent: Float) {
        val duration = playerState.value.duration
        if (duration > 0) {
            playerManager.seekTo((duration * percent).toLong())
        }
    }
    
    /**
     * Play item at index
     */
    fun playAtIndex(index: Int) {
        playerManager.playAtIndex(index)
    }
    
    // ==================== Sleep Timer ====================
    
    /**
     * Show sleep timer dialog
     */
    fun showSleepTimerDialog() {
        _showSleepTimerDialog.value = true
    }
    
    /**
     * Hide sleep timer dialog
     */
    fun hideSleepTimerDialog() {
        _showSleepTimerDialog.value = false
    }
    
    /**
     * Start sleep timer
     */
    fun startSleepTimer(durationMinutes: Int) {
        playerManager.startSleepTimer(durationMinutes)
        _showSleepTimerDialog.value = false
        
        // Save the duration for next session
        viewModelScope.launch {
            sleepTimerPreferences.saveLastDuration(durationMinutes)
        }
    }
    
    /**
     * Save sleep timer settings and start/restart the timer
     */
    fun saveSleepTimerSettings(durationMinutes: Int, fadeOutEnabled: Boolean) {
        _showSleepTimerDialog.value = false
        
        // Save preferences
        viewModelScope.launch {
            sleepTimerPreferences.saveLastDuration(durationMinutes)
            sleepTimerPreferences.saveFadeOutEnabled(fadeOutEnabled)
        }
        
        // Update fade out setting
        val currentSettings = playerState.value.sleepTimer
        playerManager.updateSleepTimerSettings(
            currentSettings.copy(
                durationMinutes = durationMinutes,
                fadeOutEnabled = fadeOutEnabled
            )
        )
        
        // Always start/restart the timer with the new duration
        playerManager.startSleepTimer(durationMinutes)
    }
    
    /**
     * Stop sleep timer
     */
    fun stopSleepTimer() {
        playerManager.stopSleepTimer()
    }
    
    /**
     * Add time to sleep timer
     */
    fun addTimeToSleepTimer(minutes: Int) {
        playerManager.addTimeToSleepTimer(minutes)
    }
    
    /**
     * Update sleep timer settings
     */
    fun updateSleepTimerSettings(settings: SleepTimerSettings) {
        playerManager.updateSleepTimerSettings(settings)
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        playerManager.clearError()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't release player here as it's singleton and shared
    }
}
