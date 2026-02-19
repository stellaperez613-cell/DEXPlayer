package com.example.dexplayer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  DATA
// ─────────────────────────────────────────────

data class ContextMenuItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val submenu: List<ContextMenuItem> = emptyList(),
    val onClick: () -> Unit = {}
)

// ─────────────────────────────────────────────
//  MAIN SCREEN
// ─────────────────────────────────────────────

@Composable
fun PlayerScreen() {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // State
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.35f) }
    var volume by remember { mutableStateOf(0.8f) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isMuted by remember { mutableStateOf(false) }

    // Auto-hide controls
    fun resetHideTimer() {
        scope.launch {
            delay(3000)
            if (isPlaying) controlsVisible = false
        }
    }

    // Context menu items
    val contextMenuItems = listOf(
        ContextMenuItem("Audio Track", Icons.Rounded.Audiotrack, listOf(
            ContextMenuItem("Track 1 – Español") {},
            ContextMenuItem("Track 2 – English") {},
            ContextMenuItem("Track 3 – Japanese") {}
        )),
        ContextMenuItem("Subtítulos", Icons.Rounded.Subtitles, listOf(
            ContextMenuItem("Desactivar") {},
            ContextMenuItem("Español SRT") {},
            ContextMenuItem("English ASS") {}
        )),
        ContextMenuItem("Velocidad", Icons.Rounded.Speed, listOf(
            ContextMenuItem("0.5×") {},
            ContextMenuItem("1.0× (normal)") {},
            ContextMenuItem("1.5×") {},
            ContextMenuItem("2.0×") {}
        )),
        ContextMenuItem("Aspect Ratio", Icons.Rounded.AspectRatio, listOf(
            ContextMenuItem("Auto") {},
            ContextMenuItem("16:9") {},
            ContextMenuItem("4:3") {},
            ContextMenuItem("Stretch") {}
        )),
        ContextMenuItem("Pantalla completa", Icons.Rounded.Fullscreen) {},
        ContextMenuItem("Abrir archivo…", Icons.Rounded.FolderOpen) {},
        ContextMenuItem("Información del archivo", Icons.Rounded.Info) {}
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible && isPlaying) resetHideTimer()
                    }
                )
            }
    ) {

        // ── Video surface placeholder ──────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            // In real app: AndroidView { SurfaceView } bound to MPV
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.size(120.dp)
            )
        }

        // ── Top gradient + bar ─────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it }
        ) {
            TopBar(
                title = "Blade.Runner.2049.2017.2160p.UHD.BluRay.mkv",
                colorScheme = colorScheme
            )
        }

        // ── Bottom gradient + controls ─────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it }
        ) {
            BottomControls(
                isPlaying = isPlaying,
                progress = progress,
                volume = volume,
                isMuted = isMuted,
                colorScheme = colorScheme,
                onPlayPause = {
                    isPlaying = !isPlaying
                    if (isPlaying) resetHideTimer()
                },
                onProgressChange = { progress = it },
                onVolumeChange = { volume = it },
                onMuteToggle = { isMuted = !isMuted },
                onRightClick = { offset ->
                    contextMenuOffset = offset
                    showContextMenu = true
                }
            )
        }

        // ── Context Menu ───────────────────────────────
        if (showContextMenu) {
            PlayerContextMenu(
                items = contextMenuItems,
                offset = contextMenuOffset,
                onDismiss = { showContextMenu = false },
                colorScheme = colorScheme
            )
        }
    }
}

// ─────────────────────────────────────────────
//  TOP BAR
// ─────────────────────────────────────────────

@Composable
fun TopBar(title: String, colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Window/back button (DEX)
            IconButton(onClick = {}) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Menu button
            IconButton(onClick = {}) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  BOTTOM CONTROLS
// ─────────────────────────────────────────────

@Composable
fun BottomControls(
    isPlaying: Boolean,
    progress: Float,
    volume: Float,
    isMuted: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRightClick: (DpOffset) -> Unit
) {
    val accentColor = colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            // ── Seekbar ──────────────────────────────────
            Slider(
                value = progress,
                onValueChange = onProgressChange,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Controls row ─────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                // Skip back
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.Replay10,
                        contentDescription = "−10s",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause — slightly larger
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Skip forward
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.Forward10,
                        contentDescription = "+10s",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Timecode
                Text(
                    text = "1:24:33 / 2:43:52",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                // Volume
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        imageVector = if (isMuted) Icons.Rounded.VolumeOff
                        else if (volume > 0.5f) Icons.Rounded.VolumeUp
                        else Icons.Rounded.VolumeDown,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Slider(
                    value = if (isMuted) 0f else volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.width(90.dp)
                )

                Spacer(Modifier.width(8.dp))

                // Subtitles
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.Subtitles,
                        contentDescription = "Subtítulos",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Playlist
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.QueueMusic,
                        contentDescription = "Playlist",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fullscreen
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = "Pantalla completa",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  CONTEXT MENU (click derecho estilo DEX)
// ─────────────────────────────────────────────

@Composable
fun PlayerContextMenu(
    items: List<ContextMenuItem>,
    offset: DpOffset,
    onDismiss: () -> Unit,
    colorScheme: ColorScheme
) {
    var expandedSubmenu by remember { mutableStateOf<String?>(null) }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerHigh)
            .width(220.dp)
    ) {
        items.forEach { item ->
            if (item.submenu.isNotEmpty()) {
                // Item with submenu
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        expandedSubmenu = if (expandedSubmenu == item.label) null else item.label
                    }
                )

                // Inline submenu (simplified — real app uses nested DropdownMenu)
                AnimatedVisibility(visible = expandedSubmenu == item.label) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainerHighest)
                    ) {
                        item.submenu.forEach { sub ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sub.label,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    sub.onClick()
                                    onDismiss()
                                },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        item.onClick()
                        onDismiss()
                    }
                )
            }
        }
    }
}
