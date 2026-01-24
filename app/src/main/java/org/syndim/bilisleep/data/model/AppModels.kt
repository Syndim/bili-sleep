package org.syndim.bilisleep.data.model

/**
 * Playlist item for the audio player
 */
data class PlaylistItem(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val author: String,
    val coverUrl: String,
    val duration: Long, // in seconds
    val audioUrl: String = "",
    val partNumber: Int = 1,      // Part number (1-based)
    val totalParts: Int = 1,      // Total number of parts in the video
    val partTitle: String? = null // Part title (null if single part)
) {
    /**
     * Get display title - includes part info for multi-part videos
     */
    fun getDisplayTitle(): String {
        return if (totalParts > 1 && !partTitle.isNullOrBlank()) {
            "$title - $partTitle"
        } else if (totalParts > 1) {
            "$title (P$partNumber)"
        } else {
            title
        }
    }
    
    companion object {
        fun fromVideoSearchItem(item: VideoSearchItem, cid: Long = 0): PlaylistItem {
            return PlaylistItem(
                bvid = item.bvid,
                aid = item.aid,
                cid = cid,
                title = item.getCleanTitle(),
                author = item.author,
                coverUrl = item.getCoverUrl(),
                duration = item.getDurationSeconds()
            )
        }
        
        fun fromVideoInfo(info: VideoInfo): PlaylistItem {
            return PlaylistItem(
                bvid = info.bvid,
                aid = info.aid,
                cid = info.cid,
                title = info.title,
                author = info.owner?.name ?: "",
                coverUrl = info.getCoverUrl(),
                duration = info.duration,
                totalParts = info.pages?.size ?: 1
            )
        }
        
        /**
         * Create a PlaylistItem from a VideoInfo and a specific page
         */
        fun fromVideoInfoAndPage(info: VideoInfo, page: VideoPage, totalParts: Int): PlaylistItem {
            return PlaylistItem(
                bvid = info.bvid,
                aid = info.aid,
                cid = page.cid,
                title = info.title,
                author = info.owner?.name ?: "",
                coverUrl = info.getCoverUrl(),
                duration = page.duration,
                partNumber = page.page,
                totalParts = totalParts,
                partTitle = page.part.takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * Sleep timer settings
 */
data class SleepTimerSettings(
    val enabled: Boolean = false,
    val durationMinutes: Int = 30,
    val remainingMillis: Long = 0,
    val pauseAtEnd: Boolean = true,
    val fadeOutEnabled: Boolean = true,
    val fadeOutDurationSeconds: Int = 10,
    val justEnded: Boolean = false // True when timer just ended (for one-shot navigation trigger)
) {
    companion object {
        val PRESET_DURATIONS = listOf(15, 30, 45, 60, 90, 120)
    }
}

/**
 * Player state
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val currentItem: PlaylistItem? = null,
    val playlist: List<PlaylistItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sleepTimer: SleepTimerSettings = SleepTimerSettings()
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
    
    val hasNext: Boolean
        get() = currentIndex < playlist.size - 1
    
    val hasPrevious: Boolean
        get() = currentIndex > 0
}

/**
 * UI state for search screen
 */
sealed class SearchUiState {
    object Initial : SearchUiState()
    object Loading : SearchUiState()
    data class Success(
        val items: List<VideoSearchItem>,
        val query: String,
        val page: Int,
        val hasMore: Boolean
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
