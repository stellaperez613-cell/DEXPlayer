package com.example.dexplayer.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize

/**
 * Estado observable del reproductor para Compose
 * Se actualiza automáticamente desde el Player.Listener
 */
class PlayerState {
    
    // ═══════════════════════════════════════════════════════════════
    // PLAYBACK STATE
    // ═══════════════════════════════════════════════════════════════
    
    var isPlaying by mutableStateOf(false)
        internal set
    
    var playbackState by mutableIntStateOf(Player.STATE_IDLE)
        internal set
    
    var isLoading by mutableStateOf(false)
        internal set
    
    var isBuffering by mutableStateOf(false)
        internal set
    
    // ═══════════════════════════════════════════════════════════════
    // PROGRESS
    // ═══════════════════════════════════════════════════════════════
    
    /** Posición actual en milisegundos */
    var currentPosition by mutableLongStateOf(0L)
        internal set
    
    /** Duración total en milisegundos */
    var duration by mutableLongStateOf(0L)
        internal set
    
    /** Posición del buffer en milisegundos */
    var bufferedPosition by mutableLongStateOf(0L)
        internal set
    
    /** Progreso de 0 a 1 */
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
    
    /** Progreso del buffer de 0 a 1 */
    val bufferedProgress: Float
        get() = if (duration > 0) bufferedPosition.toFloat() / duration else 0f
    
    // ═══════════════════════════════════════════════════════════════
    // AUDIO
    // ═══════════════════════════════════════════════════════════════
    
    var volume by mutableFloatStateOf(1f)
        internal set
    
    var isMuted by mutableStateOf(false)
        internal set
    
    // ═══════════════════════════════════════════════════════════════
    // VIDEO INFO
    // ═══════════════════════════════════════════════════════════════
    
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        internal set
    
    val hasVideo: Boolean
        get() = videoSize != VideoSize.UNKNOWN && videoSize.width > 0
    
    val aspectRatio: Float
        get() = if (videoSize.height > 0) {
            videoSize.width.toFloat() / videoSize.height
        } else 16f / 9f
    
    // ═══════════════════════════════════════════════════════════════
    // MEDIA INFO
    // ═══════════════════════════════════════════════════════════════
    
    var mediaItem by mutableStateOf<MediaItem?>(null)
        internal set
    
    var mediaMetadata by mutableStateOf(MediaMetadata.EMPTY)
        internal set
    
    var tracks by mutableStateOf(Tracks.EMPTY)
        internal set
    
    val title: String
        get() = mediaMetadata.title?.toString() 
            ?: mediaItem?.mediaMetadata?.title?.toString()
            ?: mediaItem?.localConfiguration?.uri?.lastPathSegment
            ?: "Unknown"
    
    val artist: String
        get() = mediaMetadata.artist?.toString() ?: ""
    
    val album: String
        get() = mediaMetadata.albumTitle?.toString() ?: ""
    
    // ═══════════════════════════════════════════════════════════════
    // TRACKS (Audio, Subtitles)
    // ═══════════════════════════════════════════════════════════════
    
    var audioTracks by mutableStateOf<List<TrackInfo>>(emptyList())
        internal set
    
    var subtitleTracks by mutableStateOf<List<TrackInfo>>(emptyList())
        internal set
    
    var selectedAudioTrack by mutableIntStateOf(-1)
        internal set
    
    var selectedSubtitleTrack by mutableIntStateOf(-1)
        internal set
    
    // ═══════════════════════════════════════════════════════════════
    // PLAYBACK SPEED
    // ═══════════════════════════════════════════════════════════════
    
    var playbackSpeed by mutableFloatStateOf(1f)
        internal set
    
    // ═══════════════════════════════════════════════════════════════
    // ERROR
    // ═══════════════════════════════════════════════════════════════
    
    var error by mutableStateOf<String?>(null)
        internal set
    
    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════
    
    val isIdle: Boolean get() = playbackState == Player.STATE_IDLE
    val isEnded: Boolean get() = playbackState == Player.STATE_ENDED
    val isReady: Boolean get() = playbackState == Player.STATE_READY
    
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
    
    val currentTimeFormatted: String get() = formatTime(currentPosition)
    val durationFormatted: String get() = formatTime(duration)
}

/**
 * Información de un track (audio o subtítulo)
 */
data class TrackInfo(
    val index: Int,
    val groupIndex: Int,
    val name: String,
    val language: String?,
    val isSelected: Boolean,
    val mimeType: String?
)
