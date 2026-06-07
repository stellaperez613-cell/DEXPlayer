package com.example.dexplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.dexplayer.util.DexLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.dexplayer.player.PlaylistItem
import com.example.dexplayer.player.PlayerViewModel
import com.example.dexplayer.player.ThemeMode
import com.example.dexplayer.ui.screens.DesktopPlayerScreen
import com.example.dexplayer.ui.screens.LibraryFilter
import com.example.dexplayer.ui.screens.LibraryScreen
import com.example.dexplayer.ui.screens.MusicSubFilter
import com.example.dexplayer.ui.screens.MobileMusicScreen
import com.example.dexplayer.ui.screens.MobileVideoScreen
import com.example.dexplayer.ui.screens.PlaylistDetailScreen
import com.example.dexplayer.ui.screens.SettingsScreen
import com.example.dexplayer.ui.theme.DexPlayerTheme
import android.app.Activity
import com.example.dexplayer.util.DeviceDetector
import com.example.dexplayer.util.DeviceInfo
import com.example.dexplayer.util.DeviceMode
import com.example.dexplayer.util.DisplayRefreshManager
import com.example.dexplayer.util.MediaType
import com.example.dexplayer.util.rememberDeviceInfo

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) ->
            DexLog.i("MainActivity", "Permission $perm granted=$granted")
        }
        viewModel.triggerLibraryReload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DexLog.section("MainActivity.onCreate")
        DexLog.i("MainActivity", "savedInstanceState=${savedInstanceState != null}")

        try {
            super.onCreate(savedInstanceState)
            DexLog.i("MainActivity", "super.onCreate OK")

            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)

            requestMediaPermissionsIfNeeded()

            val externalUri = intent?.data
            DexLog.i("MainActivity", "externalUri=$externalUri")
            if (externalUri != null) {
                viewModel.loadMedia(externalUri)
                viewModel.showPlayer()
            }

            DexLog.i("MainActivity", "Calling setContent…")
            setContent {
                DexLog.d("MainActivity", "setContent composable executing")
                val themeMode by viewModel.themeMode.collectAsState()
                val darkTheme = when (themeMode) {
                    ThemeMode.DARK   -> true
                    ThemeMode.LIGHT  -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                DexPlayerTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color    = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(viewModel)
                    }
                }
            }
            DexLog.i("MainActivity", "setContent returned OK")

        } catch (e: Exception) {
            DexLog.wtf("MainActivity", "CRASH in onCreate", e)
            DexLog.flush()
            throw e
        }
    }

    private fun requestMediaPermissionsIfNeeded() {
        val required = listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val missing = required.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }
        DexLog.i("MainActivity", "Required: $required")
        DexLog.i("MainActivity", "Missing:  $missing")
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            DexLog.i("MainActivity", "All permissions already granted")
        }
    }

    override fun onStart()   { super.onStart();   DexLog.i("MainActivity", "onStart") }
    override fun onResume()  { super.onResume();  DexLog.i("MainActivity", "onResume") }
    override fun onPause()   { super.onPause();   DexLog.i("MainActivity", "onPause") }
    override fun onStop()    { super.onStop();    DexLog.i("MainActivity", "onStop") }
    override fun onDestroy() { super.onDestroy(); DexLog.i("MainActivity", "onDestroy"); DexLog.flush() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data
        DexLog.i("MainActivity", "onNewIntent uri=$uri")
        if (uri != null) {
            viewModel.loadMedia(uri)
            viewModel.showPlayer()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DexLog.d("MainActivity", "onConfigurationChanged ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}")
        Log.d("MainActivity", "Config: ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}")
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AppNavigation(viewModel: PlayerViewModel) {
    val context            = LocalContext.current
    val mediaType          by viewModel.mediaType.collectAsState()
    val showingPlayer      by viewModel.showingPlayer.collectAsState()
    val showingSettings    by viewModel.showingSettings.collectAsState()
    val hasLoadedMedia     by viewModel.hasLoadedMedia.collectAsState()
    val displayTitle       by viewModel.displayTitle.collectAsState()
    val displayArtist      by viewModel.displayArtist.collectAsState()
    val currentUri         by viewModel.currentUri.collectAsState()
    val libraryFilter      by viewModel.libraryFilter.collectAsState()
    val musicSubFilter     by viewModel.musicSubFilter.collectAsState()
    val excludedFolders    by viewModel.excludedFolders.collectAsState()
    val themeMode          by viewModel.themeMode.collectAsState()
    val defaultShuffle     by viewModel.defaultShuffle.collectAsState()
    val shuffleEnabled     by viewModel.shuffleEnabled.collectAsState()
    val repeatMode         by viewModel.repeatMode.collectAsState()
    val playlists          by viewModel.playlists.collectAsState()
    val openPlaylistId     by viewModel.openPlaylistId.collectAsState()
    val sleepTimerMinutes  by viewModel.sleepTimerMinutes.collectAsState()
    val libraryReloadTick  by viewModel.libraryReloadTick.collectAsState()
    val videoPrefs         by viewModel.videoPrefs.collectAsState()   // ← unified video prefs

    val controller = viewModel.controller
    val state      = viewModel.controller.state

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaType) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (mediaType == MediaType.VIDEO) controller.pause()
                    // Restaurar modo por defecto al ir a background
                    (context as? Activity)?.window?.let {
                        DisplayRefreshManager.resetToDefault(it)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-aplicar solo para video; música queda en LTPO
                    (context as? Activity)?.window?.let { win ->
                        if (mediaType == MediaType.VIDEO)
                            DisplayRefreshManager.applyForVideo(win, state.tracks)
                        else
                            DisplayRefreshManager.resetToDefault(win)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Display refresh rate — LTPO para música, framerate-matched para video ──
    val activity = context as? Activity
    val tracks   = state.tracks
    LaunchedEffect(mediaType, tracks) {
        val window = activity?.window ?: return@LaunchedEffect
        if (mediaType == MediaType.VIDEO) {
            DisplayRefreshManager.applyForVideo(window, tracks)
        } else {
            // Música: dejar que LTPO del sistema decida (1-120Hz adaptativo)
     
            DisplayRefreshManager.resetToDefault(window)

        }

    }

    

    // Re-evaluate on mediaType change AND on configuration changes (e.g. DEX connect/disconnect)

    val deviceInfoState = rememberDeviceInfo(mediaType)

    val deviceInfo      = deviceInfoState.value

    

    BackHandler(enabled = showingSettings)                           { viewModel.hideSettings() }

    BackHandler(enabled = openPlaylistId != null && !showingPlayer) { viewModel.closePlaylist() }

    BackHandler(enabled = showingPlayer && !showingSettings)        { viewModel.hidePlayer() }

    

    when {

        // ── Settings ──────────────────────────────────────────────────────────

        showingSettings -> {

            SettingsScreen(

                excludedFolders        = excludedFolders,

                onAddExcludedFolder    = { viewModel.addExcludedFolder(it) },

                onRemoveExcludedFolder = { viewModel.removeExcludedFolder(it) },

                themeMode              = themeMode,

                onSetTheme             = { viewModel.setThemeMode(it) },

                defaultShuffle         = defaultShuffle,

                onSetDefaultShuffle    = { viewModel.setDefaultShuffle(it) },

                audioSessionId         = viewModel.audioSessionId,

                videoPrefs             = videoPrefs,

                onSetVideoPrefs        = { viewModel.updateVideoPrefs(it) },

                onBack                 = { viewModel.hideSettings() }

            )

        }

        

        // ── Player ────────────────────────────────────────────────────────────

        showingPlayer -> {

            when (deviceInfo.mode) {

                DeviceMode.DESKTOP_DEX -> {

                    DesktopPlayerScreen(

                        controller      = controller,

                        state           = state,

                        shuffleEnabled  = shuffleEnabled,

                        repeatMode      = repeatMode,

                        onOpenFile      = {},

                        onToggleShuffle = { viewModel.toggleShuffle() },

                        onCycleRepeat   = { viewModel.cycleRepeatMode() },

                        onSkipNext      = { viewModel.skipToNext() },

                        onSkipPrevious  = { viewModel.skipToPrevious() }

                    )

                }

                DeviceMode.MOBILE_MUSIC -> {

                    MobileMusicScreen(

                        controller             = controller,

                        state                  = state,

                        displayTitle           = displayTitle,

                        displayArtist          = displayArtist,

                        shuffleEnabled         = shuffleEnabled,

                        repeatMode             = repeatMode,

                        playlists              = playlists,

                        sleepTimerMinutes      = sleepTimerMinutes,

                        onBackPressed          = { viewModel.hidePlayer() },

                        onSkipNext             = { viewModel.skipToNext() },

                        onSkipPrevious         = { viewModel.skipToPrevious() },

                        onToggleShuffle        = { viewModel.toggleShuffle() },

                        onCycleRepeat          = { viewModel.cycleRepeatMode() },

                        onAddToPlaylist        = { id, items ->

                            viewModel.addItemsToPlaylist(id, items)

                        },

                        onCreatePlaylistAndAdd = { name, items ->

                            viewModel.createPlaylistAndAdd(name, items)

                        },

                        onInsertNext           = { item -> viewModel.insertNextInQueue(item) },

                        onSetSleepTimer        = { viewModel.setSleepTimer(it) },

                        onCancelSleepTimer     = { viewModel.cancelSleepTimer() }

                    )

                }

                DeviceMode.MOBILE_VIDEO -> {

                    MobileVideoScreen(

                        controller     = controller,

                        state          = state,

                        displayTitle   = displayTitle,

                        currentUri     = currentUri,

                        videoPrefs     = videoPrefs,

                        onBackPressed  = { viewModel.hidePlayer() },

                        onSkipNext     = { viewModel.skipToNext() },

                        onSkipPrevious = { viewModel.skipToPrevious() }

                    )

                }

            }

        }

        

        // ── Playlist detail ───────────────────────────────────────────────────

        openPlaylistId != null -> {

            val playlist = playlists.find { it.id == openPlaylistId }

            if (playlist != null) {

                PlaylistDetailScreen(

                    playlist     = playlist,

                    onBack       = { viewModel.closePlaylist() },

                    onPlayAll    = { items, startIndex ->

                        viewModel.loadPlaylist(items, startIndex)

                        viewModel.showPlayer()

                    },

                    onRemoveItem = { index -> viewModel.removeFromPlaylist(playlist.id, index) },

                    onExport     = { uri -> viewModel.exportPlaylist(playlist.id, uri) }

                )

            } else {

                LaunchedEffect(Unit) { viewModel.closePlaylist() }

            }

        }

        

        // ── Library ───────────────────────────────────────────────────────────

        else -> {

            LibraryScreen(

                playerState                 = state,

                currentMediaType            = mediaType,

                isPlayerActive              = hasLoadedMedia,

                miniPlayerTitle             = displayTitle.ifBlank { state.title },

                miniPlayerArtist            = displayArtist.ifBlank { state.artist },

                miniPlayerUri               = currentUri,

                currentFilter               = libraryFilter,

                excludedFolders             = excludedFolders,

                playlists                   = playlists,

                onPlaylistClick             = { viewModel.openPlaylist(it.id) },

                onCreatePlaylist            = { name -> viewModel.createPlaylist(name) },

                onDeletePlaylist            = { id -> viewModel.deletePlaylist(id) },

                onRenamePlaylist            = { id, name -> viewModel.renamePlaylist(id, name) },

                onAddToPlaylist             = { playlistIdOrNew, items ->

                    if (playlistIdOrNew.startsWith("__NEW__:")) {

                        val name = playlistIdOrNew.removePrefix("__NEW__:")

                        viewModel.createPlaylistAndAdd(name, items)

                    } else {

                        viewModel.addItemsToPlaylist(playlistIdOrNew, items)

                    }

                },

                onImportM3U                 = { uri, name -> viewModel.importPlaylist(uri, name) },

                onExportPlaylist            = { id, uri -> viewModel.exportPlaylist(id, uri) },

                onAddToQueue                = { item ->

                    if (viewModel.currentPlaylist.isNotEmpty()) {

                        viewModel.insertNextInQueue(item)

                    } else {

                        viewModel.loadPlaylist(listOf(item), 0)

                        viewModel.showPlayer()

                    }

                },

                savedScrollIndex            = viewModel.libraryScrollIndex,

                savedScrollOffset           = viewModel.libraryScrollOffset,
                onScrollChanged             = { idx, off ->
                    viewModel.libraryScrollIndex  = idx
                    viewModel.libraryScrollOffset = off
                },
                onFilterChange              = { viewModel.setLibraryFilter(it) },
                musicSubFilter              = musicSubFilter,
                onSubFilterChange           = { viewModel.setMusicSubFilter(it) },
                libraryReloadTick           = libraryReloadTick,
                onFileSelected              = { index: Int, playlist: List<PlaylistItem> ->
                    viewModel.loadPlaylist(playlist, index)
                    viewModel.showPlayer()
                },
                onMiniPlayerClick           = { viewModel.showPlayer() },
                onMiniPlayerTogglePlayPause = { controller.togglePlayPause() },
                onMiniPlayerSkipNext        = { viewModel.skipToNext() },
                onMiniPlayerSkipPrevious    = { viewModel.skipToPrevious() },
                onOpenSettings              = { viewModel.showSettings() }
            )
        }
    }
}
