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
    
    // Track which videos have had their remaining parts loaded (by bvid)
    private val loadedRemainingParts = mutableSetOf<String>()
    
    init {
        // Load saved sleep timer preferences
        loadSleepTimerPreferences()
        // Observe track changes for lazy loading
        observeTrackChanges()
    }
    
    /**
     * Observe track changes to lazy load remaining parts when a new video starts
     */
    private fun observeTrackChanges() {
        viewModelScope.launch {
            var previousBvid: String? = null
            
            playerState.collect { state ->
                val currentItem = state.currentItem
                if (currentItem != null && currentItem.bvid != previousBvid) {
                    previousBvid = currentItem.bvid
                    
                    // Check if this is the first part of a multi-part video
                    // and we haven't loaded remaining parts yet
                    if (currentItem.partNumber == 1 && 
                        currentItem.totalParts > 1 &&
                        !loadedRemainingParts.contains(currentItem.bvid)) {
                        loadRemainingPartsForCurrentVideo(currentItem.bvid)
                    }
                }
            }
        }
    }
    
    /**
     * Load remaining parts for the current video and insert them after the current item
     */
    private fun loadRemainingPartsForCurrentVideo(bvid: String) {
        viewModelScope.launch {
            // Mark as loading to prevent duplicate loads
            loadedRemainingParts.add(bvid)
            
            repository.prepareRemainingParts(bvid).fold(
                onSuccess = { remainingParts ->
                    if (remainingParts.isNotEmpty()) {
                        playerManager.insertAfterCurrent(remainingParts)
                    }
                },
                onFailure = {
                    // Remove from loaded set so it can be retried
                    loadedRemainingParts.remove(bvid)
                }
            )
        }
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
     * Play a single video item (lazy loads remaining parts when playback starts)
     */
    fun playVideo(item: VideoSearchItem) {
        viewModelScope.launch {
            _isPreparingPlaylist.value = true
            
            // Only load the first part initially
            repository.prepareFirstPart(item).fold(
                onSuccess = { playlistItem ->
                    // Clear the loaded parts tracker for fresh playback
                    loadedRemainingParts.clear()
                    playerManager.setPlaylist(listOf(playlistItem), 0)
                    // Remaining parts will be loaded automatically via observeTrackChanges
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
     * Only loads the first part of each video; remaining parts are lazy loaded when played.
     */
    fun playPlaylist(items: List<VideoSearchItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        
        viewModelScope.launch {
            _isPreparingPlaylist.value = true
            
            // Clear the loaded parts tracker for fresh playback
            loadedRemainingParts.clear()
            
            // First, prepare and play the selected item immediately (first part only)
            val selectedItem = items[startIndex]
            val preparedItem = repository.prepareFirstPart(selectedItem).getOrNull()
            
            if (preparedItem != null) {
                // Start playing immediately with just the first part
                playerManager.setPlaylist(listOf(preparedItem), 0)
                _isPreparingPlaylist.value = false
                
                // Remaining parts will be lazy loaded via observeTrackChanges
                // Then load the rest of the playlist in background (first parts only)
                loadRemainingPlaylistItems(items, startIndex)
            } else {
                _isPreparingPlaylist.value = false
                // Handle error - could try next item or show error
            }
        }
    }
    
    /**
     * Load remaining playlist items in background and append to playlist.
     * Only loads the first part of each video; remaining parts are lazy loaded when played.
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
                repository.prepareFirstPart(item).fold(
                    onSuccess = { playlistItem ->
                        // Append first part of this video
                        playerManager.appendToPlaylist(listOf(playlistItem))
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
                    repository.prepareFirstPart(item).fold(
                        onSuccess = { playlistItem ->
                            preparedItemsBefore.add(playlistItem)
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
     * Add items to current playlist (only first parts; remaining parts lazy loaded when played)
     */
    fun addToPlaylist(items: List<VideoSearchItem>) {
        viewModelScope.launch {
            for (item in items) {
                repository.prepareFirstPart(item).fold(
                    onSuccess = { playlistItem ->
                        playerManager.appendToPlaylist(listOf(playlistItem))
                    },
                    onFailure = {
                        // Skip failed items
                    }
                )
            }
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
