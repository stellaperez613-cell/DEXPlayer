package com.example.dexplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  SCALE — 125%
// ─────────────────────────────────────────────

private val IconSizeSm   = 22
private val IconSizeMd   = 20
private val IconBtnSize  = 38
private val PlayBtnSize  = 40
private val PlayIconSize = 22
private val TimecodeSize = 13
private val StatusSize   = 12
private val MenuIconSize = 17
private val MenuTextSize = 14
private val SeekHeight   = 30
private val VolumeWidth  = 100
private val BarPadV      = 4
private val BarPadH      = 6

// ─────────────────────────────────────────────
//  COLORS
// ─────────────────────────────────────────────

private val BgPrimary  = Color(0xFF0D0D0D)
private val AccentBlue = Color(0xFF4A90D9)
private val SeekTrack  = Color(0xFF3A3A3A)

data class PlayerColors(
    val bgToolbar: Color,
    val bgToolbarTop: Color,
    val bgHover: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

@Composable
fun playerColors(): PlayerColors = if (isSystemInDarkTheme()) {
    PlayerColors(
        bgToolbar    = Color(0xFF1A1A1A),
        bgToolbarTop = Color(0xFF141414),
        bgHover      = Color(0xFF2A2A2A),
        border       = Color(0xFF2E2E2E),
        textPrimary  = Color(0xFFFFFFFF),
        textSecondary= Color(0xFFFFFFFF)
    )
} else {
    PlayerColors(
        bgToolbar    = Color(0xFFFFFFFF),
        bgToolbarTop = Color(0xFFFFFFFF),
        bgHover      = Color(0xFFEEEEEE),
        border       = Color(0xFFDDDDDD),
        textPrimary  = Color(0xFF1A1A1A),
        textSecondary= Color(0xFF555555)
    )
}

// ─────────────────────────────────────────────
//  DATA
// ─────────────────────────────────────────────

data class MenuItem(
    val label: String,
    val shortcut: String = "",
    val icon: ImageVector? = null,
    val submenu: List<MenuItem> = emptyList(),
    val dividerAfter: Boolean = false,
    val onClick: () -> Unit = {}
)

// ─────────────────────────────────────────────
//  PLAYER STATE HOLDER
// ─────────────────────────────────────────────

class PlayerState {
    var isPlaying by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var duration by mutableStateOf(0)
    var currentPos by mutableStateOf(0)
    var volume by mutableStateOf(0.75f)
    var isMuted by mutableStateOf(false)
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var mediaInfo by mutableStateOf("")
}

@Composable
fun rememberPlayerState(): PlayerState {
    return remember { PlayerState() }
}

// ─────────────────────────────────────────────
//  MAIN SCREEN
// ─────────────────────────────────────────────

private const val TAG = "PlayerScreen"

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = playerColors()
    val playerState = rememberPlayerState()

    var controlsVisible by remember { mutableStateOf(true) }
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var expandedSub by remember { mutableStateOf<String?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    // Estado del surface
    var surfaceReady by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<String?>(null) }

    // Permisos - diferentes según versión de Android
    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ usa READ_MEDIA_VIDEO
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12: Scoped storage, pero READ_EXTERNAL_STORAGE aún funciona para media
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10 y menor
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.values.any { it }
        if (hasStoragePermission) {
            Log.d(TAG, "Storage permission granted")
        } else {
            playerState.errorMessage = "Se requiere permiso de almacenamiento para reproducir videos"
        }
    }

    // Función para cargar archivo de forma segura
    fun loadFile(path: String) {
        if (!MainActivity.isMpvInitialized) {
            Log.e(TAG, "Cannot load file - MPV not initialized")
            playerState.errorMessage = "MPV no inicializado"
            return
        }

        if (!surfaceReady) {
            Log.d(TAG, "Surface not ready, queuing file: $path")
            pendingFile = path
            return
        }

        try {
            playerState.isLoading = true
            playerState.errorMessage = null

            Log.d(TAG, "Loading file: $path")
            MPVLib.command(arrayOf("loadfile", path))

        } catch (e: Exception) {
            Log.e(TAG, "Error loading file", e)
            playerState.errorMessage = "Error: ${e.message}"
            playerState.isLoading = false
        }
    }

    // Callback cuando el surface está listo
    val onSurfaceReady: () -> Unit = {
        Log.d(TAG, "Surface ready callback")
        surfaceReady = true

        // Cargar archivo pendiente si existe
        pendingFile?.let { file ->
            Log.d(TAG, "Loading pending file: $file")
            scope.launch {
                delay(100) // Pequeño delay para asegurar estabilidad
                loadFile(file)
                pendingFile = null
            }
        }
    }

    val onSurfaceDestroyed: () -> Unit = {
        Log.d(TAG, "Surface destroyed callback")
        surfaceReady = false
    }

    // Observer de MPV
    DisposableEffect(Unit) {
        if (!MainActivity.isMpvInitialized) {
            Log.e(TAG, "MPV not initialized, skipping observer setup")
            return@DisposableEffect onDispose { }
        }

        val observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {
                Log.v(TAG, "Property changed: $property")
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> {
                        playerState.currentPos = value.toInt()
                        if (playerState.duration > 0) {
                            playerState.progress = value.toFloat() / playerState.duration
                        }
                    }
                    "duration" -> {
                        playerState.duration = value.toInt()
                    }
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        playerState.isPlaying = !value
                    }
                    "seeking" -> {
                        playerState.isLoading = value
                    }
                }
            }

            override fun eventProperty(property: String, value: Double) {}

            override fun eventProperty(property: String, value: String) {
                when (property) {
                    "media-title" -> {
                        Log.d(TAG, "Media title: $value")
                    }
                }
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "File loaded")
                        playerState.isLoading = false
                        playerState.errorMessage = null
                    }
                    MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                        Log.d(TAG, "Playback restart")
                        playerState.isLoading = false
                    }
                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                        Log.d(TAG, "End file")
                    }
                }
            }
        }

        try {
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("seeking", MPVLib.MpvFormat.MPV_FORMAT_FLAG)

            Log.d(TAG, "Observer registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observer", e)
        }

        onDispose {
            try {
                MPVLib.removeObserver(observer)
                Log.d(TAG, "Observer removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing observer", e)
            }
        }
    }

    // Auto-hide controls con cancelación
    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3500)
            if (playerState.isPlaying) {
                controlsVisible = false
            }
        }
    }

    // Solicitar permisos al inicio
    LaunchedEffect(Unit) {
        if (!hasStoragePermission) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    }

    // Cargar archivo inicial cuando todo esté listo
    LaunchedEffect(hasStoragePermission, surfaceReady) {
        if (hasStoragePermission && surfaceReady && pendingFile == null) {
            // Aquí puedes cambiar el archivo a cargar
            val testFile = "/storage/emulated/0/Movies/Hikaru No Go 36.mkv"
            loadFile(testFile)
        }
    }

    val contextMenuItems = listOf(
        MenuItem("Open File…", "Ctrl+O", Icons.Rounded.FolderOpen, dividerAfter = true),
        MenuItem("Audio Track", icon = Icons.Rounded.Audiotrack, submenu = listOf(
            MenuItem("Track 1 – Español (AC3 5.1)"),
            MenuItem("Track 2 – English (DTS)"),
            MenuItem("Track 3 – Japanese (AAC)")
        )),
        MenuItem("Subtitles", icon = Icons.Rounded.Subtitles, submenu = listOf(
            MenuItem("Disabled"),
            MenuItem("Español.srt"),
            MenuItem("English.ass")
        )),
        MenuItem("Playback Speed", icon = Icons.Rounded.Speed, submenu = listOf(
            MenuItem("0.5×"),
            MenuItem("0.75×"),
            MenuItem("1.0×  (Normal)"),
            MenuItem("1.25×"),
            MenuItem("1.5×"),
            MenuItem("2.0×")
        ), dividerAfter = true),
        MenuItem("Aspect Ratio", icon = Icons.Rounded.AspectRatio, submenu = listOf(
            MenuItem("Auto"),
            MenuItem("16:9"),
            MenuItem("4:3"),
            MenuItem("2.35:1"),
            MenuItem("Stretch")
        )),
        MenuItem("Zoom", icon = Icons.Rounded.ZoomIn, submenu = listOf(
            MenuItem("50%"),
            MenuItem("100%"),
            MenuItem("150%"),
            MenuItem("200%")
        ), dividerAfter = true),
        MenuItem("Properties", "Alt+Return", Icons.Rounded.Info),
        MenuItem("Toggle Fullscreen", "Alt+Enter", Icons.Rounded.Fullscreen)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible && playerState.isPlaying) {
                            scheduleHide()
                        }
                    }
                )
            }
    ) {
        // ── Video Surface ──────────────────────────────
        AndroidView(
            factory = { ctx ->
                MPVSurfaceView(
                    context = ctx,
                    onSurfaceReady = onSurfaceReady,
                    onSurfaceDestroyed = onSurfaceDestroyed
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Loading Indicator ──────────────────────────
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentBlue,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // ── Error Message ──────────────────────────────
        playerState.errorMessage?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = error,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // ── Controls Overlay ───────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopBar(colors = colors)

                Spacer(Modifier.weight(1f))

                // Bottom controls
                BottomControls(
                    colors = colors,
                    playerState = playerState,
                    onSeek = { newProgress ->
                        if (MainActivity.isMpvInitialized && playerState.duration > 0) {
                            val newPos = (newProgress * playerState.duration).toInt()
                            MPVLib.command(arrayOf("seek", newPos.toString(), "absolute"))
                        }
                    },
                    onPlayPause = {
                        if (MainActivity.isMpvInitialized) {
                            val newPause = playerState.isPlaying
                            MPVLib.setPropertyBoolean("pause", newPause)
                        }
                    },
                    onVolumeChange = { newVolume ->
                        playerState.volume = newVolume
                        playerState.isMuted = false
                        if (MainActivity.isMpvInitialized) {
                            MPVLib.setPropertyInt("volume", (newVolume * 100).toInt())
                        }
                    },
                    onMuteToggle = {
                        playerState.isMuted = !playerState.isMuted
                        if (MainActivity.isMpvInitialized) {
                            MPVLib.setPropertyBoolean("mute", playerState.isMuted)
                        }
                    }
                )
            }
        }

        // Context menu
        if (showContextMenu) {
            MpcContextMenu(
                items = contextMenuItems,
                offset = menuOffset,
                expandedSub = expandedSub,
                colors = colors,
                onExpandSub = { expandedSub = it },
                onDismiss = {
                    showContextMenu = false
                    expandedSub = null
                }
            )
        }
    }
}

// ─────────────────────────────────────────────
//  TOP BAR
// ─────────────────────────────────────────────

@Composable
private fun TopBar(colors: PlayerColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgToolbarTop.copy(alpha = 0.95f))
            .padding(horizontal = BarPadH.dp, vertical = BarPadV.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MpcIconBtn(Icons.Rounded.Menu, "Menu", colors = colors)
            Spacer(Modifier.width(8.dp))
            Text(
                "DexPlayer",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row {
            MpcIconBtn(Icons.Rounded.FolderOpen, "Open", colors = colors)
            MpcIconBtn(Icons.Rounded.Settings, "Settings", colors = colors)
        }
    }
}

// ─────────────────────────────────────────────
//  BOTTOM CONTROLS
// ─────────────────────────────────────────────

@Composable
private fun BottomControls(
    colors: PlayerColors,
    playerState: PlayerState,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgToolbar.copy(alpha = 0.95f))
    ) {
        // Seek bar
        Slider(
            value = playerState.progress,
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = SeekTrack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekHeight.dp)
                .padding(horizontal = 8.dp)
        )

        // Control buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BarPadH.dp, vertical = BarPadV.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback controls
            MpcIconBtn(Icons.Rounded.SkipPrevious, "Previous", colors = colors)
            MpcIconBtn(Icons.Rounded.FastRewind, "Rewind", colors = colors)

            // Play/Pause button
            Box(
                modifier = Modifier
                    .size(PlayBtnSize.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentBlue)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(PlayIconSize.dp)
                )
            }

            MpcIconBtn(Icons.Rounded.FastForward, "Forward", colors = colors)
            MpcIconBtn(Icons.Rounded.SkipNext, "Next", colors = colors)
            MpcIconBtn(Icons.Rounded.Stop, "Stop", colors = colors)

            Spacer(Modifier.width(12.dp))

            // Timecode
            fun formatTime(secs: Int): String {
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val s = secs % 60
                return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
                else "%02d:%02d".format(m, s)
            }

            Text(
                text = "${formatTime(playerState.currentPos)} / ${formatTime(playerState.duration)}",
                color = colors.textSecondary,
                fontSize = TimecodeSize.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.weight(1f))

            // Volume
            MpcIconBtn(
                icon = if (playerState.isMuted) Icons.Rounded.VolumeOff
                else if (playerState.volume > 0.5f) Icons.Rounded.VolumeUp
                else Icons.Rounded.VolumeDown,
                desc = "Mute",
                colors = colors,
                size = IconSizeSm,
                onClick = onMuteToggle
            )

            Slider(
                value = if (playerState.isMuted) 0f else playerState.volume,
                onValueChange = onVolumeChange,
                colors = SliderDefaults.colors(
                    thumbColor = colors.textSecondary,
                    activeTrackColor = colors.textSecondary,
                    inactiveTrackColor = SeekTrack
                ),
                modifier = Modifier
                    .width(VolumeWidth.dp)
                    .height(SeekHeight.dp)
            )

            Spacer(Modifier.width(10.dp))
            HorizontalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .width(0.5.dp),
                color = colors.border
            )
            Spacer(Modifier.width(10.dp))

            MpcIconBtn(Icons.Rounded.Subtitles, "Subtitles", colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.QueueMusic, "Playlist", colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.Fullscreen, "Fullscreen", colors = colors, size = IconSizeSm)
        }

        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                playerState.mediaInfo.ifEmpty { "No file loaded" },
                color = Color(0xFFFFFFFF),
                fontSize = StatusSize.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (playerState.isLoading) "Loading..." else "Ready",
                color = Color(0xFFFFFFFF),
                fontSize = StatusSize.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────
//  CONTEXT MENU
// ─────────────────────────────────────────────

@Composable
fun MpcContextMenu(
    items: List<MenuItem>,
    offset: DpOffset,
    expandedSub: String?,
    colors: PlayerColors,
    onExpandSub: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .background(colors.bgToolbar)
            .width(260.dp)
            .border(BorderStroke(0.5.dp, colors.border))
    ) {
        items.forEach { item ->
            if (item.submenu.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                item.label,
                                fontSize = MenuTextSize.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(MenuIconSize.dp)
                            )
                        }
                    },
                    leadingIcon = item.icon?.let {
                        { Icon(it, null, tint = colors.textSecondary,
                            modifier = Modifier.size(MenuIconSize.dp)) }
                    },
                    onClick = { onExpandSub(item.label) },
                    colors = MenuDefaults.itemColors(textColor = colors.textPrimary),
                    modifier = Modifier.background(
                        if (expandedSub == item.label) colors.bgHover else Color.Transparent
                    )
                )
                AnimatedVisibility(visible = expandedSub == item.label) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgHover)
                            .border(BorderStroke(0.5.dp, colors.border))
                    ) {
                        item.submenu.forEach { sub ->
                            DropdownMenuItem(
                                text = {
                                    Text(sub.label, fontSize = MenuTextSize.sp,
                                        color = colors.textPrimary)
                                },
                                onClick = { sub.onClick(); onDismiss() },
                                modifier = Modifier
                                    .padding(start = 14.dp)
                                    .height(32.dp),
                                colors = MenuDefaults.itemColors(textColor = colors.textPrimary)
                            )
                        }
                    }
                }
            } else {
                DropdownMenuItem(
                    text = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                item.label,
                                fontSize = MenuTextSize.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.shortcut.isNotEmpty()) {
                                Text(item.shortcut, fontSize = (MenuTextSize - 2).sp,
                                    color = colors.textSecondary)
                            }
                        }
                    },
                    leadingIcon = item.icon?.let {
                        { Icon(it, null, tint = colors.textSecondary,
                            modifier = Modifier.size(MenuIconSize.dp)) }
                    },
                    onClick = { item.onClick(); onDismiss() },
                    colors = MenuDefaults.itemColors(textColor = colors.textPrimary),
                    modifier = Modifier.height(36.dp)
                )
            }

            if (item.dividerAfter) {
                HorizontalDivider(
                    color = colors.border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  HELPER — botón icono compacto
// ─────────────────────────────────────────────

@Composable
fun MpcIconBtn(
    icon: ImageVector,
    desc: String,
    colors: PlayerColors,
    size: Int = IconSizeSm,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size((size + 12).dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = colors.textSecondary,
            modifier = Modifier.size(size.dp)
        )
    }
}
