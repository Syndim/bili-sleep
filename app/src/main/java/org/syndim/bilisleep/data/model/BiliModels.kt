package org.syndim.bilisleep.data.model

import com.google.gson.annotations.SerializedName

/**
 * Bilibili API response wrapper
 */
data class BiliResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?
)

/**
 * Search result from Bilibili
 */
data class SearchResult(
    @SerializedName("seid") val seid: String = "",
    @SerializedName("page") val page: Int = 1,
    @SerializedName("pagesize") val pageSize: Int = 20,
    @SerializedName("numResults") val numResults: Int = 0,
    @SerializedName("numPages") val numPages: Int = 0,
    @SerializedName("result") val result: List<VideoSearchItem>? = null
)

/**
 * Video item from search results
 */
data class VideoSearchItem(
    @SerializedName("type") val type: String = "",
    @SerializedName("id") val id: Long = 0,
    @SerializedName("author") val author: String = "",
    @SerializedName("mid") val mid: Long = 0,
    @SerializedName("typeid") val typeId: String = "",
    @SerializedName("typename") val typeName: String = "",
    @SerializedName("arcurl") val arcUrl: String = "",
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("bvid") val bvid: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("arcrank") val arcRank: String = "",
    @SerializedName("pic") val pic: String = "",
    @SerializedName("play") val play: Long = 0,
    @SerializedName("video_review") val videoReview: Int = 0,
    @SerializedName("favorites") val favorites: Int = 0,
    @SerializedName("tag") val tag: String = "",
    @SerializedName("review") val review: Int = 0,
    @SerializedName("pubdate") val pubDate: Long = 0,
    @SerializedName("senddate") val sendDate: Long = 0,
    @SerializedName("duration") val duration: String = "", // Format: "MM:SS" or "HH:MM:SS"
    @SerializedName("badgepay") val badgePay: Boolean = false,
    @SerializedName("hit_columns") val hitColumns: List<String>? = null,
    @SerializedName("view_type") val viewType: String = "",
    @SerializedName("is_pay") val isPay: Int = 0,
    @SerializedName("is_union_video") val isUnionVideo: Int = 0,
    @SerializedName("rec_tags") val recTags: List<String>? = null,
    @SerializedName("new_rec_tags") val newRecTags: List<Any>? = null,
    @SerializedName("rank_score") val rankScore: Long = 0
) {
    /**
     * Get duration in seconds
     */
    fun getDurationSeconds(): Long {
        if (duration.isBlank()) return 0
        val parts = duration.split(":")
        return when (parts.size) {
            2 -> parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toLongOrNull() ?: 0) ?: 0
            3 -> parts[0].toLongOrNull()?.times(3600)
                ?.plus(parts[1].toLongOrNull()?.times(60) ?: 0)
                ?.plus(parts[2].toLongOrNull() ?: 0) ?: 0
            else -> 0
        }
    }
    
    /**
     * Get clean title without HTML tags
     */
    fun getCleanTitle(): String {
        return title.replace(Regex("<[^>]*>"), "")
    }
    
    /**
     * Get full cover URL
     */
    fun getCoverUrl(): String {
        return if (pic.startsWith("//")) "https:$pic" else pic
    }
}

/**
 * Video detail info
 */
data class VideoInfo(
    @SerializedName("bvid") val bvid: String = "",
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("videos") val videos: Int = 0,
    @SerializedName("tid") val tid: Int = 0,
    @SerializedName("tname") val tname: String = "",
    @SerializedName("copyright") val copyright: Int = 0,
    @SerializedName("pic") val pic: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("pubdate") val pubDate: Long = 0,
    @SerializedName("ctime") val ctime: Long = 0,
    @SerializedName("desc") val desc: String = "",
    @SerializedName("state") val state: Int = 0,
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("owner") val owner: VideoOwner? = null,
    @SerializedName("stat") val stat: VideoStat? = null,
    @SerializedName("cid") val cid: Long = 0,
    @SerializedName("pages") val pages: List<VideoPage>? = null
) {
    fun getCoverUrl(): String {
        return if (pic.startsWith("//")) "https:$pic" else pic
    }
}

data class VideoOwner(
    @SerializedName("mid") val mid: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("face") val face: String = ""
)

data class VideoStat(
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("view") val view: Long = 0,
    @SerializedName("danmaku") val danmaku: Int = 0,
    @SerializedName("reply") val reply: Int = 0,
    @SerializedName("favorite") val favorite: Int = 0,
    @SerializedName("coin") val coin: Int = 0,
    @SerializedName("share") val share: Int = 0,
    @SerializedName("like") val like: Int = 0
)

data class VideoPage(
    @SerializedName("cid") val cid: Long = 0,
    @SerializedName("page") val page: Int = 0,
    @SerializedName("from") val from: String = "",
    @SerializedName("part") val part: String = "",
    @SerializedName("duration") val duration: Long = 0
)

/**
 * Video play URL response
 */
data class PlayUrlResponse(
    @SerializedName("quality") val quality: Int = 0,
    @SerializedName("format") val format: String = "",
    @SerializedName("timelength") val timeLength: Long = 0,
    @SerializedName("accept_format") val acceptFormat: String = "",
    @SerializedName("accept_description") val acceptDescription: List<String>? = null,
    @SerializedName("accept_quality") val acceptQuality: List<Int>? = null,
    @SerializedName("video_codecid") val videoCodecId: Int = 0,
    @SerializedName("seek_param") val seekParam: String = "",
    @SerializedName("seek_type") val seekType: String = "",
    @SerializedName("durl") val durl: List<PlayUrlDurl>? = null,
    @SerializedName("dash") val dash: DashInfo? = null,
    @SerializedName("support_formats") val supportFormats: List<SupportFormat>? = null
)

data class PlayUrlDurl(
    @SerializedName("order") val order: Int = 0,
    @SerializedName("length") val length: Long = 0,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("ahead") val ahead: String = "",
    @SerializedName("vhead") val vhead: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("backup_url") val backupUrl: List<String>? = null
)

data class DashInfo(
    @SerializedName("duration") val duration: Long = 0,
    @SerializedName("minBufferTime") val minBufferTime: Float = 0f,
    @SerializedName("video") val video: List<DashStream>? = null,
    @SerializedName("audio") val audio: List<DashStream>? = null,
    @SerializedName("dolby") val dolby: DolbyInfo? = null,
    @SerializedName("flac") val flac: FlacInfo? = null
)

data class DashStream(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("base_url") val baseUrlAlt: String = "",
    @SerializedName("backupUrl") val backupUrl: List<String>? = null,
    @SerializedName("backup_url") val backupUrlAlt: List<String>? = null,
    @SerializedName("bandwidth") val bandwidth: Long = 0,
    @SerializedName("mimeType") val mimeType: String = "",
    @SerializedName("mime_type") val mimeTypeAlt: String = "",
    @SerializedName("codecs") val codecs: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
    @SerializedName("frameRate") val frameRate: String = "",
    @SerializedName("frame_rate") val frameRateAlt: String = "",
    @SerializedName("sar") val sar: String = "",
    @SerializedName("startWithSap") val startWithSap: Int = 0,
    @SerializedName("start_with_sap") val startWithSapAlt: Int = 0,
    @SerializedName("SegmentBase") val segmentBase: SegmentBase? = null,
    @SerializedName("segment_base") val segmentBaseAlt: SegmentBase? = null,
    @SerializedName("codecid") val codecId: Int = 0
) {
    fun getUrl(): String = baseUrl.ifBlank { baseUrlAlt }
    fun getBackups(): List<String> = backupUrl ?: backupUrlAlt ?: emptyList()
}

data class SegmentBase(
    @SerializedName("initialization") val initialization: String = "",
    @SerializedName("Initialization") val initializationAlt: String = "",
    @SerializedName("indexRange") val indexRange: String = "",
    @SerializedName("index_range") val indexRangeAlt: String = ""
)

data class DolbyInfo(
    @SerializedName("type") val type: Int = 0,
    @SerializedName("audio") val audio: List<DashStream>? = null
)

data class FlacInfo(
    @SerializedName("display") val display: Boolean = false,
    @SerializedName("audio") val audio: DashStream? = null
)

data class SupportFormat(
    @SerializedName("quality") val quality: Int = 0,
    @SerializedName("format") val format: String = "",
    @SerializedName("new_description") val newDescription: String = "",
    @SerializedName("display_desc") val displayDesc: String = "",
    @SerializedName("superscript") val superscript: String = "",
    @SerializedName("codecs") val codecs: List<String>? = null
)
