package org.syndim.bilisleep.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    
    // Pending playlist items that haven't had their URLs loaded yet
    // Key: index in the logical playlist, Value: VideoSearchItem to load
    private val pendingPlaylistItems = mutableMapOf<Int, VideoSearchItem>()
    
    // Track which playlist indices have been loaded (by their logical index)
    private val loadedPlaylistIndices = mutableSetOf<Int>()
    
    init {
        // Load saved sleep timer preferences
        loadSleepTimerPreferences()
        // Observe track changes for lazy loading
        observeTrackChanges()
    }
    
    /**
     * Observe track changes to lazy load remaining parts when a new video starts
     * and preload the next item's URL
     */
    private fun observeTrackChanges() {
        viewModelScope.launch {
            var previousBvid: String? = null
            var previousIndex: Int? = null
            
            playerState.collect { state ->
                val currentItem = state.currentItem
                val currentIndex = state.currentIndex
                
                if (currentIndex != previousIndex) {
                    previousIndex = currentIndex
                    preloadNextItem(currentIndex + 1)
                }
                
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
     * Preload the URL for a pending playlist item at the given index
     */
    private fun preloadNextItem(index: Int) {
        val pendingItem = pendingPlaylistItems[index] ?: return
        if (loadedPlaylistIndices.contains(index)) return
        
        viewModelScope.launch {
            loadedPlaylistIndices.add(index)
            pendingPlaylistItems.remove(index)
            
            repository.prepareFirstPart(pendingItem).fold(
                onSuccess = { playlistItem ->
                    playerManager.appendToPlaylist(listOf(playlistItem))
                },
                onFailure = {
                    // Remove from loaded set so it can be retried
                    loadedPlaylistIndices.remove(index)
                }
            )
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
            
            loadedRemainingParts.clear()
            pendingPlaylistItems.clear()
            loadedPlaylistIndices.clear()
            
            repository.prepareFirstPart(item).fold(
                onSuccess = { playlistItem ->
                    playerManager.setPlaylist(listOf(playlistItem), 0)
                },
                onFailure = { error ->
                }
            )
            
            _isPreparingPlaylist.value = false
        }
    }
    
    /**
     * Play multiple videos as playlist - starts playing immediately.
     * Only loads URLs for current and next item; remaining items are lazy loaded when needed.
     */
    fun playPlaylist(items: List<VideoSearchItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        
        viewModelScope.launch {
            _isPreparingPlaylist.value = true
            
            loadedRemainingParts.clear()
            pendingPlaylistItems.clear()
            loadedPlaylistIndices.clear()
            
            val selectedItem = items[startIndex]
            val preparedItem = repository.prepareFirstPart(selectedItem).getOrNull()
            
            if (preparedItem != null) {
                loadedPlaylistIndices.add(startIndex)
                playerManager.setPlaylist(listOf(preparedItem), 0)
                _isPreparingPlaylist.value = false
                
                storePendingItems(items, startIndex)
                preloadNextItem(startIndex + 1)
            } else {
                _isPreparingPlaylist.value = false
            }
        }
    }
    
    /**
     * Store remaining playlist items as pending for lazy loading
     */
    private fun storePendingItems(items: List<VideoSearchItem>, startIndex: Int) {
        items.forEachIndexed { index, item ->
            if (index != startIndex) {
                pendingPlaylistItems[index] = item
            }
        }
    }
    
    /**
     * Add items to current playlist (URLs loaded lazily when needed)
     */
    fun addToPlaylist(items: List<VideoSearchItem>) {
        val currentPlaylistSize = playerManager.playerState.value.playlist.size
        val currentPendingMax = pendingPlaylistItems.keys.maxOrNull() ?: (currentPlaylistSize - 1)
        
        items.forEachIndexed { index, item ->
            val newIndex = currentPendingMax + 1 + index
            pendingPlaylistItems[newIndex] = item
        }
        
        val currentIndex = playerManager.playerState.value.currentIndex
        preloadNextItem(currentIndex + 1)
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
    
    fun clearError() {
        playerManager.clearError()
    }
    
    fun clearSleepTimerEndedFlag() {
        playerManager.clearSleepTimerEndedFlag()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't release player here as it's singleton and shared
    }
}
