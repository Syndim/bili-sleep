package org.syndim.bilisleep.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.syndim.bilisleep.MainActivity
import org.syndim.bilisleep.data.api.BiliApiService

/**
 * MediaPlaybackService handles background audio playback with system media controls.
 * This service shows playback controls in the notification center and lock screen.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Create custom HttpDataSource with Referer header for Bilibili CDN
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BiliApiService.DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(
                mapOf("Referer" to "https://www.bilibili.com")
            )
        
        // Create MediaSourceFactory with custom data source
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)
        
        // Configure audio attributes for media playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // true = handle audio focus
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keep WiFi awake during playback
            .build()
        
        // Create PendingIntent to open the app when notification is clicked
        val sessionActivityIntent = createSessionActivityIntent()
        
        // Create MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(sessionActivityIntent)
            .build()
    }
    
    /**
     * Creates a PendingIntent that opens MainActivity with the player screen
     */
    private fun createSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Use these flags to bring existing task to front or create new one
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add extra to indicate we should navigate to player screen
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return PendingIntent.getActivity(this, 0, intent, flags)
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Stop the service if not playing
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
    
    /**
     * Custom callback for MediaSession
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            // Allow adding media items with custom URI schemes
            val updatedMediaItems = mediaItems.map { mediaItem ->
                mediaItem.buildUpon()
                    .setUri(mediaItem.requestMetadata.mediaUri ?: mediaItem.localConfiguration?.uri)
                    .build()
            }.toMutableList()
            
            return com.google.common.util.concurrent.Futures.immediateFuture(updatedMediaItems)
        }
    }
    
    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
