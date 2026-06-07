package com.example.dexplayer.player

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.dexplayer.ui.screens.LibraryFilter
import com.example.dexplayer.ui.screens.MusicSubFilter
import com.example.dexplayer.util.DeviceDetector
import com.example.dexplayer.util.DexLog
import com.example.dexplayer.util.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Repeat mode ───────────────────────────────────────────────────────────────
enum class RepeatMode { NONE, ALL, ONE }
enum class ThemeMode  { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME          = "dexplayer_prefs"
private const val KEY_EXCLUDED        = "excluded_folders"
private const val KEY_THEME           = "app_theme"
private const val KEY_DEFAULT_SHUFFLE = "default_shuffle"
private const val SEPARATOR           = "|||"

// ── Video settings keys ───────────────────────────────────────────────────────
// (persistence handled by VideoPrefsStore in VideoPrefs.kt)

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val controller = PlayerController(application)
    val state      = controller.state

    // ── Playlist management — must be declared before init ────────────────────
    val playlistRepo = PlaylistRepository(application)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _openPlaylistId = MutableStateFlow<String?>(null)
    val openPlaylistId: StateFlow<String?> = _openPlaylistId.asStateFlow()

    init {
        DexLog.section("PlayerViewModel.init")
        try {
            // Pista terminada de forma natural
            controller.onTrackEnded = { onTrackEndedNaturally() }

            // FUENTE DE VERDAD: ExoPlayer cambió de ítem por cualquier razón
            // (skip manual, notificación, lock screen, auriculares, repeat ALL)
            controller.onCurrentIndexChanged = { newIndex, uri ->
                onExoPlayerIndexChanged(newIndex, uri)
            }

            DexLog.i("PlayerViewModel", "PlayerController OK — callbacks registrados")
            reloadPlaylists()
            DexLog.i("PlayerViewModel", "Playlists cargadas: ${_playlists.value.size}")

        } catch (e: Exception) {
            DexLog.wtf("PlayerViewModel", "CRASH en init", e)
            throw e
        }
    }

    /**
     * ExoPlayer reportó un cambio de ítem activo. Corrige cualquier deriva entre
     * el estado del ViewModel y lo que realmente está sonando.
     *
     * Escenarios:
     *  1. Sin cambio real → ignorar
     *  2. Deriva de índice (URI coincide en playlist[newIndex]) → corregir índice
     *  3. URI mismatch (cola reordenada, buscar por URI) → corregir índice + metadata
     *  4. URI no encontrada → desincronización grave, loggear + fallback
     */
    private fun onExoPlayerIndexChanged(newIndex: Int, uri: Uri?) {
        val pl      = _playlist.value
        val logical = exoToLogical(newIndex)   // exo index → índice lógico en _playlist
        val vmIdx   = _currentIndex.value
        val vmUri   = _currentUri.value

        DexLog.i("PlayerViewModel",
            "onExoIndexChanged exoIdx=$newIndex logical=$logical exoUri=$uri | vmIdx=$vmIdx vmUri=$vmUri pl.size=${pl.size}")

        if (pl.isEmpty() || newIndex < 0) {
            DexLog.w("PlayerViewModel", "  playlist vacía o idx inválido — ignorando")
            return
        }
        if (_windowLoading) {
            DexLog.d("PlayerViewModel", "  ventana cargando — ignorando transición")
            return
        }

        // Caso 1: ya en sync
        if (logical == vmIdx && uri == vmUri) {
            DexLog.d("PlayerViewModel", "  ya en sync — sin cambio")
            // Deslizar ventana si estamos cerca del borde
            maybeSlideWindow(logical)
            return
        }

        // Caso 2: índice lógico diferente pero URI coincide con playlist[logical]
        val itemAtLogical = pl.getOrNull(logical)
        if (itemAtLogical != null && itemAtLogical.uri == uri) {
            if (logical != vmIdx) {
                DexLog.w("PlayerViewModel",
                    "  DERIVA ÍNDICE vmIdx=$vmIdx→logical=$logical — corrigiendo")
                _currentIndex.value = logical
                applyMetadata(itemAtLogical)
            }
            maybeSlideWindow(logical)
            return
        }

        // Caso 3: URI de ExoPlayer no coincide → buscar por URI en playlist completa
        if (uri != null) {
            val matchIdx = pl.indexOfFirst { it.uri == uri }
            if (matchIdx >= 0) {
                DexLog.w("PlayerViewModel",
                    "  URI MISMATCH: playlist[$logical]=${itemAtLogical?.uri} " +
                    "exo=$uri → encontrada en [$matchIdx] — corrigiendo")
                _currentIndex.value = matchIdx
                applyMetadata(pl[matchIdx])
                maybeSlideWindow(matchIdx)
                return
            }
        }

        // Caso 4: URI no encontrada
        DexLog.e("PlayerViewModel",
            "  DESYNC GRAVE: uri=$uri no en playlist — cola ExoPlayer y ViewModel desalineadas")
        if (logical < pl.size) {
            DexLog.w("PlayerViewModel", "  fallback: usando playlist[$logical]")
            _currentIndex.value = logical
            applyMetadata(pl[logical])
        }
    }

    fun reloadPlaylists() {
        _playlists.value = playlistRepo.loadAll()
    }

    fun createPlaylist(name: String): Playlist {
        val pl = playlistRepo.create(name)
        reloadPlaylists()
        return pl
    }

    /** Creates a playlist and immediately adds items to it — avoids race condition */
    fun createPlaylistAndAdd(name: String, items: List<PlaylistItem>): Playlist {
        val pl = playlistRepo.create(name)
        playlistRepo.addItems(pl.id, items)
        reloadPlaylists()
        return pl
    }

    fun renamePlaylist(id: String, newName: String) {
        playlistRepo.rename(id, newName)
        reloadPlaylists()
    }

    fun deletePlaylist(id: String) {
        playlistRepo.delete(id)
        reloadPlaylists()
    }

    fun addItemsToPlaylist(playlistId: String, items: List<PlaylistItem>) {
        playlistRepo.addItems(playlistId, items)
        reloadPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, itemIndex: Int) {
        playlistRepo.removeItem(playlistId, itemIndex)
        reloadPlaylists()
    }

    fun reorderPlaylist(playlistId: String, from: Int, to: Int) {
        playlistRepo.reorderItems(playlistId, from, to)
        reloadPlaylists()
    }

    fun openPlaylist(id: String)  { _openPlaylistId.value = id   }
    fun closePlaylist()           { _openPlaylistId.value = null  }

    fun exportPlaylist(id: String, destUri: Uri): Boolean {
        val pl = _playlists.value.find { it.id == id } ?: return false
        return playlistRepo.exportM3U(pl, destUri)
    }

    fun importPlaylist(sourceUri: Uri, name: String): Playlist? {
        val pl = playlistRepo.importM3U(sourceUri, name)
        if (pl != null) reloadPlaylists()
        return pl
    }

    private val _mediaType = MutableStateFlow(MediaType.UNKNOWN)
    val mediaType: StateFlow<MediaType> = _mediaType.asStateFlow()

    // ── Navegación ────────────────────────────────────────────────────────────
    private val _showingPlayer   = MutableStateFlow(false)
    val showingPlayer: StateFlow<Boolean> = _showingPlayer.asStateFlow()

    private val _showingSettings = MutableStateFlow(false)
    val showingSettings: StateFlow<Boolean> = _showingSettings.asStateFlow()

    fun showPlayer()   { _showingPlayer.value   = true  }
    fun hidePlayer()   { _showingPlayer.value   = false }
    fun showSettings() { _showingSettings.value = true  }
    fun hideSettings() { _showingSettings.value = false }

    private val _hasLoadedMedia = MutableStateFlow(false)
    val hasLoadedMedia: StateFlow<Boolean> = _hasLoadedMedia.asStateFlow()

    private val _displayTitle  = MutableStateFlow("")
    private val _displayArtist = MutableStateFlow("")
    val displayTitle:  StateFlow<String> = _displayTitle.asStateFlow()
    val displayArtist: StateFlow<String> = _displayArtist.asStateFlow()

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    // ── Library state (filter + scroll position) ──────────────────────────────
    private val _libraryFilter = MutableStateFlow(LibraryFilter.MUSIC)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()
    fun setLibraryFilter(filter: LibraryFilter) { _libraryFilter.value = filter }

    private val _musicSubFilter = MutableStateFlow(MusicSubFilter.SONGS)
    val musicSubFilter: StateFlow<MusicSubFilter> = _musicSubFilter.asStateFlow()
    fun setMusicSubFilter(sub: MusicSubFilter) { _musicSubFilter.value = sub }

    // Fix #3: persisted as plain vars — no need for StateFlow since only
    // read on initial composition of LibraryScreen, not reacted to
    var libraryScrollIndex:  Int = 0
    var libraryScrollOffset: Int = 0

    // ── Fix #2: Excluded folders — persisted in SharedPreferences ────────────
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadExcludedFromPrefs(): Set<String> {
        val raw = prefs.getString(KEY_EXCLUDED, "") ?: ""
        return if (raw.isBlank()) emptySet()
        else raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    private fun saveExcludedToPrefs(folders: Set<String>) {
        prefs.edit().putString(KEY_EXCLUDED, folders.joinToString(SEPARATOR)).apply()
    }

    private val _excludedFolders = MutableStateFlow(loadExcludedFromPrefs())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    fun addExcludedFolder(path: String) {
        val updated = _excludedFolders.value + path
        _excludedFolders.value = updated
        saveExcludedToPrefs(updated)
    }

    fun removeExcludedFolder(path: String) {
        val updated = _excludedFolders.value - path
        _excludedFolders.value = updated
        saveExcludedToPrefs(updated)
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        DexLog.i("PlayerViewModel", "Theme set to $mode")
    }

    // ── Default shuffle ───────────────────────────────────────────────────────
    private val _defaultShuffle = MutableStateFlow(
        prefs.getBoolean(KEY_DEFAULT_SHUFFLE, false)
    )
    val defaultShuffle: StateFlow<Boolean> = _defaultShuffle.asStateFlow()

    fun setDefaultShuffle(enabled: Boolean) {
        _defaultShuffle.value = enabled
        prefs.edit().putBoolean(KEY_DEFAULT_SHUFFLE, enabled).apply()
        DexLog.i("PlayerViewModel", "Default shuffle set to $enabled")
    }

    // ── Video player preferences (uses VideoPrefsStore for persistence) ────────
    private val _videoPrefs = MutableStateFlow(VideoPrefsStore.load(application))
    val videoPrefs: StateFlow<VideoPrefs> = _videoPrefs.asStateFlow()

    fun updateVideoPrefs(prefs: VideoPrefs) {
        _videoPrefs.value = prefs
        VideoPrefsStore.save(getApplication(), prefs)
        DexLog.i("PlayerViewModel", "VideoPrefs updated")
    }

    // ── Audio session ID — needed for system equalizer ────────────────────────
    val audioSessionId: Int
        get() = try {
            (controller.player as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0
        } catch (_: Exception) { 0 }

    // ── Fix #2: Shuffle ───────────────────────────────────────────────────────
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    // Preserve original order so disabling shuffle restores it
    private val _originalPlaylist = MutableStateFlow<List<PlaylistItem>>(emptyList())

    fun toggleShuffle() {
        val enabling = !_shuffleEnabled.value
        _shuffleEnabled.value = enabling
        val pl = _playlist.value
        if (pl.isEmpty()) {
            DexLog.w("PlayerViewModel", "toggleShuffle: playlist vacía — ignorando")
            return
        }

        // SIEMPRE usar el índice real de ExoPlayer como fuente de verdad.
        // _currentIndex puede estar desactualizado si el usuario saltó pistas
        // desde la notificación sin que el ViewModel se enterara todavía.
        // Always use logical vmIdx — exoIdx is window-relative and meaningless here
        val vmIdx   = _currentIndex.value
        val safeIdx = if (vmIdx >= 0 && vmIdx < pl.size) vmIdx else 0
        val exoIdx  = controller.currentMediaItemIndex   // only for logging

        DexLog.section("PlayerViewModel.toggleShuffle")
        DexLog.i("PlayerViewModel",
            "toggleShuffle enabling=$enabling exoIdx=$exoIdx vmIdx=$vmIdx safeIdx=$safeIdx pl.size=${pl.size}")

        if (enabling) {
            val currentItem = pl[safeIdx]
            DexLog.i("PlayerViewModel", "  Ítem activo: [$safeIdx] ${currentItem.uri}")

            // Ítem activo en posición 0, barajar el resto
            val rest     = pl.toMutableList().also { it.removeAt(safeIdx) }.shuffled()
            val shuffled = listOf(currentItem) + rest

            DexLog.i("PlayerViewModel", "  Cola shuffleada (${shuffled.size} ítems):")
            shuffled.forEachIndexed { i, item ->
                DexLog.d("PlayerViewModel", "    [$i] ${item.uri}")
            }

            _playlist.value     = shuffled
            _currentIndex.value = 0
            applyMetadata(currentItem)

            // Build window around index 0 (current song is already at 0 in shuffled)
            // loadWindow handles position + play state preservation
            loadWindow(0)
            DexLog.i("PlayerViewModel", "toggleShuffle ON — loadWindow(0)")

        } else {
            // Restaurar orden original buscando por URI (no por índice)
            val currentUri = _currentUri.value
            val restored   = _originalPlaylist.value

            DexLog.i("PlayerViewModel",
                "  Desactivando shuffle currentUri=$currentUri restored.size=${restored.size}")

            val newIndex = restored.indexOfFirst { it.uri == currentUri }.takeIf { it >= 0 }

            if (newIndex == null) {
                // Fallback: usar URI de ExoPlayer directamente
                DexLog.e("PlayerViewModel",
                    "  currentUri NO en _originalPlaylist — buscando URI de ExoPlayer")
                val exoUri       = controller.player?.currentMediaItem?.localConfiguration?.uri
                val fallbackIdx  = restored.indexOfFirst { it.uri == exoUri }.takeIf { it >= 0 } ?: 0
                DexLog.w("PlayerViewModel", "  exoUri=$exoUri fallbackIdx=$fallbackIdx")
                _playlist.value     = restored
                _currentIndex.value = fallbackIdx
                if (fallbackIdx < restored.size) applyMetadata(restored[fallbackIdx])
                loadWindow(fallbackIdx)
            } else {
                DexLog.i("PlayerViewModel", "  currentUri en restored[$newIndex]")
                _playlist.value     = restored
                _currentIndex.value = newIndex
                loadWindow(newIndex)
            }

            DexLog.i("PlayerViewModel",
                "toggleShuffle OFF completado vmIdx=${_currentIndex.value} " +
                "exoIdx=${controller.currentMediaItemIndex}")
        }
    }

    // ── Fix #2: Repeat ────────────────────────────────────────────────────────
    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.NONE
        }
    }

    // ── Playlist ──────────────────────────────────────────────────────────────
    private val _playlist     = MutableStateFlow<List<PlaylistItem>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)

    // ── Lazy Queue — ventana deslizante sobre la playlist completa ────────────
    // ExoPlayer solo tiene WINDOW_SIZE items. _windowOffset es el índice lógico
    // del primer item de la ventana en _playlist.
    companion object {
        private const val WINDOW_BEFORE = 10   // items antes del actual
        private const val WINDOW_AFTER  = 10   // items después del actual
        private const val WINDOW_SIZE   = WINDOW_BEFORE + 1 + WINDOW_AFTER  // 21
    }
    private var _windowOffset = 0       // índice en _playlist donde empieza la ventana
    private var _windowLoading = false  // true mientras se recarga la ventana — bloquea callbacks
    private var _lastSeekTime = 0L      // timestamp of last manual seek — block maybeSlide briefly
    private var _windowItemCount = 0    // actual number of items loaded in ExoPlayer window

    fun loadMedia(uri: Uri) {
        DexLog.i("PlayerViewModel", "loadMedia uri=$uri")
        viewModelScope.launch {
            try {
                val type = resolveMediaType(uri)
                DexLog.i("PlayerViewModel", "loadMedia resolved type=$type")
                _mediaType.value        = type
                _currentUri.value       = uri
                val item                = PlaylistItem(uri, type == MediaType.VIDEO)
                _playlist.value         = listOf(item)
                _originalPlaylist.value = listOf(item)
                _currentIndex.value     = 0
                _hasLoadedMedia.value   = true
                _displayTitle.value     = ""
                _displayArtist.value    = ""
                _shuffleEnabled.value   = false
                controller.loadUri(uri)
                DexLog.i("PlayerViewModel", "loadMedia complete")
            } catch (e: Exception) {
                DexLog.e("PlayerViewModel", "loadMedia FAILED", e)
            }
        }
    }

    fun loadMedia(path: String) {
        viewModelScope.launch {
            val type = DeviceDetector.detectMediaType(path)
            val uri  = Uri.parse(path)
            _mediaType.value      = type
            _currentUri.value     = uri
            val item = PlaylistItem(uri, type == MediaType.VIDEO)
            _playlist.value       = listOf(item)
            _originalPlaylist.value = listOf(item)
            _currentIndex.value   = 0
            _hasLoadedMedia.value = true
            _displayTitle.value   = ""
            _displayArtist.value  = ""
            _shuffleEnabled.value = false
            controller.loadPath(path)
        }
    }

    // ── Lazy Queue helpers ────────────────────────────────────────────────────

    /**
     * Calcula la ventana de items alrededor de [logicalIndex] en [playlist].
     * Devuelve (windowItems, windowOffset, exoIndexInWindow).
     *
     * La ventana siempre tiene WINDOW_SIZE items (o menos si la playlist es pequeña).
     * exoIndexInWindow es la posición del item actual dentro de windowItems.
     */
    private fun buildWindow(
        playlist:     List<PlaylistItem>,
        logicalIndex: Int
    ): Triple<List<PlaylistItem>, Int, Int> {
        val total  = playlist.size
        val before = minOf(WINDOW_BEFORE, logicalIndex)
        val after  = minOf(WINDOW_AFTER, total - logicalIndex - 1)
        val start  = logicalIndex - before
        val end    = (logicalIndex + after + 1).coerceAtMost(total)
        val window = playlist.subList(start, end)
        val exoIdx = logicalIndex - start
        DexLog.d("PlayerViewModel",
            "buildWindow logical=$logicalIndex start=$start end=$end exoIdx=$exoIdx windowSize=${window.size}")
        return Triple(window, start, exoIdx)
    }

    /**
     * Carga o recarga la ventana en ExoPlayer centrada en [logicalIndex].
     * Usa loadQueue (setMediaItems) — operación completa pero sobre ~21 items.
     */
    private fun loadWindow(logicalIndex: Int, isSameSong: Boolean = (logicalIndex == _currentIndex.value)) {
        val pl = _playlist.value
        if (pl.isEmpty()) return
        val (window, offset, exoIdx) = buildWindow(pl, logicalIndex)
        _windowOffset       = offset
        _windowLoading      = true
        _currentIndex.value = logicalIndex
        applyMetadata(pl[logicalIndex])
        _windowItemCount    = window.size
        DexLog.i("PlayerViewModel",
            "loadWindow logicalIdx=$logicalIndex isSameSong=$isSameSong exoIdx=$exoIdx windowSize=${window.size}")
        viewModelScope.launch {
            if (isSameSong) {
                // Same song — use replaceWindow: seamless, no pause/play interruption
                controller.replaceWindow(window, exoIdx)
            } else {
                // New song — use loadQueue: full reset needed, preserve nothing
                controller.loadQueue(window, exoIdx, play = true, positionMs = 0L)
            }
            kotlinx.coroutines.delay(300)
            _windowLoading = false
            DexLog.d("PlayerViewModel", "loadWindow done isSameSong=$isSameSong")
        }
    }

    /**
     * Convierte un índice de ExoPlayer (dentro de la ventana actual)
     * al índice lógico en _playlist.
     */
    private fun exoToLogical(exoIdx: Int): Int = _windowOffset + exoIdx

    /**
     * Comprueba si el índice lógico está dentro de la ventana actual.
     * Si está cerca del borde (< 3 items de margen), recarga la ventana.
     */
    private fun maybeSlideWindow(logicalIndex: Int) {
        if (_windowLoading) return
        // Block slide for 600ms after a manual seek — ExoPlayer is buffering and
        // isPlaying/isBuffering state is unreliable during that window
        if (System.currentTimeMillis() - _lastSeekTime < 600L) return
        val pl    = _playlist.value
        val start = _windowOffset
        val end   = start + minOf(WINDOW_SIZE, pl.size - start) - 1
        val margin = 3
        val nearEdge = logicalIndex - start < margin || end - logicalIndex < margin
        if (nearEdge) {
            DexLog.i("PlayerViewModel",
                "maybeSlideWindow: cerca del borde (logical=$logicalIndex window=[$start,$end]) — recargando ventana")
            loadWindow(logicalIndex)
        }
    }

    fun loadPlaylist(items: List<PlaylistItem>, startIndex: Int) {
        DexLog.section("PlayerViewModel.loadPlaylist")
        DexLog.i("PlayerViewModel", "loadPlaylist items=${items.size} startIndex=$startIndex")
        if (items.isEmpty()) { DexLog.w("PlayerViewModel", "loadPlaylist llamado con lista vacía"); return }
        items.forEachIndexed { i, it -> DexLog.d("PlayerViewModel", "  [$i] ${it.uri} '${it.title}'") }

        // Apply default shuffle preference
        val applyShuffleNow = _defaultShuffle.value && !_shuffleEnabled.value
        _originalPlaylist.value = items
        _playlist.value         = items
        _currentIndex.value     = startIndex
        _hasLoadedMedia.value   = true
        applyMetadata(items[startIndex])
        DexLog.i("PlayerViewModel", "  applyShuffleNow=$applyShuffleNow")

        viewModelScope.launch {
            try {
                val (window, offset, exoIdx) = buildWindow(items, startIndex)
                _windowOffset    = offset
                _windowItemCount = window.size
                controller.loadQueue(window, exoIdx, true)
                DexLog.i("PlayerViewModel", "loadPlaylist loadQueue OK window=${window.size} exoIdx=$exoIdx")
            } catch (e: Exception) {
                DexLog.e("PlayerViewModel", "loadPlaylist loadQueue FAILED", e)
            }
        }

        // Enable shuffle after loading if default shuffle is on
        if (applyShuffleNow) {
            _shuffleEnabled.value = false  // reset so toggleShuffle actually enables it
            toggleShuffle()
        }
    }

    /** Exposes the current queue — used by MainActivity to decide append vs new */
    val currentPlaylist: List<PlaylistItem> get() = _playlist.value

    /** Appends one item to the end of the current queue without interrupting playback */
    fun appendToQueue(item: PlaylistItem) {
        val updated = _playlist.value + item
        _playlist.value         = updated
        _originalPlaylist.value = updated
        DexLog.i("PlayerViewModel", "appendToQueue: queue size now ${updated.size}")
        // Con lazy queue solo recargamos ventana si el item nuevo cae en ella
        val newLogical = updated.size - 1
        val winEnd = _windowOffset + minOf(WINDOW_SIZE, updated.size - _windowOffset) - 1
        if (newLogical <= winEnd + 1) {
            loadWindow(_currentIndex.value)
        }
    }

    /** Inserts item immediately after the current track — true "play next" */
    fun insertNextInQueue(item: PlaylistItem) {
        val current  = _currentIndex.value
        val updated  = _playlist.value.toMutableList()
        val insertAt = (current + 1).coerceAtMost(updated.size)
        updated.add(insertAt, item)
        _playlist.value         = updated
        _originalPlaylist.value = updated
        DexLog.i("PlayerViewModel", "insertNextInQueue at $insertAt, queue=${updated.size}")
        // Recargar ventana para incluir el nuevo item si cae dentro
        loadWindow(current)
    }

    // ── Library reload trigger (called after permissions granted) ─────────────
    private val _libraryReloadTick = MutableStateFlow(0)
    val libraryReloadTick: StateFlow<Int> = _libraryReloadTick.asStateFlow()

    fun triggerLibraryReload() {
        DexLog.i("PlayerViewModel", "triggerLibraryReload")
        _libraryReloadTick.value++
    }
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        DexLog.i("PlayerViewModel", "Sleep timer set: $minutes min")
        sleepTimerJob = viewModelScope.launch {
            kotlinx.coroutines.delay(minutes * 60_000L)
            DexLog.i("PlayerViewModel", "Sleep timer fired — pausing")
            controller.pause()
            _sleepTimerMinutes.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob        = null
        _sleepTimerMinutes.value = null
        DexLog.i("PlayerViewModel", "Sleep timer cancelled")
    }

    // Manual skip SIEMPRE avanza — el repeat mode nunca debe atrapar al usuario.
    // El repeat mode solo aplica en onTrackEndedNaturally (fin natural del track).
    fun skipToNext() {
        val pl    = _playlist.value
        val vmIdx = _currentIndex.value   // always use logical index — exoIdx is window-relative
        if (pl.isEmpty()) return
        val nextIdx = (vmIdx + 1) % pl.size
        DexLog.i("PlayerViewModel", "skipToNext vmIdx=$vmIdx → $nextIdx")
        loadAtIndex(nextIdx)
    }

    fun skipToPrevious() {
        val pl    = _playlist.value
        val vmIdx = _currentIndex.value
        if (pl.isEmpty()) return
        val prev = if (vmIdx <= 0) pl.size - 1 else vmIdx - 1
        DexLog.i("PlayerViewModel", "skipToPrevious vmIdx=$vmIdx → $prev")
        loadAtIndex(prev)
    }

    // Llamado por PlayerController cuando ExoPlayer reporta STATE_ENDED.
    // Aquí es donde el repeat mode tiene efecto.
    fun onTrackEndedNaturally() {
        val pl      = _playlist.value
        val current = _currentIndex.value   // always logical index
        DexLog.i("PlayerViewModel",
            "onTrackEndedNaturally vmIdx=$current pl.size=${pl.size} repeat=${_repeatMode.value}")
        if (pl.isEmpty()) return
        when (_repeatMode.value) {
            RepeatMode.ONE  -> { DexLog.d("PlayerViewModel", "  repeat ONE → recargar $current"); loadAtIndex(current) }
            RepeatMode.ALL  -> { val next = (current + 1) % pl.size; DexLog.d("PlayerViewModel", "  repeat ALL → $next"); loadAtIndex(next) }
            RepeatMode.NONE -> {
                if (current < pl.size - 1) {
                    DexLog.d("PlayerViewModel", "  repeat NONE → siguiente ${current + 1}")
                    loadAtIndex(current + 1)
                } else {
                    DexLog.i("PlayerViewModel", "  repeat NONE → última pista, fin de cola")
                }
            }
        }
    }

    private fun loadAtIndex(index: Int) {
        val pl = _playlist.value
        if (index < 0 || index >= pl.size) {
            DexLog.e("PlayerViewModel", "loadAtIndex: índice $index fuera de rango [0,${pl.size})")
            return
        }
        val item = pl[index]
        DexLog.i("PlayerViewModel", "loadAtIndex $index '${item.title}' ${item.uri}")

        // Compute isSameSong BEFORE updating _currentIndex
        val isSameSong = index == _currentIndex.value
        _currentIndex.value = index
        applyMetadata(item)

        // inWindow: index must be within _windowOffset.._windowOffset+_windowItemCount-1
        // Use _windowItemCount (actual ExoPlayer count) NOT WINDOW_SIZE
        val winStart = _windowOffset
        val winEnd   = _windowOffset + _windowItemCount - 1
        val exoIdx   = index - _windowOffset
        val inWindow = index in winStart..winEnd && exoIdx < _windowItemCount

        if (inWindow) {
            DexLog.i("PlayerViewModel", "loadAtIndex: en ventana exoIdx=$exoIdx")
            val shouldPlay = controller.state.isPlaying || controller.state.isBuffering
            _lastSeekTime = System.currentTimeMillis()
            viewModelScope.launch { controller.seekToQueueItem(exoIdx, shouldPlay) }
        } else {
            DexLog.i("PlayerViewModel", "loadAtIndex: fuera de ventana — recargando")
            loadWindow(index, isSameSong)
        }
    }

    private fun applyMetadata(item: PlaylistItem) {
        DexLog.d("PlayerViewModel",
            "applyMetadata title='${item.title}' artist='${item.artist}' uri=${item.uri}")
        _mediaType.value     = if (item.isVideo) MediaType.VIDEO else MediaType.AUDIO
        _displayTitle.value  = item.title
        _displayArtist.value = item.artist
        _currentUri.value    = item.uri
    }

    private fun resolveMediaType(uri: Uri): MediaType {
        val detected = DeviceDetector.detectMediaType(uri.toString())
        if (detected != MediaType.UNKNOWN) return detected
        val mime = getApplication<Application>().contentResolver.getType(uri) ?: ""
        return when {
            mime.startsWith("audio") -> MediaType.AUDIO
            mime.startsWith("video") -> MediaType.VIDEO
            else                     -> MediaType.UNKNOWN
        }
    }

    override fun onCleared() {
        super.onCleared()
        DexLog.i("PlayerViewModel", "onCleared — releasing controller")
        controller.release()
    }
}
