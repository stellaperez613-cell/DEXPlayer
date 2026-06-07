@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.dexplayer.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dexplayer.player.PlayerController
import com.example.dexplayer.player.PlayerState
import com.example.dexplayer.player.RepeatMode
import com.example.dexplayer.ui.components.VideoSurface

// ── MPC-HC Dark palette ───────────────────────────────────────────────────────
private val MpcBg        = Color(0xFF1E1E1E)
private val MpcSurface   = Color(0xFF252526)
private val MpcBar       = Color(0xFF2D2D30)
private val MpcBorder    = Color(0xFF3F3F46)
private val MpcText      = Color(0xFFD4D4D4)
private val MpcTextDim   = Color(0xFF808080)
private val MpcAccent    = Color(0xFF007ACC)
private val MpcSeekBg    = Color(0xFF3E3E42)
private val MpcSeekFill  = Color(0xFF007ACC)
private val MpcHover     = Color(0xFF3E3E42)

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun DesktopPlayerScreen(
    controller:       PlayerController,
    state:            PlayerState,
    shuffleEnabled:   Boolean    = false,
    repeatMode:       RepeatMode = RepeatMode.NONE,
    onOpenFile:       () -> Unit = {},
    onToggleShuffle:  () -> Unit = {},
    onCycleRepeat:    () -> Unit = {},
    onSkipNext:       () -> Unit = {},
    onSkipPrevious:   () -> Unit = {}
) {
    var showFileMenu  by remember { mutableStateOf(false) }
    var showViewMenu  by remember { mutableStateOf(false) }
    var showPlayMenu  by remember { mutableStateOf(false) }
    var showHelpMenu  by remember { mutableStateOf(false) }

    // Derive file extension from URI
    val fileExt = remember(state.mediaItem) {
        state.mediaItem?.localConfiguration?.uri
            ?.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.uppercase()
            ?.takeIf { it.isNotBlank() } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MpcBg)
    ) {

        // ════════════════════════════════════════════════════════════════════
        // TOP MENU BAR
        // ════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(MpcSurface)
                .border(BorderStroke(1.dp, MpcBorder)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File
            Box {
                MpcMenuButton("File") { showFileMenu = true }
                DropdownMenu(
                    expanded         = showFileMenu,
                    onDismissRequest = { showFileMenu = false },
                    modifier         = Modifier.background(MpcSurface)
                ) {
                    MpcMenuItem("Open File…",  "Ctrl+O") { onOpenFile(); showFileMenu = false }
                    MpcMenuItem("Open URL…",   "Ctrl+U") { showFileMenu = false }
                    HorizontalDivider(color = MpcBorder, thickness = 0.5.dp)
                    MpcMenuItem("Exit", "Alt+X") { showFileMenu = false }
                }
            }

            // View
            Box {
                MpcMenuButton("View") { showViewMenu = true }
                DropdownMenu(
                    expanded         = showViewMenu,
                    onDismissRequest = { showViewMenu = false },
                    modifier         = Modifier.background(MpcSurface)
                ) {
                    MpcMenuItem("Fit to Window",  "Alt+Enter") { showViewMenu = false }
                    HorizontalDivider(color = MpcBorder, thickness = 0.5.dp)
                    MpcMenuItem("50%",  "1") { showViewMenu = false }
                    MpcMenuItem("100%", "2") { showViewMenu = false }
                    MpcMenuItem("200%", "3") { showViewMenu = false }
                }
            }

            // Play
            Box {
                MpcMenuButton("Play") { showPlayMenu = true }
                DropdownMenu(
                    expanded         = showPlayMenu,
                    onDismissRequest = { showPlayMenu = false },
                    modifier         = Modifier.background(MpcSurface)
                ) {
                    MpcMenuItem("Play / Pause", "Space") { controller.togglePlayPause(); showPlayMenu = false }
                    MpcMenuItem("Stop",         ".")     { controller.stop(); showPlayMenu = false }
                    HorizontalDivider(color = MpcBorder, thickness = 0.5.dp)
                    MpcMenuItem("Previous",     "←")    { onSkipPrevious(); showPlayMenu = false }
                    MpcMenuItem("Next",         "→")    { onSkipNext(); showPlayMenu = false }
                    HorizontalDivider(color = MpcBorder, thickness = 0.5.dp)
                    MpcMenuItem("Skip back 10s",  "Ctrl+←") { controller.seekBackward(10); showPlayMenu = false }
                    MpcMenuItem("Skip ahead 10s", "Ctrl+→") { controller.seekForward(10); showPlayMenu = false }
                    HorizontalDivider(color = MpcBorder, thickness = 0.5.dp)
                    MpcMenuCheckItem("Shuffle", shuffleEnabled) { onToggleShuffle(); showPlayMenu = false }
                    MpcMenuCheckItem("Repeat",  repeatMode != RepeatMode.NONE) { onCycleRepeat(); showPlayMenu = false }
                }
            }

            // Help
            Box {
                MpcMenuButton("Help") { showHelpMenu = true }
                DropdownMenu(
                    expanded         = showHelpMenu,
                    onDismissRequest = { showHelpMenu = false },
                    modifier         = Modifier.background(MpcSurface)
                ) {
                    MpcMenuItem("About DexPlayer") { showHelpMenu = false }
                }
            }

            Spacer(Modifier.weight(1f))

            // Title in menu bar
            Text(
                text     = state.title.ifBlank { "DexPlayer" },
                color    = MpcTextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 10.dp).widthIn(max = 400.dp)
            )
        }

        // ════════════════════════════════════════════════════════════════════
        // MAIN CONTENT AREA (video or music)
        // ════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { controller.togglePlayPause() }
                    )
                }
        ) {
            if (state.hasVideo) {
                VideoSurface(
                    player   = controller.player ?: return@Box,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Music / audio mode — centered info panel
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Big audio icon
                    Box(
                        modifier        = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MpcSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint     = MpcAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    if (state.title.isNotBlank()) {
                        Text(state.title,
                            color      = MpcText,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1, overflow = TextOverflow.Ellipsis,
                            modifier   = Modifier.widthIn(max = 500.dp))
                    }
                    if (state.artist.isNotBlank()) {
                        Text(state.artist,
                            color    = MpcTextDim,
                            fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Idle placeholder
            if (state.isIdle) {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint     = MpcTextDim.copy(alpha = 0.25f),
                        modifier = Modifier.size(72.dp))
                    Text("Open a file to start playing",
                        color    = MpcTextDim.copy(alpha = 0.5f),
                        fontSize = 13.sp)
                }
            }

            // Buffering spinner
            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier    = Modifier.align(Alignment.Center),
                    color       = MpcAccent,
                    strokeWidth = 2.dp
                )
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // BAR 1 — SEEKBAR / PROGRESS
        // ════════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(MpcBar)
                .border(BorderStroke(1.dp, MpcBorder))
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val pct = (offset.x / size.width).coerceIn(0f, 1f)
                        controller.seekToPercent(pct)
                    }
                }
        ) {
            // Track background
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(MpcSeekBg))
            // Fill
            Box(
                Modifier
                    .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MpcSeekFill)
            )
            // Thumb dot
            if (state.progress > 0f && state.progress < 1f) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .wrapContentWidth(Alignment.End)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .align(Alignment.Center)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // BAR 2 — PLAYBACK CONTROLS
        // ════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(MpcBar)
                .border(BorderStroke(1.dp, MpcBorder))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback controls
            MpcIconButton(Icons.Filled.SkipPrevious, "Previous") { onSkipPrevious() }
            MpcIconButton(Icons.Filled.FastRewind,   "Seek back") { controller.seekBackward(10) }

            // Play/Pause — highlighted
            MpcPlayButton(isPlaying = state.isPlaying) { controller.togglePlayPause() }

            MpcIconButton(Icons.Filled.FastForward,  "Seek forward") { controller.seekForward(10) }
            MpcIconButton(Icons.Filled.SkipNext,     "Next") { onSkipNext() }

            MpcSeparator()

            MpcIconButton(Icons.Filled.Stop, "Stop") { controller.stop() }

            MpcSeparator()

            // Shuffle
            MpcToggleButton(
                icon    = Icons.Rounded.Shuffle,
                active  = shuffleEnabled,
                tooltip = "Shuffle"
            ) { onToggleShuffle() }

            // Repeat
            MpcToggleButton(
                icon    = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else           -> Icons.Rounded.Repeat
                },
                active  = repeatMode != RepeatMode.NONE,
                tooltip = "Repeat"
            ) { onCycleRepeat() }

            MpcSeparator()

            // Volume
            MpcIconButton(
                if (state.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                "Mute"
            ) { controller.toggleMute() }

            Slider(
                value         = if (state.isMuted) 0f else state.volume,
                onValueChange = { controller.setVolume(it) },
                modifier      = Modifier.width(80.dp),
                colors        = SliderDefaults.colors(
                    thumbColor         = MpcText,
                    activeTrackColor   = MpcAccent,
                    inactiveTrackColor = MpcSeekBg
                )
            )

            Spacer(Modifier.weight(1f))

            // Subtitle / Audio track buttons (video only)
            if (state.hasVideo) {
                MpcIconButton(Icons.Filled.Subtitles,  "Subtitles") {}
                MpcIconButton(Icons.Filled.Audiotrack, "Audio track") {}
                MpcSeparator()
            }

            MpcIconButton(Icons.Filled.Fullscreen, "Fullscreen") {}
        }

        // ════════════════════════════════════════════════════════════════════
        // BAR 3 — STATUS INFO
        // ════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(MpcSurface)
                .border(BorderStroke(1.dp, MpcBorder))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left — state + file extension
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Playback state dot + label
                val (stateLabel, stateColor) = when {
                    state.isBuffering -> "Buffering" to MpcAccent
                    state.isPlaying   -> "Playing"   to Color(0xFF4EC94E)
                    state.isIdle      -> "Stopped"   to MpcTextDim
                    else              -> "Paused"    to Color(0xFFD4A24E)
                }
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(stateColor)
                )
                Text(stateLabel, color = stateColor, fontSize = 11.sp)

                if (fileExt.isNotBlank()) {
                    MpcStatusSeparator()
                    Text(fileExt,
                        color      = MpcTextDim,
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }

                if (state.hasVideo) {
                    MpcStatusSeparator()
                    Text("${state.videoSize.width}×${state.videoSize.height}",
                        color      = MpcTextDim,
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }

            // Right — elapsed / total
            Text(
                text       = "${state.currentTimeFormatted} / ${state.durationFormatted}",
                color      = MpcText,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun MpcMenuButton(text: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Text(
        text     = text,
        color    = MpcText,
        fontSize = 12.sp,
        modifier = Modifier
            .background(if (hovered) MpcHover else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .hoverable(remember { MutableInteractionSource() })
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun MpcMenuItem(text: String, shortcut: String = "", onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text,     color = MpcText,    fontSize = 12.sp)
                if (shortcut.isNotBlank())
                    Text(shortcut, color = MpcTextDim, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 24.dp))
            }
        },
        onClick  = onClick,
        modifier = Modifier.height(28.dp),
        colors   = MenuDefaults.itemColors(textColor = MpcText)
    )
}

@Composable
private fun MpcMenuCheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (checked)
                    Icon(Icons.Rounded.Check, null,
                        tint = MpcAccent, modifier = Modifier.size(14.dp))
                else
                    Spacer(Modifier.size(14.dp))
                Text(text, color = if (checked) MpcAccent else MpcText, fontSize = 12.sp)
            }
        },
        onClick  = onClick,
        modifier = Modifier.height(28.dp),
        colors   = MenuDefaults.itemColors(textColor = MpcText)
    )
}

@Composable
private fun MpcIconButton(icon: ImageVector, tooltip: String = "", onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (hovered) MpcHover else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
            .hoverable(remember { MutableInteractionSource() }),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = tooltip,
            tint = MpcText, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MpcPlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MpcAccent.copy(alpha = 0.25f))
            .border(BorderStroke(1.dp, MpcAccent.copy(alpha = 0.5f)), RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint     = MpcText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MpcToggleButton(icon: ImageVector, active: Boolean, tooltip: String = "", onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) MpcAccent.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (active) MpcAccent.copy(0.6f) else Color.Transparent),
                RoundedCornerShape(3.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = tooltip,
            tint     = if (active) MpcAccent else MpcTextDim,
            modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MpcSeparator() {
    Spacer(Modifier.width(3.dp))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MpcBorder)
    )
    Spacer(Modifier.width(3.dp))
}

@Composable
private fun MpcStatusSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(10.dp)
            .background(MpcBorder)
    )
}
