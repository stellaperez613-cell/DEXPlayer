@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.dexplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dexplayer.player.PlayerController
import com.example.dexplayer.player.PlayerState
import com.example.dexplayer.player.SrtParser
import com.example.dexplayer.player.SubtitleBackground
import com.example.dexplayer.player.SubtitleCue
import com.example.dexplayer.player.SubtitleFinder
import com.example.dexplayer.player.SubtitlePrefs
import com.example.dexplayer.player.VideoPrefs
import com.example.dexplayer.ui.components.VideoSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ControlWhite  = Color.White
private val TrackInactive = Color.White.copy(alpha = 0.30f)

@Composable
fun MobileVideoScreen(
    controller:     PlayerController,
    state:          PlayerState,
    displayTitle:   String     = "",
    currentUri:     Uri?       = null,
    videoPrefs:     VideoPrefs = VideoPrefs(),   // ← receives prefs from ViewModel
    onBackPressed:  () -> Unit = {},
    onSkipNext:     () -> Unit = {},
    onSkipPrevious: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(true) }
    var hideJob         by remember { mutableStateOf<Job?>(null) }
    var doubleTapSide   by remember { mutableStateOf<Int?>(null) }

    // ── Subtitle state ────────────────────────────────────────────────────────
    var subtitleCues    by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleEnabled by remember { mutableStateOf(true) }
    var subtitleUri     by remember { mutableStateOf<Uri?>(null) }
    var showSubMenu     by remember { mutableStateOf(false) }
    var isLoadingSubs   by remember { mutableStateOf(false) }

    val subPrefs = videoPrefs.subtitlePrefs

    // Active cue for current position
    val currentCue = remember(subtitleCues, state.currentPosition) {
        if (!subtitleEnabled || subtitleCues.isEmpty()) return@remember null
        subtitleCues.firstOrNull { state.currentPosition in it.startMs..it.endMs }
    }

    // ── Auto-detect .srt beside the video ────────────────────────────────────
    LaunchedEffect(currentUri) {
        subtitleCues = emptyList()
        subtitleUri  = null
        val uri = currentUri ?: return@LaunchedEffect
        isLoadingSubs = true
        val found = SubtitleFinder.findSrt(context, uri)
        if (found != null) {
            subtitleUri  = found
            subtitleCues = SrtParser.parse(context, found)
        }
        isLoadingSubs = false
    }

    // ── Manual .srt picker ────────────────────────────────────────────────────
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            isLoadingSubs = true
            try { context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            subtitleUri     = uri
            subtitleCues    = SrtParser.parse(context, uri)
            subtitleEnabled = true
            isLoadingSubs   = false
        }
    }

    val shownTitle = listOf(displayTitle, state.title)
        .firstOrNull { it.isNotBlank() && !it.all { c -> c.isDigit() || c == '-' } } ?: "Unknown"

    val seekSec = videoPrefs.doubleTapSeekSec

    fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch { delay(4000); if (state.isPlaying) controlsVisible = false }
    }
    LaunchedEffect(doubleTapSide)   { if (doubleTapSide != null) { delay(400); doubleTapSide = null } }
    LaunchedEffect(state.isPlaying) { if (state.isPlaying) scheduleHide() else controlsVisible = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        val resolvedPlayer = controller.player ?: return@Box
        VideoSurface(player = resolvedPlayer, modifier = Modifier.fillMaxSize())

        // ── Gesture layer ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    if (showSubMenu) showSubMenu = false
                    else { controlsVisible = !controlsVisible; if (controlsVisible) scheduleHide() }
                },
                onDoubleTap = { offset ->
                    if (offset.x < size.width / 2) { controller.seekBackward(seekSec); doubleTapSide = -1 }
                    else { controller.seekForward(seekSec); doubleTapSide = 1 }
                }
            )
        })

        // ── Double-tap feedback ───────────────────────────────────────────────
        AnimatedVisibility(doubleTapSide != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (doubleTapSide == -1) DoubleTapFeedback(-seekSec)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (doubleTapSide == 1) DoubleTapFeedback(seekSec)
                }
            }
        }

        if (state.isBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = ControlWhite, strokeWidth = 3.dp)
        }

        // ── Subtitle overlay — always rendered, independent of controls ───────
        SubtitleOverlay(
            cue      = currentCue,
            prefs    = subPrefs,
            loading  = isLoadingSubs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (controlsVisible) 96.dp else 28.dp,
                    start  = 20.dp,
                    end    = 20.dp
                )
        )

        // ── Controls overlay ──────────────────────────────────────────────────
        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {

                // Top bar
                Box(Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
                    .align(Alignment.TopCenter)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = ControlWhite)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(shownTitle, color = ControlWhite, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = {}) {
                            Icon(Icons.Rounded.Settings, "Settings", tint = ControlWhite)
                        }
                    }
                }

                // Center controls
                Row(Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment     = Alignment.CenterVertically) {
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", tint = ControlWhite, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { controller.seekBackward(seekSec) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Replay10, "Rewind", tint = ControlWhite, modifier = Modifier.size(36.dp))
                    }
                    Surface(onClick = { controller.togglePlayPause() }, shape = CircleShape,
                        color = ControlWhite, modifier = Modifier.size(68.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                "Play/Pause", tint = Color.Black, modifier = Modifier.size(40.dp))
                        }
                    }
                    IconButton(onClick = { controller.seekForward(seekSec) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Forward10, "Forward", tint = ControlWhite, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, "Next", tint = ControlWhite, modifier = Modifier.size(32.dp))
                    }
                }

                // Bottom controls
                Column(Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.BottomCenter)) {
                    Slider(value = state.progress, onValueChange = { controller.seekToPercent(it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = SliderDefaults.colors(thumbColor = Color.White,
                            activeTrackColor = Color.White, inactiveTrackColor = TrackInactive))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Text("${state.currentTimeFormatted} / ${state.durationFormatted}",
                            color = ControlWhite, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val subTint = when {
                                isLoadingSubs                              -> ControlWhite.copy(0.5f)
                                subtitleCues.isNotEmpty() && subtitleEnabled -> MaterialTheme.colorScheme.primary
                                else                                       -> ControlWhite
                            }
                            IconButton(onClick = { showSubMenu = true; hideJob?.cancel() },
                                modifier = Modifier.size(36.dp)) {
                                Icon(if (subtitleCues.isNotEmpty() && subtitleEnabled)
                                    Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                                    "Subtitles", tint = subTint, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Audiotrack, "Audio", tint = ControlWhite, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Fullscreen, "Fullscreen", tint = ControlWhite, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Subtitle quick menu ───────────────────────────────────────────────
        if (showSubMenu) {
            SubtitleMenu(
                hasCues     = subtitleCues.isNotEmpty(),
                enabled     = subtitleEnabled,
                subtitleUri = subtitleUri,
                onToggle    = { subtitleEnabled = it; showSubMenu = false },
                onPickFile  = { srtPicker.launch(arrayOf("*/*")); showSubMenu = false },
                onDismiss   = { showSubMenu = false },
                modifier    = Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding().padding(end = 12.dp, bottom = 76.dp)
            )
        }
    }
}

// ── Subtitle overlay ──────────────────────────────────────────────────────────

@Composable
private fun SubtitleOverlay(
    cue:      SubtitleCue?,
    prefs:    SubtitlePrefs,
    loading:  Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Loading indicator
        AnimatedVisibility(loading, enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(0.55f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(12.dp), Color.White, strokeWidth = 1.5.dp)
                    Text("Loading subtitles…", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // Active cue — styled from SubtitlePrefs
        AnimatedVisibility(!loading && cue != null, enter = fadeIn(), exit = fadeOut()) {
            cue?.let {
                val bgColor = when (prefs.background) {
                    SubtitleBackground.BOX  -> prefs.bgColor.copy(alpha = prefs.bgAlpha)
                    else                    -> Color.Transparent
                }
                val shadow = when (prefs.background) {
                    SubtitleBackground.NONE        -> null
                    SubtitleBackground.SHADOW_ONLY -> Shadow(color = Color.Black, blurRadius = 12f)
                    SubtitleBackground.BOX         -> Shadow(color = Color.Black.copy(0.4f), blurRadius = 4f)
                }
                Surface(shape = RoundedCornerShape(4.dp), color = bgColor) {
                    Text(
                        text       = it.text,
                        color      = prefs.textColor,
                        fontSize   = prefs.fontSize.sp,
                        fontWeight = if (prefs.boldText) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = prefs.font.toFontFamily(),
                        textAlign  = TextAlign.Center,
                        lineHeight = (prefs.fontSize * 1.4f).sp,
                        style      = if (shadow != null) TextStyle(shadow = shadow) else TextStyle.Default,
                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── Subtitle quick-menu popup ─────────────────────────────────────────────────

@Composable
private fun SubtitleMenu(
    hasCues:     Boolean,
    enabled:     Boolean,
    subtitleUri: Uri?,
    onToggle:    (Boolean) -> Unit,
    onPickFile:  () -> Unit,
    onDismiss:   () -> Unit,
    modifier:    Modifier = Modifier
) {
    Card(modifier = modifier.width(210.dp), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1A1A)),
        elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text("Subtitles", color = Color.White.copy(0.55f), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            HorizontalDivider(color = Color.White.copy(0.1f))
            SubMenuRow("Off",  Icons.Rounded.SubtitlesOff, !enabled)     { onToggle(false) }
            if (hasCues)
                SubMenuRow("On", Icons.Rounded.Subtitles, enabled)       { onToggle(true)  }
            HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 2.dp))
            SubMenuRow(if (subtitleUri != null) "Change .srt file" else "Open .srt file",
                Icons.Rounded.FileOpen, false) { onPickFile() }
        }
    }
}

@Composable
private fun SubMenuRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else Color.White
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, color = tint, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        }
    }
}

// ── Double-tap feedback ───────────────────────────────────────────────────────

@Composable
private fun DoubleTapFeedback(seconds: Int) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Icon(if (seconds < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
                null, tint = Color.White, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(4.dp))
            Text("${if (seconds > 0) "+" else ""}${seconds}s", color = Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
