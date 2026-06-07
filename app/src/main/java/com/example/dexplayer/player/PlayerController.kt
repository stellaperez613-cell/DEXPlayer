@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.dexplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.dexplayer.player.PlaylistItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.dexplayer.util.DexLog
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * PlayerController — conecta con PlaybackService vía MediaController.
 *
 * Fuente de verdad para el estado del reproductor. El ViewModel escucha
 * [onCurrentIndexChanged] para sincronizar su propio estado cada vez que
 * Media3 transiciona entre ítems (skip manual, notificación, lock screen,
 * fin natural de pista, shuffle).
 */
@UnstableApi
class PlayerController(private val context: Context) {

    val state = PlayerState()

    // ── Callbacks hacia el ViewModel ──────────────────────────────────────────

    /**
     * Llega cada vez que ExoPlayer cambia el ítem activo — por cualquier razón.
     * El ViewModel debe usarlo para mantener _currentIndex y metadata en sync.
     * @param newIndex  Índice en la cola de ExoPlayer (fuente de verdad)
     * @param uri       URI del nuevo ítem (puede ser null si ExoPlayer aún no la tiene)
     */
    var onCurrentIndexChanged: ((newIndex: Int, uri: Uri?) -> Unit)? = null

    /** Llega cuando el ítem termina de forma natural (STATE_ENDED). */
    var onTrackEnded: (() -> Unit)? = null

    // ── Internos ──────────────────────────────────────────────────────────────

    private val scope             = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob:  Job? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller:   MediaController? = null

    private val POLL_PLAYING_MS = 500L
    private val POLL_PAUSED_MS  = 2_000L

    /** Índice actual según ExoPlayer — siempre más fiable que el del ViewModel. */
    val currentMediaItemIndex: Int get() = controller?.currentMediaItemIndex ?: -1

    val player: Player? get() = controller

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        DexLog.section("PlayerController.init")
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                DexLog.i("PlayerController", "MediaController conectado OK")
                setupListener()
                restartProgressTracking()
            } catch (e: Exception) {
                DexLog.e("PlayerController", "MediaController FALLÓ conexión", e)
            }
        }, Executors.newSingleThreadExecutor())
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    private fun setupListener() {
        val ctrl = controller ?: return
        ctrl.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                DexLog.d("PlayerController",
                    "onIsPlayingChanged=$isPlaying exoIdx=${ctrl.currentMediaItemIndex}")
                state.isPlaying = isPlaying
                restartProgressTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = when (playbackState) {
                    Player.STATE_IDLE      -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY     -> "READY"
                    Player.STATE_ENDED     -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                DexLog.d("PlayerController",
                    "onPlaybackStateChanged=$name exoIdx=${ctrl.currentMediaItemIndex}")
                state.playbackState = playbackState
                state.isBuffering   = playbackState == Player.STATE_BUFFERING
                state.isLoading     = playbackState == Player.STATE_BUFFERING

                if (playbackState == Player.STATE_ENDED) {
                    state.isPlaying = false
                    restartProgressTracking()
                    DexLog.i("PlayerController", "Pista terminó → onTrackEnded")
                    scope.launch(Dispatchers.Main) { onTrackEnded?.invoke() }
                }
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                state.isLoading = isLoading
            }

            /**
             * PUNTO CLAVE — se dispara por CUALQUIER transición de ítem:
             *   skip manual, notificación, lock screen, auriculares, seekTo(index),
             *   repeat ALL, o playlist changed.
             *
             * Notificamos al ViewModel para que corrija _currentIndex y metadata.
             */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = ctrl.currentMediaItemIndex
                val uri      = mediaItem?.localConfiguration?.uri
                val reasonName = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT           -> "REPEAT"
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO             -> "AUTO(natural)"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK             -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    else -> "UNKNOWN($reason)"
                }
                DexLog.i("PlayerController",
                    "onMediaItemTransition reason=$reasonName newIndex=$newIndex uri=$uri")

                state.mediaItem = mediaItem

                // Notificamos en Main para que el ViewModel pueda actualizar StateFlows
                scope.launch(Dispatchers.Main) {
                    onCurrentIndexChanged?.invoke(newIndex, uri)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                DexLog.d("PlayerController",
                    "onMediaMetadataChanged title=${mediaMetadata.title}")
                state.mediaMetadata = mediaMetadata
            }

            override fun onTracksChanged(tracks: Tracks) {
                state.tracks = tracks
                updateTrackLists(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                state.videoSize = videoSize
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DexLog.e("PlayerController",
                    "ExoPlayer error: ${error.errorCodeName} — ${error.message}", error)
                state.error = error.message
            }
        })
    }

    // ── Comandos de reproducción ──────────────────────────────────────────────

    fun loadUri(uri: Uri) {
        DexLog.i("PlayerController", "loadUri $uri")
        loadQueue(listOf(PlaylistItem(uri = uri, isVideo = false)), 0)
    }

    // ── Construye un MediaItem con metadatos explícitos para lock screen/notificación ──
    private fun buildMediaItem(item: PlaylistItem): MediaItem {
        val artist = item.artist.ifBlank { "Unknown Artist" }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title.ifBlank { item.uri.lastPathSegment ?: "Unknown" })
            .setArtist(artist)
            .setDisplayTitle(item.title.ifBlank { item.uri.lastPathSegment ?: "Unknown" })
            .build()
        return MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Replaces the window around the currently playing item WITHOUT interrupting playback.
     *
     * Uses removeMediaItems + addMediaItems instead of setMediaItems.
     * ExoPlayer tracks the current item by object identity — it keeps playing
     * through add/remove ops on surrounding items with zero pause/resume.
     *
     * [items]           : new desired window (already contains current item at [newCurrentExoIndex])
     * [newCurrentExoIndex] : position of the current item in [items]
     */
    fun replaceWindow(items: List<PlaylistItem>, newCurrentExoIndex: Int) {
        val ctrl = controller ?: return
        try {
            val oldCount       = ctrl.mediaItemCount
            val oldCurrentIdx  = ctrl.currentMediaItemIndex
            val newMediaItems  = items.map { buildMediaItem(it) }

            DexLog.i("PlayerController",
                "replaceWindow: oldCount=$oldCount oldCurrentIdx=$oldCurrentIdx " +
                "newSize=${items.size} newCurrentIdx=$newCurrentExoIndex")

            // Step 1: remove items AFTER current (reverse order doesn't matter here)
            if (oldCurrentIdx < oldCount - 1) {
                ctrl.removeMediaItems(oldCurrentIdx + 1, oldCount)
            }
            // Step 2: remove items BEFORE current — current is now at index 0
            if (oldCurrentIdx > 0) {
                ctrl.removeMediaItems(0, oldCurrentIdx)
            }
            // Current item is now alone at index 0, still playing

            // Step 3: add new items BEFORE current
            if (newCurrentExoIndex > 0) {
                ctrl.addMediaItems(0, newMediaItems.subList(0, newCurrentExoIndex))
            }
            // Current item is now at newCurrentExoIndex

            // Step 4: add new items AFTER current
            val afterStart = newCurrentExoIndex + 1
            if (afterStart < items.size) {
                ctrl.addMediaItems(afterStart, newMediaItems.subList(afterStart, items.size))
            }

            DexLog.i("PlayerController",
                "replaceWindow done: finalCount=${ctrl.mediaItemCount} " +
                "finalCurrentIdx=${ctrl.currentMediaItemIndex}")

        } catch (e: Exception) {
            DexLog.e("PlayerController", "replaceWindow FAILED — falling back to loadQueue", e)
            val wasPlaying = ctrl?.isPlaying ?: false
            val pos = ctrl?.currentPosition ?: 0L
            val mediaItems = items.map { buildMediaItem(it) }
            ctrl?.setMediaItems(mediaItems, newCurrentExoIndex, pos)
            ctrl?.prepare()
            if (wasPlaying) ctrl?.play()
        }
    }

    /**
     * Replaces the ExoPlayer queue with [items] using moveMediaItem ops only.
     * The currently playing item is NEVER touched — no interruption, no pause.
     * Used for shuffle/unshuffle where the current song must keep playing.
     *
     * [currentExoIndex] is the index of the currently playing item in [items].
     */
    fun syncWindow(items: List<PlaylistItem>, currentExoIndex: Int) {
        val ctrl = controller ?: return
        try {
            val n = items.size
            if (n == 0) return

            val playerCount = ctrl.mediaItemCount

            // If queue size differs, fall back to full reload (shouldn't happen normally)
            if (playerCount != n) {
                DexLog.w("PlayerController",
                    "syncWindow: size mismatch player=$playerCount vs target=$n — falling back to loadQueue")
                // keep playing
                val wasPlaying = ctrl.isPlaying
                val mediaItems = items.map { buildMediaItem(it) }
                ctrl.setMediaItems(mediaItems, currentExoIndex, ctrl.currentPosition)
                ctrl.prepare()
                if (wasPlaying) ctrl.play()
                return
            }

            DexLog.i("PlayerController",
                "syncWindow n=$n currentExoIndex=$currentExoIndex exoIdx_before=${ctrl.currentMediaItemIndex}")

            // Insertion sort using moveMediaItem — same algorithm as old syncQueue
            val current = (0 until n).map { i ->
                ctrl.getMediaItemAt(i).localConfiguration?.uri
            }.toMutableList()

            var moves = 0
            for (destIdx in 0 until n) {
                val targetUri = items[destIdx].uri
                val srcIdx    = current.indexOf(targetUri)
                if (srcIdx == -1) {
                    DexLog.e("PlayerController", "syncWindow: URI not found for destIdx=$destIdx: $targetUri")
                    continue
                }
                if (srcIdx != destIdx) {
                    ctrl.moveMediaItem(srcIdx, destIdx)
                    current.removeAt(srcIdx)
                    current.add(destIdx, targetUri)
                    moves++
                }
            }

            DexLog.i("PlayerController",
                "syncWindow done: moves=$moves exoIdx_after=${ctrl.currentMediaItemIndex}")

        } catch (e: Exception) {
            DexLog.e("PlayerController", "syncWindow FAILED", e)
        }
    }

    fun loadQueue(items: List<PlaylistItem>, startIndex: Int, play: Boolean = true, positionMs: Long = 0L) {
        DexLog.section("PlayerController.loadQueue")
        DexLog.i("PlayerController", "loadQueue size=${items.size} startIndex=$startIndex play=$play positionMs=$positionMs")
        items.forEachIndexed { i, it -> DexLog.d("PlayerController", "  [$i] ${it.uri}") }

        val ctrl = controller
        if (ctrl == null) {
            DexLog.w("PlayerController", "loadQueue: controller null — reintentando en 300ms")
            scope.launch { delay(300); loadQueue(items, startIndex, play, positionMs) }
            return
        }
        try {
            state.error = null
            val mediaItems = items.map { buildMediaItem(it) }
            ctrl.setMediaItems(mediaItems, startIndex, positionMs)
            ctrl.prepare()
            if (play) ctrl.play() else ctrl.pause()
            state.currentPosition  = 0L
            state.duration         = 0L
            state.bufferedPosition = 0L
            DexLog.i("PlayerController",
                "loadQueue OK — exoIdx=${ctrl.currentMediaItemIndex}")
        } catch (e: Exception) {
            DexLog.e("PlayerController", "loadQueue FALLÓ", e)
            state.error = e.message
        }
    }

    fun loadPath(path: String) = loadUri(Uri.parse(path))

    /**
     * Salta al índice dentro de la ventana actual (exo index, no lógico).
     * Con lazy queue el ViewModel ya garantiza que el índice es válido.
     */
    fun seekToQueueItem(exoIndex: Int, play: Boolean = true) {
        val ctrl = controller ?: run {
            DexLog.w("PlayerController", "seekToQueueItem: controller null — reintentando")
            scope.launch { delay(300); seekToQueueItem(exoIndex, play) }
            return
        }
        try {
            DexLog.i("PlayerController",
                "seekToQueueItem exoIndex=$exoIndex playerCount=${ctrl.mediaItemCount} play=$play")
            ctrl.seekTo(exoIndex, 0L)
            if (play) ctrl.play() else ctrl.pause()
            DexLog.i("PlayerController", "seekToQueueItem OK — exoIdx=${ctrl.currentMediaItemIndex}")
        } catch (e: Exception) {
            DexLog.e("PlayerController", "seekToQueueItem FALLÓ", e)
        }
    }

    /**
     * Reordena la cola usando [moveMediaItem] — sin interrumpir la reproducción.
     *
     * [setMediaItems] hace reset completo del buffer (lag + posición perdida).
     * [moveMediaItem] reorganiza sólo la lista interna; el ítem activo sigue
     * bufereado sin interrupción ni rebuffering.
     *
     * Algoritmo: insertion sort — para cada posición destino i buscamos en la
     * cola actual el URI correcto y lo movemos con moveMediaItem.
     */
    fun syncQueue(items: List<PlaylistItem>, currentIndex: Int) {
        val ctrl = controller ?: run {
            DexLog.e("PlayerController", "syncQueue: controller null — abortando")
            return
        }
        try {
            val n = items.size
            if (n == 0) return

            val playerCount = ctrl.mediaItemCount
            if (playerCount != n) {
                DexLog.e("PlayerController",
                    "syncQueue: tamaño diferente player=$playerCount vs target=$n " +
                    "— cola inconsistente, imposible reordenar sin resetear")
                return
            }

            DexLog.section("PlayerController.syncQueue")
            DexLog.i("PlayerController",
                "syncQueue n=$n targetCurrentIdx=$currentIndex " +
                "exoIdx_antes=${ctrl.currentMediaItemIndex}")

            // Snapshot mutable de la cola actual
            DexLog.d("PlayerController", "Cola ExoPlayer ANTES:")
            val current = (0 until n).map { i ->
                val uri = ctrl.getMediaItemAt(i).localConfiguration?.uri
                DexLog.d("PlayerController", "  [$i] $uri")
                uri
            }.toMutableList()

            DexLog.d("PlayerController", "Cola OBJETIVO:")
            items.forEachIndexed { i, it -> DexLog.d("PlayerController", "  [$i] ${it.uri}") }

            var moves = 0
            for (destIdx in 0 until n) {
                val targetUri = items[destIdx].uri
                val srcIdx    = current.indexOf(targetUri)

                if (srcIdx == -1) {
                    DexLog.e("PlayerController",
                        "syncQueue: URI NO ENCONTRADA para destIdx=$destIdx: $targetUri " +
                        "— saltando (cola puede quedar parcialmente desordenada)")
                    continue
                }
                if (srcIdx != destIdx) {
                    DexLog.d("PlayerController",
                        "  moveMediaItem $srcIdx→$destIdx $targetUri")
                    ctrl.moveMediaItem(srcIdx, destIdx)
                    current.removeAt(srcIdx)
                    current.add(destIdx, targetUri)
                    moves++
                }
            }

            val finalExoIdx = ctrl.currentMediaItemIndex
            val finalExoUri = ctrl.currentMediaItem?.localConfiguration?.uri
            DexLog.i("PlayerController",
                "syncQueue completado: moves=$moves " +
                "exoIdx_despues=$finalExoIdx exoUri=$finalExoUri targetIdx=$currentIndex")

            if (finalExoIdx != currentIndex) {
                DexLog.w("PlayerController",
                    "ADVERTENCIA syncQueue: índice final ExoPlayer ($finalExoIdx) " +
                    "!= objetivo ($currentIndex) — posible desincronización residual")
            }

        } catch (e: Exception) {
            DexLog.e("PlayerController", "syncQueue FALLÓ", e)
        }
    }

    // ── Controles básicos ─────────────────────────────────────────────────────

    fun togglePlayPause() {
        val ctrl = controller ?: return
        DexLog.d("PlayerController",
            "togglePlayPause isPlaying=${ctrl.isPlaying} idx=${ctrl.currentMediaItemIndex}")
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun pause() {
        DexLog.d("PlayerController", "pause()")
        controller?.pause()
    }

    fun stop() {
        DexLog.i("PlayerController", "stop()")
        val ctrl = controller ?: return
        ctrl.stop()
        state.isPlaying       = false
        state.playbackState   = Player.STATE_IDLE
        state.currentPosition = 0L
        state.duration        = 0L
        state.error           = null
        restartProgressTracking()
    }

    fun seekToPercent(percent: Float) {
        val ctrl   = controller ?: return
        val dur    = ctrl.duration.takeIf { it > 0 } ?: return
        val target = (dur * percent).toLong()
        DexLog.d("PlayerController", "seekToPercent ${percent}% → ${target}ms")
        ctrl.seekTo(target)
        state.currentPosition = target
    }

    fun seekForward(seconds: Int) {
        val ctrl   = controller ?: return
        val target = ctrl.currentPosition + seconds * 1000L
        ctrl.seekTo(target)
        state.currentPosition = target
    }

    fun seekBackward(seconds: Int) {
        val ctrl   = controller ?: return
        val target = maxOf(0L, ctrl.currentPosition - seconds * 1000L)
        ctrl.seekTo(target)
        state.currentPosition = target
    }

    // ── Volumen / velocidad ───────────────────────────────────────────────────

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        state.volume  = clamped
        state.isMuted = clamped == 0f
    }

    fun toggleMute() {
        val muted     = !state.isMuted
        state.isMuted = muted
        state.volume  = if (muted) 0f else state.volume.takeIf { it > 0f } ?: 1f
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        state.playbackSpeed = speed
    }

    // ── Pistas de audio/subtítulos ────────────────────────────────────────────

    private fun updateTrackLists(tracks: Tracks) {
        val audio    = mutableListOf<TrackInfo>()
        val subtitle = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            val format = group.getTrackFormat(0)
            val mime   = format.sampleMimeType ?: ""
            val lang   = format.language
            val label  = format.label ?: lang ?: "Track ${groupIndex + 1}"
            val info   = TrackInfo(
                index      = groupIndex,
                groupIndex = groupIndex,
                name       = label,
                language   = lang,
                isSelected = group.isSelected,
                mimeType   = mime
            )
            when {
                mime.startsWith("audio")                              -> audio.add(info)
                mime.startsWith("text") || mime.contains("subtitle") -> subtitle.add(info)
            }
        }
        state.audioTracks    = audio
        state.subtitleTracks = subtitle
    }

    // ── Tracking de progreso ──────────────────────────────────────────────────
    // Corre siempre (playing o pausado). Intervalo adaptativo para ahorrar batería.

    private fun restartProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null) {
                    state.currentPosition  = ctrl.currentPosition
                    state.duration         = ctrl.duration.takeIf { it > 0 } ?: 0L
                    state.bufferedPosition = ctrl.bufferedPosition
                }
                delay(if (state.isPlaying) POLL_PLAYING_MS else POLL_PAUSED_MS)
            }
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    fun release() {
        DexLog.i("PlayerController", "release()")
        progressJob?.cancel()
        scope.cancel()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
}
