package com.example.dexplayer

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  SCALE — 125%
// ─────────────────────────────────────────────

private val IconSizeSm   = 22   // era 18
private val IconSizeMd   = 20   // era 16
private val IconBtnSize  = 38   // era 28-32
private val PlayBtnSize  = 40   // era 32
private val PlayIconSize = 22   // era 18
private val TimecodeSize = 13   // era 11sp
private val StatusSize   = 12   // era 10sp
private val MenuIconSize = 17   // era 14
private val MenuTextSize = 14   // era 12sp
private val SeekHeight   = 30   // era 24dp
private val VolumeWidth  = 100  // era 80dp
private val BarPadV      = 4    // era 2dp vertical padding toolbar
private val BarPadH      = 6    // era 4dp horizontal padding toolbar

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
//  MAIN SCREEN
// ─────────────────────────────────────────────

@Composable
fun PlayerScreen() {
    val scope  = rememberCoroutineScope()
    val colors = playerColors()

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying       by remember { mutableStateOf(false) }
    var progress        by remember { mutableStateOf(0.28f) }
    var volume          by remember { mutableStateOf(0.75f) }
    var isMuted         by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset      by remember { mutableStateOf(DpOffset.Zero) }
    var expandedSub     by remember { mutableStateOf<String?>(null) }

    fun resetHide() {
        scope.launch {
            delay(3500)
            if (isPlaying) controlsVisible = false
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
                        if (controlsVisible && isPlaying) resetHide()
                    }
                )
            }
    ) {
        // ── Video area ──────────────────────────────
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.size(96.dp)
            )
        }

        // ── Top bar ─────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it },
            exit  = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it }
        ) {
            MpcTopBar(colors)
        }

        // ── Bottom controls ─────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it },
            exit  = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it }
        ) {
            MpcBottomBar(
                colors           = colors,
                isPlaying        = isPlaying,
                progress         = progress,
                volume           = volume,
                isMuted          = isMuted,
                onPlayPause      = { isPlaying = !isPlaying; if (isPlaying) resetHide() },
                onProgressChange = { progress = it },
                onVolumeChange   = { volume = it },
                onMuteToggle     = { isMuted = !isMuted }
            )
        }

        // ── Context menu ────────────────────────────
        if (showContextMenu) {
            MpcContextMenu(
                items       = contextMenuItems,
                offset      = menuOffset,
                expandedSub = expandedSub,
                colors      = colors,
                onExpandSub = { expandedSub = if (expandedSub == it) null else it },
                onDismiss   = { showContextMenu = false; expandedSub = null }
            )
        }
    }
}

// ─────────────────────────────────────────────
//  TOP BAR
// ─────────────────────────────────────────────

@Composable
fun MpcTopBar(colors: PlayerColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(colors.bgToolbar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Blade.Runner.2049.2017.2160p.UHD.BluRay.HEVC.TrueHD.7.1.Atmos-HUNO.mkv",
                color = colors.textSecondary,
                fontSize = TimecodeSize.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            MpcIconBtn(
                icon   = Icons.Rounded.MoreVert,
                desc   = "Menu",
                size   = IconSizeMd,
                colors = colors
            )
        }
    }
}

// ─────────────────────────────────────────────
//  BOTTOM BAR
// ─────────────────────────────────────────────

@Composable
fun MpcBottomBar(
    colors: PlayerColors,
    isPlaying: Boolean,
    progress: Float,
    volume: Float,
    isMuted: Boolean,
    onPlayPause: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(colors.bgToolbar)
    ) {
        // ── Seekbar ──────────────────────────────────
        Slider(
            value = progress,
            onValueChange = onProgressChange,
            colors = SliderDefaults.colors(
                thumbColor         = AccentBlue,
                activeTrackColor   = AccentBlue,
                inactiveTrackColor = SeekTrack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekHeight.dp)
                .padding(horizontal = 8.dp)
        )

        // ── Toolbar ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BarPadH.dp, vertical = BarPadV.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MpcIconBtn(Icons.Rounded.SkipPrevious, "Previous", colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.Replay10,     "−10s",     colors = colors, size = IconSizeSm)

            // Play/Pause
            Box(
                modifier = Modifier
                    .size(PlayBtnSize.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.bgHover)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(PlayIconSize.dp)
                )
            }

            MpcIconBtn(Icons.Rounded.Forward10, "+10s", colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.SkipNext,  "Next", colors = colors, size = IconSizeSm)

            Spacer(Modifier.width(8.dp))

            Text(
                text = "01:24:33 / 02:43:52",
                color = colors.textSecondary,
                fontSize = TimecodeSize.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.weight(1f))

            MpcIconBtn(
                icon = if (isMuted) Icons.Rounded.VolumeOff
                       else if (volume > 0.5f) Icons.Rounded.VolumeUp
                       else Icons.Rounded.VolumeDown,
                desc    = "Mute",
                colors  = colors,
                size    = IconSizeSm,
                onClick = onMuteToggle
            )

            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = onVolumeChange,
                colors = SliderDefaults.colors(
                    thumbColor         = colors.textSecondary,
                    activeTrackColor   = colors.textSecondary,
                    inactiveTrackColor = SeekTrack
                ),
                modifier = Modifier
                    .width(VolumeWidth.dp)
                    .height(SeekHeight.dp)
            )

            Spacer(Modifier.width(10.dp))
            HorizontalDivider(
                modifier  = Modifier.height(20.dp).width(0.5.dp),
                color     = colors.border
            )
            Spacer(Modifier.width(10.dp))

            MpcIconBtn(Icons.Rounded.Subtitles,  "Subtitles",  colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.QueueMusic, "Playlist",   colors = colors, size = IconSizeSm)
            MpcIconBtn(Icons.Rounded.Fullscreen, "Fullscreen", colors = colors, size = IconSizeSm)
        }

        // ── Status bar ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "2160p • HEVC • TrueHD 7.1 • 60fps",
                color      = Color(0xFFFFFFFF),
                fontSize   = StatusSize.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                "CPU: 12%  •  HW Decode",
                color      = Color(0xFFFFFFFF),
                fontSize   = StatusSize.sp,
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
                                color    = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint     = colors.textSecondary,
                                modifier = Modifier.size(MenuIconSize.dp)
                            )
                        }
                    },
                    leadingIcon = item.icon?.let {
                        { Icon(it, null, tint = colors.textSecondary,
                            modifier = Modifier.size(MenuIconSize.dp)) }
                    },
                    onClick  = { onExpandSub(item.label) },
                    colors   = MenuDefaults.itemColors(textColor = colors.textPrimary),
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
                                onClick  = { sub.onClick(); onDismiss() },
                                modifier = Modifier.padding(start = 14.dp).height(32.dp),
                                colors   = MenuDefaults.itemColors(textColor = colors.textPrimary)
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
                                color    = colors.textPrimary,
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
                    onClick  = { item.onClick(); onDismiss() },
                    colors   = MenuDefaults.itemColors(textColor = colors.textPrimary),
                    modifier = Modifier.height(36.dp)
                )
            }

            if (item.dividerAfter) {
                HorizontalDivider(
                    color     = colors.border,
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(vertical = 2.dp)
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
                indication        = LocalIndication.current,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = desc,
            tint               = colors.textSecondary,
            modifier           = Modifier.size(size.dp)
        )
    }
}
