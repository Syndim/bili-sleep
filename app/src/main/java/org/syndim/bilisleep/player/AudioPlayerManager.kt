package org.syndim.bilisleep.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.syndim.bilisleep.data.model.PlaylistItem
import org.syndim.bilisleep.data.model.PlayerState
import org.syndim.bilisleep.data.model.SleepTimerSettings
import org.syndim.bilisleep.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio player manager that handles MediaController connection and sleep timer functionality.
 * Connects to MediaPlaybackService for background playback with system media controls.
 */
@Singleton
@OptIn(UnstableApi::class)
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private var sleepTimerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var fadeOutJob: Job? = null
    
    init {
        initializeController()
    }
    
    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            },
            MoreExecutors.directExecutor()
        )
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdate()
                } else {
                    stopPositionUpdate()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> onTrackEnded()
                    Player.STATE_BUFFERING -> _playerState.update { it.copy(isLoading = true) }
                    Player.STATE_READY -> _playerState.update { 
                        it.copy(
                            isLoading = false,
                            duration = mediaController?.duration ?: 0
                        ) 
                    }
                    Player.STATE_IDLE -> _playerState.update { it.copy(isLoading = false) }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playerState.update { 
                    it.copy(
                        isLoading = false, 
                        error = error.message ?: "Playback error"
                    ) 
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Update current item when track changes
                val currentIndex = mediaController?.currentMediaItemIndex ?: 0
                val playlist = _playerState.value.playlist
                if (currentIndex in playlist.indices) {
                    _playerState.update {
                        it.copy(
                            currentIndex = currentIndex,
                            currentItem = playlist[currentIndex]
                        )
                    }
                }
            }
        })
    }
    
    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _playerState.update { 
                    it.copy(currentPosition = mediaController?.currentPosition ?: 0)
                }
                delay(1000)
            }
        }
    }
    
    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
    }
    
    /**
     * Set playlist and start playing from index.
     * Automatically starts the sleep timer with saved duration.
     */
    fun setPlaylist(items: List<PlaylistItem>, startIndex: Int = 0) {
        _playerState.update { 
            it.copy(
                playlist = items,
                currentIndex = startIndex,
                currentItem = items.getOrNull(startIndex)
            )
        }
        
        if (items.isNotEmpty() && startIndex < items.size) {
            // Convert PlaylistItems to MediaItems and set on player
            val mediaItems = items.map { it.toMediaItem() }
            mediaController?.apply {
                setMediaItems(mediaItems, startIndex, 0)
                prepare()
                playWhenReady = true
            }
            
            // Auto-start sleep timer with saved duration
            val savedDuration = _playerState.value.sleepTimer.durationMinutes
            startSleepTimer(savedDuration)
        }
    }
    
    /**
     * Add items to the end of the playlist without interrupting playback
     */
    fun appendToPlaylist(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        
        _playerState.update { 
            it.copy(playlist = it.playlist + items)
        }
        
        // Add to MediaController's playlist
        val mediaItems = items.map { it.toMediaItem() }
        mediaController?.addMediaItems(mediaItems)
    }
    
    /**
     * Insert items at the beginning of the playlist without interrupting playback
     * Adjusts currentIndex to maintain correct position
     */
    fun insertAtBeginning(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        
        val currentIndex = _playerState.value.currentIndex
        
        _playerState.update { 
            it.copy(
                playlist = items + it.playlist,
                currentIndex = it.currentIndex + items.size
            )
        }
        
        // Insert at beginning of MediaController's playlist
        val mediaItems = items.map { it.toMediaItem() }
        mediaController?.addMediaItems(0, mediaItems)
    }
    
    /**
     * Insert items right after the current item
     * Used for lazy loading remaining parts of a multi-part video
     */
    fun insertAfterCurrent(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        
        val currentIndex = _playerState.value.currentIndex
        val insertPosition = currentIndex + 1
        
        _playerState.update { state ->
            val newPlaylist = state.playlist.toMutableList()
            newPlaylist.addAll(insertPosition, items)
            state.copy(playlist = newPlaylist)
        }
        
        // Insert after current item in MediaController's playlist
        val mediaItems = items.map { it.toMediaItem() }
        mediaController?.addMediaItems(insertPosition, mediaItems)
    }
    
    /**
     * Play item at specific index
     */
    fun playAtIndex(index: Int) {
        val playlist = _playerState.value.playlist
        if (index in playlist.indices) {
            _playerState.update { 
                it.copy(
                    currentIndex = index,
                    currentItem = playlist[index],
                    isLoading = true
                )
            }
            mediaController?.seekTo(index, 0)
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }
    
    /**
     * Play
     * If the sleep timer had ended, automatically restart it with the saved duration
     */
    fun play() {
        // Check if sleep timer had ended (enabled = false, remainingMillis = 0)
        // and restart it when resuming playback
        val sleepTimer = _playerState.value.sleepTimer
        if (!sleepTimer.enabled && sleepTimer.remainingMillis <= 0 && sleepTimer.durationMinutes > 0) {
            startSleepTimer(sleepTimer.durationMinutes)
        }
        
        mediaController?.play()
    }
    
    /**
     * Pause
     */
    fun pause() {
        mediaController?.pause()
    }
    
    /**
     * Play next track
     */
    fun playNext() {
        val state = _playerState.value
        if (state.hasNext) {
            playAtIndex(state.currentIndex + 1)
        }
    }
    
    /**
     * Play previous track
     */
    fun playPrevious() {
        val state = _playerState.value
        if (state.hasPrevious) {
            playAtIndex(state.currentIndex - 1)
        } else {
            // Restart current track
            seekTo(0)
        }
    }
    
    /**
     * Seek to position
     */
    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _playerState.update { it.copy(currentPosition = position) }
    }
    
    /**
     * Set volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        mediaController?.volume = volume.coerceIn(0f, 1f)
    }
    
    /**
     * Handle track end - check sleep timer or play next
     */
    private fun onTrackEnded() {
        val state = _playerState.value
        val sleepTimer = state.sleepTimer
        
        // If sleep timer is active and time is up
        if (sleepTimer.enabled && sleepTimer.remainingMillis <= 0) {
            stopSleepTimer()
            pause()
            return
        }
        
        // Note: MediaController handles next track automatically when using setMediaItems
    }
    
    // ==================== Sleep Timer ====================
    
    /**
     * Start sleep timer with duration in minutes
     */
    fun startSleepTimer(durationMinutes: Int) {
        val durationMillis = durationMinutes * 60 * 1000L
        
        _playerState.update { 
            it.copy(
                sleepTimer = it.sleepTimer.copy(
                    enabled = true,
                    durationMinutes = durationMinutes,
                    remainingMillis = durationMillis
                )
            )
        }
        
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remaining = durationMillis
            
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                
                _playerState.update {
                    it.copy(
                        sleepTimer = it.sleepTimer.copy(remainingMillis = remaining)
                    )
                }
                
                // Start fade out if enabled
                val sleepTimer = _playerState.value.sleepTimer
                if (sleepTimer.fadeOutEnabled && 
                    remaining <= sleepTimer.fadeOutDurationSeconds * 1000 &&
                    fadeOutJob == null) {
                    startFadeOut(remaining)
                }
            }
            
            // Timer ended - pause playback
            onSleepTimerEnd()
        }
    }
    
    /**
     * Stop sleep timer
     */
    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        fadeOutJob?.cancel()
        fadeOutJob = null
        
        // Restore volume
        setVolume(1f)
        
        _playerState.update {
            it.copy(
                sleepTimer = SleepTimerSettings()
            )
        }
    }
    
    /**
     * Update sleep timer settings
     */
    fun updateSleepTimerSettings(settings: SleepTimerSettings) {
        _playerState.update {
            it.copy(sleepTimer = settings)
        }
    }
    
    /**
     * Start volume fade out
     */
    private fun startFadeOut(remainingMillis: Long) {
        fadeOutJob?.cancel()
        fadeOutJob = scope.launch {
            val steps = (remainingMillis / 100).toInt()
            val volumeStep = 1f / steps
            var currentVolume = 1f
            
            repeat(steps) {
                if (!isActive) return@launch
                currentVolume -= volumeStep
                setVolume(currentVolume.coerceAtLeast(0f))
                delay(100)
            }
        }
    }
    
    /**
     * Handle sleep timer end
     */
    private fun onSleepTimerEnd() {
        pause()
        setVolume(1f)
        
        _playerState.update {
            it.copy(
                sleepTimer = it.sleepTimer.copy(
                    enabled = false,
                    remainingMillis = 0
                )
            )
        }
        
        fadeOutJob?.cancel()
        fadeOutJob = null
    }
    
    /**
     * Add time to sleep timer
     */
    fun addTimeToSleepTimer(minutes: Int) {
        val addMillis = minutes * 60 * 1000L
        _playerState.update {
            it.copy(
                sleepTimer = it.sleepTimer.copy(
                    remainingMillis = it.sleepTimer.remainingMillis + addMillis
                )
            )
        }
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _playerState.update { it.copy(error = null) }
    }
    
    /**
     * Release player resources
     */
    fun release() {
        scope.cancel()
        sleepTimerJob?.cancel()
        positionUpdateJob?.cancel()
        fadeOutJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
    
    /**
     * Convert PlaylistItem to MediaItem with metadata
     */
    private fun PlaylistItem.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(bvid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(author)
                    .setArtworkUri(android.net.Uri.parse(coverUrl))
                    .build()
            )
            .build()
    }
}
