package org.syndim.bilisleep.data.repository

import org.syndim.bilisleep.data.api.BiliApiService
import org.syndim.bilisleep.data.model.PlayUrlResponse
import org.syndim.bilisleep.data.model.PlaylistItem
import org.syndim.bilisleep.data.model.VideoInfo
import org.syndim.bilisleep.data.model.VideoSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Bilibili API operations
 */
@Singleton
class BiliRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    
    /**
     * Search videos by keyword
     */
    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<VideoSearchItem>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchVideo(
                keyword = keyword,
                page = page,
                pageSize = pageSize
            )
            
            if (response.code == 0 && response.data != null) {
                Result.success(response.data.result ?: emptyList())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get video info by bvid
     */
    suspend fun getVideoInfo(bvid: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getVideoInfo(bvid)
            
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get video info by aid
     */
    suspend fun getVideoInfoByAid(aid: Long): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getVideoInfoByAid(aid)
            
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get video play URL
     */
    suspend fun getPlayUrl(bvid: String, cid: Long): Result<PlayUrlResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPlayUrl(
                bvid = bvid,
                cid = cid,
                quality = 64, // 720P for better audio quality
                fnval = 16 // DASH format to get separate audio stream
            )
            
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get audio URL from video
     * Returns the highest quality audio stream URL
     */
    suspend fun getAudioUrl(bvid: String, cid: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val playUrlResult = getPlayUrl(bvid, cid)
            
            playUrlResult.fold(
                onSuccess = { playUrl ->
                    // Try DASH audio first (better quality)
                    val audioUrl = playUrl.dash?.audio
                        ?.maxByOrNull { it.bandwidth }
                        ?.getUrl()
                    
                    // Fallback to FLAC if available
                        ?: playUrl.dash?.flac?.audio?.getUrl()
                        
                        // Fallback to Dolby audio
                        ?: playUrl.dash?.dolby?.audio?.firstOrNull()?.getUrl()
                        
                        // Last resort: use durl (which contains video+audio)
                        ?: playUrl.durl?.firstOrNull()?.url
                    
                    if (audioUrl != null) {
                        Result.success(audioUrl)
                    } else {
                        Result.failure(Exception("No audio stream found"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Prepare playlist items for all pages of a video
     * Returns a list of PlaylistItems, one for each page/part
     */
    suspend fun preparePlaylistItems(item: VideoSearchItem): Result<List<PlaylistItem>> = withContext(Dispatchers.IO) {
        try {
            // First get video info to get all pages
            val videoInfoResult = if (item.bvid.isNotBlank()) {
                getVideoInfo(item.bvid)
            } else {
                getVideoInfoByAid(item.aid)
            }
            
            videoInfoResult.fold(
                onSuccess = { videoInfo ->
                    val pages = videoInfo.pages
                    val totalParts = pages?.size ?: 1
                    
                    if (pages.isNullOrEmpty() || pages.size == 1) {
                        // Single part video - use the original method logic
                        val cid = videoInfo.cid
                        val audioResult = getAudioUrl(videoInfo.bvid, cid)
                        
                        audioResult.fold(
                            onSuccess = { audioUrl ->
                                val playlistItem = PlaylistItem.fromVideoInfo(videoInfo).copy(
                                    audioUrl = audioUrl
                                )
                                Result.success(listOf(playlistItem))
                            },
                            onFailure = { Result.failure(it) }
                        )
                    } else {
                        // Multi-part video - create a PlaylistItem for each page
                        val playlistItems = mutableListOf<PlaylistItem>()
                        
                        for (page in pages) {
                            val audioResult = getAudioUrl(videoInfo.bvid, page.cid)
                            audioResult.fold(
                                onSuccess = { audioUrl ->
                                    val playlistItem = PlaylistItem.fromVideoInfoAndPage(
                                        videoInfo, page, totalParts
                                    ).copy(audioUrl = audioUrl)
                                    playlistItems.add(playlistItem)
                                },
                                onFailure = {
                                    // Skip failed parts but continue with others
                                }
                            )
                        }
                        
                        if (playlistItems.isNotEmpty()) {
                            Result.success(playlistItems)
                        } else {
                            Result.failure(Exception("Failed to load any parts"))
                        }
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Prepare playlist item with audio URL (returns only first part for compatibility)
     */
    suspend fun preparePlaylistItem(item: VideoSearchItem): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        preparePlaylistItems(item).fold(
            onSuccess = { items ->
                if (items.isNotEmpty()) {
                    Result.success(items.first())
                } else {
                    Result.failure(Exception("No items prepared"))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    /**
     * Prepare playlist item from video info
     */
    suspend fun preparePlaylistItemFromVideoInfo(videoInfo: VideoInfo): Result<PlaylistItem> = withContext(Dispatchers.IO) {
        try {
            val audioResult = getAudioUrl(videoInfo.bvid, videoInfo.cid)
            
            audioResult.fold(
                onSuccess = { audioUrl ->
                    val playlistItem = PlaylistItem.fromVideoInfo(videoInfo).copy(
                        audioUrl = audioUrl
                    )
                    Result.success(playlistItem)
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
