package com.example.dexplayer.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dexplayer.R
import com.example.dexplayer.player.SubtitleBackground
import com.example.dexplayer.player.SubtitleFont
import com.example.dexplayer.player.SubtitlePrefs
import com.example.dexplayer.player.ThemeMode
import com.example.dexplayer.player.VideoPrefs

// ── Navigation state ──────────────────────────────────────────────────────────

private enum class SettingsPage { ROOT, GENERAL, MUSIC_PLAYER, VIDEO_PLAYER }

// ── Root SettingsScreen ───────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    excludedFolders:        Set<String>     = emptySet(),
    onAddExcludedFolder:    (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    themeMode:              ThemeMode       = ThemeMode.SYSTEM,
    onSetTheme:             (ThemeMode) -> Unit = {},
    defaultShuffle:         Boolean         = false,
    onSetDefaultShuffle:    (Boolean) -> Unit = {},
    audioSessionId:         Int             = 0,
    videoPrefs:             VideoPrefs      = VideoPrefs(),
    onSetVideoPrefs:        (VideoPrefs) -> Unit = {},
    onBack:                 () -> Unit      = {}
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    // Intercept system back button on sub-pages — go to ROOT instead of closing Settings
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.ROOT) {
        page = SettingsPage.ROOT
    }

    when (page) {
        SettingsPage.ROOT -> RootSettingsScreen(
            excludedFolders        = excludedFolders,
            onAddExcludedFolder    = onAddExcludedFolder,
            onRemoveExcludedFolder = onRemoveExcludedFolder,
            onOpenGeneral          = { page = SettingsPage.GENERAL },
            onOpenMusicPlayer      = { page = SettingsPage.MUSIC_PLAYER },
            onOpenVideoPlayer      = { page = SettingsPage.VIDEO_PLAYER },
            onBack                 = onBack
        )
        SettingsPage.GENERAL -> GeneralSettingsScreen(
            themeMode   = themeMode,
            onSetTheme  = onSetTheme,
            onBack      = { page = SettingsPage.ROOT }
        )
        SettingsPage.MUSIC_PLAYER -> MusicPlayerSettingsScreen(
            defaultShuffle      = defaultShuffle,
            onSetDefaultShuffle = onSetDefaultShuffle,
            audioSessionId      = audioSessionId,
            onBack              = { page = SettingsPage.ROOT }
        )
        SettingsPage.VIDEO_PLAYER -> VideoPlayerSettingsScreen(
            videoPrefs      = videoPrefs,
            onSetVideoPrefs = onSetVideoPrefs,
            onBack          = { page = SettingsPage.ROOT }
        )
    }
}

// ── Root page ─────────────────────────────────────────────────────────────────

@Composable
private fun RootSettingsScreen(
    excludedFolders:        Set<String>,
    onAddExcludedFolder:    (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onOpenGeneral:          () -> Unit,
    onOpenMusicPlayer:      () -> Unit,
    onOpenVideoPlayer:      () -> Unit,
    onBack:                 () -> Unit
) {
    val context = LocalContext.current
    var pendingPath by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = resolveSafPath(uri)
            if (path.isNotBlank()) onAddExcludedFolder(path)
        }
    }

    Scaffold(
        topBar              = { SettingsTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        containerColor      = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Navigation tiles ──────────────────────────────────────────────
            item { SectionHeader("Settings") }
            item {
                SettingsNavItem(
                    icon     = Icons.Rounded.Tune,
                    title    = "General",
                    subtitle = "Theme, language",
                    onClick  = onOpenGeneral
                )
            }
            item {
                SettingsNavItem(
                    icon     = Icons.Rounded.MusicNote,
                    title    = "Music Player",
                    subtitle = "Shuffle, equalizer, crossfade",
                    onClick  = onOpenMusicPlayer
                )
            }
            item {
                SettingsNavItem(
                    icon     = Icons.Rounded.VideoSettings,
                    title    = "Video Player",
                    subtitle = "Seek, subtitles, screen",
                    onClick  = onOpenVideoPlayer
                )
            }

            // ── Excluded Folders ──────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_section_excluded_folders)) }
            item {
                SettingsItem(icon = Icons.Rounded.FolderOff,
                    title    = stringResource(R.string.settings_add_excluded_folder),
                    subtitle = stringResource(R.string.settings_add_excluded_subtitle),
                    tint     = MaterialTheme.colorScheme.primary,
                    onClick  = { folderPickerLauncher.launch(null) })
            }
            if (excludedFolders.isEmpty()) {
                item {
                    Text(stringResource(R.string.settings_no_excluded),
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            } else {
                items(excludedFolders.toList(), key = { it }) { path ->
                    ExcludedFolderItem(path = path, onRemove = { onRemoveExcludedFolder(path) })
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                SettingsItem(icon = Icons.Rounded.Info,
                    title    = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_about_version),
                    enabled  = false, onClick = {})
            }
        }
    }
}

// ── General settings page ─────────────────────────────────────────────────────

@Composable
private fun GeneralSettingsScreen(
    themeMode:  ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    onBack:     () -> Unit
) {
    var showThemePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar         = { SettingsTopBar(title = "General", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionHeader("Appearance") }
            item {
                val themeLabel = when (themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_follow)
                    ThemeMode.LIGHT  -> stringResource(R.string.settings_theme_light)
                    ThemeMode.DARK   -> stringResource(R.string.settings_theme_dark)
                }
                SettingsItem(icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_theme), subtitle = themeLabel,
                    onClick = { showThemePicker = true })
            }

            item { SectionHeader("System") }
            item {
                SettingsItem(icon = Icons.Rounded.Language,
                    title    = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_subtitle),
                    onClick  = {
                        val intent = Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data  = Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data  = Uri.fromParts("package", context.packageName, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                        }
                    })
            }
        }
    }

    if (showThemePicker) {
        AlertDialog(onDismissRequest = { showThemePicker = false },
            icon  = { Icon(Icons.Rounded.Palette, null) },
            title = { Text(stringResource(R.string.settings_choose_theme)) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeOption(stringResource(R.string.settings_theme_follow), Icons.Rounded.Brightness4,
                        themeMode == ThemeMode.SYSTEM) { onSetTheme(ThemeMode.SYSTEM); showThemePicker = false }
                    ThemeOption(stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode,
                        themeMode == ThemeMode.LIGHT)  { onSetTheme(ThemeMode.LIGHT);  showThemePicker = false }
                    ThemeOption(stringResource(R.string.settings_theme_dark),  Icons.Rounded.DarkMode,
                        themeMode == ThemeMode.DARK)   { onSetTheme(ThemeMode.DARK);   showThemePicker = false }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

// ── Music Player settings page ────────────────────────────────────────────────

@Composable
private fun MusicPlayerSettingsScreen(
    defaultShuffle:      Boolean,
    onSetDefaultShuffle: (Boolean) -> Unit,
    audioSessionId:      Int,
    onBack:              () -> Unit
) {
    val context          = LocalContext.current
    var showCrossfadeInfo by remember { mutableStateOf(false) }
    val eqLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {}

    Scaffold(
        topBar         = { SettingsTopBar(title = "Music Player", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionHeader("Playback") }
            item {
                SettingsToggleItem(icon = Icons.Rounded.Shuffle,
                    title    = stringResource(R.string.settings_default_shuffle),
                    subtitle = if (defaultShuffle) stringResource(R.string.settings_default_shuffle_on)
                               else stringResource(R.string.settings_default_shuffle_off),
                    checked  = defaultShuffle, onToggle = { onSetDefaultShuffle(it) })
            }
            item {
                SettingsItem(icon = Icons.Rounded.GraphicEq,
                    title    = stringResource(R.string.settings_equalizer),
                    subtitle = stringResource(R.string.settings_equalizer_subtitle),
                    onClick  = {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        }
                        try { eqLauncher.launch(intent) } catch (_: Exception) {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://search?q=equalizer&c=apps")))
                            } catch (_: Exception) {} }
                    })
            }
            item {
                SettingsItem(icon = Icons.Rounded.BlurOn,
                    title    = stringResource(R.string.settings_crossfade),
                    subtitle = stringResource(R.string.settings_crossfade_subtitle),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    onClick  = { showCrossfadeInfo = true })
            }
        }
    }

    if (showCrossfadeInfo) {
        AlertDialog(onDismissRequest = { showCrossfadeInfo = false },
            icon  = { Icon(Icons.Rounded.BlurOn, null) },
            title = { Text(stringResource(R.string.settings_crossfade)) },
            text  = { Text("Crossfade between tracks requires real-time audio mixing which needs a more complex audio pipeline. This feature is planned for a future update.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { showCrossfadeInfo = false }) { Text(stringResource(R.string.action_close)) } })
    }
}

// ── Video Player settings page ────────────────────────────────────────────────

@Composable
private fun VideoPlayerSettingsScreen(
    videoPrefs:      VideoPrefs,
    onSetVideoPrefs: (VideoPrefs) -> Unit,
    onBack:          () -> Unit
) {
    var showSeekPicker    by remember { mutableStateOf(false) }
    var showSubFontPicker by remember { mutableStateOf(false) }
    var showSubBgPicker   by remember { mutableStateOf(false) }
    var showSubColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker  by remember { mutableStateOf(false) }

    fun updateSubs(block: SubtitlePrefs.() -> SubtitlePrefs) =
        onSetVideoPrefs(videoPrefs.copy(subtitlePrefs = videoPrefs.subtitlePrefs.block()))

    Scaffold(
        topBar         = { SettingsTopBar(title = "Video Player", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionHeader("Playback") }
            item {
                SettingsItem(icon = Icons.Rounded.Forward10,
                    title    = "Double-tap seek",
                    subtitle = "${videoPrefs.doubleTapSeekSec} seconds per tap",
                    onClick  = { showSeekPicker = true })
            }
            item {
                SettingsToggleItem(icon = Icons.Rounded.ScreenLockLandscape,
                    title    = "Keep screen on",
                    subtitle = if (videoPrefs.keepScreenOn) "Screen stays on during playback"
                               else "Screen may turn off during playback",
                    checked  = videoPrefs.keepScreenOn,
                    onToggle = { onSetVideoPrefs(videoPrefs.copy(keepScreenOn = it)) })
            }
            item {
                SettingsToggleItem(icon = Icons.Rounded.BookmarkAdded,
                    title    = "Remember position",
                    subtitle = if (videoPrefs.rememberPosition) "Resumes from where you left off"
                               else "Always starts from the beginning",
                    checked  = videoPrefs.rememberPosition,
                    onToggle = { onSetVideoPrefs(videoPrefs.copy(rememberPosition = it)) })
            }

            // ── Subtitles ─────────────────────────────────────────────────────
            item { SubSectionHeader(Icons.Rounded.Subtitles, "Subtitles") }
            item { SubtitlePreviewCard(prefs = videoPrefs.subtitlePrefs) }

            // Font size stepper
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.FormatSize, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp))
                            Column {
                                Text("Font size", fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text("${videoPrefs.subtitlePrefs.fontSize.toInt()} sp",
                                    fontSize = 13.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick  = { if (videoPrefs.subtitlePrefs.fontSize > 10f)
                                    updateSubs { copy(fontSize = fontSize - 2f) } },
                                modifier = Modifier.size(34.dp)
                            ) { Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            Text("${videoPrefs.subtitlePrefs.fontSize.toInt()}",
                                modifier  = Modifier.width(32.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                fontSize  = 15.sp,
                                color     = MaterialTheme.colorScheme.primary)
                            FilledTonalIconButton(
                                onClick  = { if (videoPrefs.subtitlePrefs.fontSize < 40f)
                                    updateSubs { copy(fontSize = fontSize + 2f) } },
                                modifier = Modifier.size(34.dp)
                            ) { Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            item {
                SettingsToggleItem(icon = Icons.Rounded.FormatBold,
                    title    = "Bold text",
                    subtitle = if (videoPrefs.subtitlePrefs.boldText) "Bold subtitle text"
                               else "Normal weight",
                    checked  = videoPrefs.subtitlePrefs.boldText,
                    onToggle = { updateSubs { copy(boldText = it) } })
            }
            item {
                SettingsItem(icon = Icons.Rounded.FontDownload,
                    title    = "Font",
                    subtitle = videoPrefs.subtitlePrefs.font.displayName,
                    onClick  = { showSubFontPicker = true })
            }
            item {
                SettingsColorItem(icon = Icons.Rounded.FormatColorText,
                    title   = "Text color",
                    color   = videoPrefs.subtitlePrefs.textColor,
                    onClick = { showSubColorPicker = true })
            }
            item {
                SettingsItem(icon = Icons.Rounded.CropSquare,
                    title    = "Background style",
                    subtitle = videoPrefs.subtitlePrefs.background.displayName,
                    onClick  = { showSubBgPicker = true })
            }
            if (videoPrefs.subtitlePrefs.background == SubtitleBackground.BOX) {
                item {
                    SettingsColorItem(icon = Icons.Rounded.FormatColorFill,
                        title   = "Background color",
                        color   = videoPrefs.subtitlePrefs.bgColor,
                        onClick = { showBgColorPicker = true })
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Opacity, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp))
                            Column {
                                Text("Background opacity", fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground)
                                Text("${(videoPrefs.subtitlePrefs.bgAlpha * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(
                            value         = videoPrefs.subtitlePrefs.bgAlpha,
                            onValueChange = { updateSubs { copy(bgAlpha = it) } },
                            valueRange    = 0f..1f,
                            modifier      = Modifier.fillMaxWidth().padding(start = 34.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSeekPicker) {
        AlertDialog(onDismissRequest = { showSeekPicker = false },
            icon  = { Icon(Icons.Rounded.Forward10, null) },
            title = { Text("Double-tap seek duration") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { sec ->
                        ThemeOption("$sec seconds", Icons.Rounded.FastForward,
                            videoPrefs.doubleTapSeekSec == sec) {
                            onSetVideoPrefs(videoPrefs.copy(doubleTapSeekSec = sec))
                            showSeekPicker = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSeekPicker = false }) { Text(stringResource(R.string.action_cancel)) } })
    }

    if (showSubFontPicker) {
        AlertDialog(onDismissRequest = { showSubFontPicker = false },
            icon  = { Icon(Icons.Rounded.FontDownload, null) },
            title = { Text("Subtitle font") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SubtitleFont.entries.forEach { font ->
                        val selected = videoPrefs.subtitlePrefs.font == font
                        val bg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(0.5f)
                            else Color.Transparent, label = "bg")
                        Surface(onClick = { updateSubs { copy(font = font) }; showSubFontPicker = false },
                            shape = RoundedCornerShape(10.dp), color = bg) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Ag",
                                    fontFamily = font.toFontFamily(),
                                    fontSize   = 18.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurface,
                                    modifier   = Modifier.width(32.dp))
                                Text(font.displayName, fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color      = if (selected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurface,
                                    modifier   = Modifier.weight(1f))
                                if (selected) Icon(Icons.Rounded.Check, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubFontPicker = false }) { Text(stringResource(R.string.action_cancel)) } })
    }

    if (showSubBgPicker) {
        AlertDialog(onDismissRequest = { showSubBgPicker = false },
            icon  = { Icon(Icons.Rounded.CropSquare, null) },
            title = { Text("Subtitle background") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SubtitleBackground.entries.forEach { style ->
                        val icon = when (style) {
                            SubtitleBackground.BOX         -> Icons.Rounded.CropSquare
                            SubtitleBackground.SHADOW_ONLY -> Icons.Rounded.TextFields
                            SubtitleBackground.NONE        -> Icons.Rounded.FormatClear
                        }
                        ThemeOption(style.displayName, icon, videoPrefs.subtitlePrefs.background == style) {
                            updateSubs { copy(background = style) }; showSubBgPicker = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubBgPicker = false }) { Text(stringResource(R.string.action_cancel)) } })
    }

    if (showSubColorPicker) {
        ColorPickerDialog(title = "Text color", currentColor = videoPrefs.subtitlePrefs.textColor,
            onPick    = { updateSubs { copy(textColorInt = it.toArgb()) }; showSubColorPicker = false },
            onDismiss = { showSubColorPicker = false })
    }

    if (showBgColorPicker) {
        ColorPickerDialog(title = "Background color", currentColor = videoPrefs.subtitlePrefs.bgColor,
            onPick    = { updateSubs { copy(bgColorInt = it.toArgb()) }; showBgColorPicker = false },
            onDismiss = { showBgColorPicker = false })
    }
}

// ── Navigation tile ───────────────────────────────────────────────────────────

@Composable
private fun SettingsNavItem(
    icon:    ImageVector,
    title:   String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color   = Color.Transparent
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier        = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    color = MaterialTheme.colorScheme.onBackground,     fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

// ── Subtitle live preview card ────────────────────────────────────────────────

@Composable
private fun SubtitlePreviewCard(prefs: SubtitlePrefs) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("▶  video preview", color = Color.White.copy(0.12f), fontSize = 11.sp)
        val bgColor = when (prefs.background) {
            SubtitleBackground.BOX -> prefs.bgColor.copy(alpha = prefs.bgAlpha)
            else                   -> Color.Transparent
        }
        Surface(
            shape    = RoundedCornerShape(4.dp),
            color    = bgColor,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        ) {
            Text(
                "Sample subtitle text",
                color      = prefs.textColor,
                fontSize   = (prefs.fontSize * 0.65f).sp,
                fontWeight = if (prefs.boldText) FontWeight.Bold else FontWeight.Normal,
                fontFamily = prefs.font.toFontFamily(),
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ── Color picker dialog ───────────────────────────────────────────────────────

private val colorSwatches = listOf(
    Color.White,       Color(0xFFFFFDE7), Color(0xFFFFEB3B), Color(0xFFFF9800),
    Color(0xFFFF5252), Color(0xFFFF4081), Color(0xFF69F0AE), Color(0xFF40C4FF),
    Color(0xFF536DFE), Color(0xFF9C27B0), Color.Black,       Color(0xFF212121),
    Color(0xFF424242), Color(0xFF757575),
)

@Composable
private fun ColorPickerDialog(
    title:        String,
    currentColor: Color,
    onPick:       (Color) -> Unit,
    onDismiss:    () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Palette, null) },
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(40.dp)
                    .clip(RoundedCornerShape(8.dp)).background(currentColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(8.dp)))
                colorSwatches.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { color ->
                            val selected = color == currentColor
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(0.25f),
                                        shape = CircleShape
                                    )
                                    .clickable { onPick(color) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null,
                                    tint     = if (color.luminance() > 0.4f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// ── SubtitleFont → FontFamily ─────────────────────────────────────────────────

fun SubtitleFont.toFontFamily(): FontFamily = when (this) {
    SubtitleFont.SANS    -> FontFamily.SansSerif
    SubtitleFont.SERIF   -> FontFamily.Serif
    SubtitleFont.MONO    -> FontFamily.Monospace
    SubtitleFont.CURSIVE -> FontFamily.Cursive
    else                 -> FontFamily.Default
}

// ── Sub-section header ────────────────────────────────────────────────────────

@Composable
private fun SubSectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(title.uppercase(), color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

// ── Color swatch settings item ────────────────────────────────────────────────

@Composable
private fun SettingsColorItem(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(28.dp).clip(CircleShape).background(color)
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(0.35f), CircleShape))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ── Theme option row ──────────────────────────────────────────────────────────

@Composable
private fun ThemeOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent, label = "bg")
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = bg) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null,
                tint     = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Text(label, fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title          = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor             = MaterialTheme.colorScheme.background,
            titleContentColor          = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground)
    )
}

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp))
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

// ── Settings Item ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String,
    enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.45f
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),     fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), fontSize = 13.sp)
        }
        if (enabled) Icon(Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ── Settings Toggle Item ──────────────────────────────────────────────────────

@Composable
private fun SettingsToggleItem(icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle(!checked) }
        .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    color = MaterialTheme.colorScheme.onBackground,     fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

// ── Excluded Folder Item ──────────────────────────────────────────────────────

@Composable
private fun ExcludedFolderItem(path: String, onRemove: () -> Unit) {
    val displayPath = path.trimEnd('/').split("/").takeLast(2).joinToString("/")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FolderOff, null,
                tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(displayPath, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(path, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.RemoveCircleOutline, "Remove",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
    }
}

// ── SAF path resolver ─────────────────────────────────────────────────────────

private fun resolveSafPath(treeUri: Uri): String {
    return try {
        val docId  = DocumentsContract.getTreeDocumentId(treeUri)
        val parts  = docId.split(":")
        if (parts.size >= 2) {
            val volume   = parts[0]
            val relative = parts[1]
            if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0/$relative"
            else "/storage/$volume/$relative"
        } else treeUri.toString()
    } catch (_: Exception) { treeUri.toString() }
}
