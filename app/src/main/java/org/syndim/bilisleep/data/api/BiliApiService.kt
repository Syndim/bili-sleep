package org.syndim.bilisleep.data.api

import org.syndim.bilisleep.data.model.BiliResponse
import org.syndim.bilisleep.data.model.PlayUrlResponse
import org.syndim.bilisleep.data.model.SearchResult
import org.syndim.bilisleep.data.model.VideoInfo
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Bilibili API interface
 */
interface BiliApiService {
    
    /**
     * Search videos
     */
    @GET("x/web-interface/search/type")
    suspend fun searchVideo(
        @Query("keyword") keyword: String,
        @Query("search_type") searchType: String = "video",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("order") order: String = "pubdate", // pubdate: newest first, totalrank: by relevance
        @Header("Referer") referer: String = "https://www.bilibili.com",
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): BiliResponse<SearchResult>
    
    /**
     * Get video info by bvid
     */
    @GET("x/web-interface/view")
    suspend fun getVideoInfo(
        @Query("bvid") bvid: String,
        @Header("Referer") referer: String = "https://www.bilibili.com",
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): BiliResponse<VideoInfo>
    
    /**
     * Get video info by aid
     */
    @GET("x/web-interface/view")
    suspend fun getVideoInfoByAid(
        @Query("aid") aid: Long,
        @Header("Referer") referer: String = "https://www.bilibili.com",
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): BiliResponse<VideoInfo>
    
    /**
     * Get video play URL
     */
    @GET("x/player/playurl")
    suspend fun getPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") quality: Int = 64, // 64: 720P, 80: 1080P, 16: 360P
        @Query("fnval") fnval: Int = 16, // 16: DASH format
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Header("Referer") referer: String = "https://www.bilibili.com",
        @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
    ): BiliResponse<PlayUrlResponse>
    
    companion object {
        const val BASE_URL = "https://api.bilibili.com/"
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
