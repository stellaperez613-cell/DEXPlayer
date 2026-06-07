

package com.example.dexplayer.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.dexplayer.util.ScrollRefreshBoost
import com.example.dexplayer.R
import com.example.dexplayer.player.Playlist
import com.example.dexplayer.player.PlaylistItem
import com.example.dexplayer.player.PlayerController
import com.example.dexplayer.player.RepeatMode
import com.example.dexplayer.player.PlayerState
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

// ── Color contrast utility ────────────────────────────────────────────────────
//
// Calcula la luminancia relativa de un Color según la fórmula WCAG 2.1.
// Devuelve un valor entre 0 (negro absoluto) y 1 (blanco absoluto).
private fun Color.relativeLuminance(): Float {
    fun linearize(c: Float) =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linearize(red) + 0.7152f * linearize(green) + 0.0722f * linearize(blue)
}

// Ratio de contraste WCAG entre dos colores (siempre ≥ 1).
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = maxOf(la, lb)
    val darker  = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Devuelve [this] si tiene suficiente contraste contra [background] (ratio ≥ [minRatio]),
 * o [fallback] en caso contrario.
 *
 * Usado para garantizar que el color dominante de un artwork sea legible
 * como texto o elemento de UI, sin importar si la portada es negra, blanca o gris.
 */
private fun Color.orFallbackIfLowContrast(
    background: Color,
    fallback:   Color,
    minRatio:   Float = 3.0f          // AA para texto grande; usar 4.5 para texto pequeño
): Color = if (contrastRatio(this, background) >= minRatio) this else fallback

// ── Screen entry point ────────────────────────────────────────────────────────
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MobileMusicScreen(
    controller:             PlayerController,
    state:                  PlayerState,
    displayTitle:           String          = "",
    displayArtist:          String          = "",
    shuffleEnabled:         Boolean         = false,
    repeatMode:             RepeatMode      = RepeatMode.NONE,
    playlists:              List<Playlist>  = emptyList(),
    sleepTimerMinutes:      Int?            = null,
    onBackPressed:          () -> Unit      = {},
    onSkipNext:             () -> Unit      = {},
    onSkipPrevious:         () -> Unit      = {},
    onToggleShuffle:        () -> Unit      = {},
    onCycleRepeat:          () -> Unit      = {},
    onAddToPlaylist:        (String, List<PlaylistItem>) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (String, List<PlaylistItem>) -> Unit = { _, _ -> },
    onInsertNext:           (PlaylistItem) -> Unit = {},
    onSetSleepTimer:        (Int) -> Unit   = {},
    onCancelSleepTimer:     () -> Unit      = {}
) {
    val context         = LocalContext.current
    val activity        = context as Activity
    val windowSizeClass = calculateWindowSizeClass(activity)
    val configuration   = LocalConfiguration.current
    val scope           = rememberCoroutineScope()
    val snackbarState   = remember { SnackbarHostState() }

    val isLandscape     = configuration.screenWidthDp > configuration.screenHeightDp
    val isCompactWidth  = windowSizeClass.widthSizeClass  == WindowWidthSizeClass.Compact
    val isCompactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

    var artwork       by remember { mutableStateOf<Bitmap?>(null) }
    var dominantColor by remember { mutableStateOf<Color?>(null) }

    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSleepDialog   by remember { mutableStateOf(false) }
    var showTrackInfo     by remember { mutableStateOf(false) }

    val currentItem = remember(state.mediaItem, displayTitle, displayArtist) {
        val uri = state.mediaItem?.localConfiguration?.uri ?: return@remember null
        PlaylistItem(
            uri     = uri,
            isVideo = false,
            title   = displayTitle.ifBlank { state.title },
            artist  = displayArtist.ifBlank { state.artist }
        )
    }

    LaunchedEffect(state.mediaItem) {
        artwork = null
        dominantColor = null
        val uri = state.mediaItem?.localConfiguration?.uri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(context, uri)
                val bytes = r.embeddedPicture
                if (bytes != null) {
                    artwork = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    artwork?.let { bmp ->
                        val scaled = Bitmap.createScaledBitmap(bmp, 1, 1, false)
                        dominantColor = Color(scaled.getPixel(0, 0)).copy(alpha = 1f)
                    }
                } else {
                    dominantColor = null
                }
                r.release()
            } catch (_: Exception) {}
        }
    }

    // ── Color contrast ────────────────────────────────────────────────────────
    // El color dominante se extrae tal cual del artwork (puede ser negro, blanco o gris).
    // Antes de usarlo en texto o UI, verificamos que tenga suficiente contraste
    // contra el fondo. Si no, usamos el color primario del tema (gold).
    val isDark   = isSystemInDarkTheme()
    val bgColor  = MaterialTheme.colorScheme.background
    val primary  = MaterialTheme.colorScheme.primary

    // safeAccent: color legible garantizado para texto/slider sobre el fondo.
    // minRatio 4.5 = WCAG AA para texto normal.
    val safeAccent: Color? = dominantColor?.orFallbackIfLowContrast(
        background = bgColor,
        fallback   = primary,
        minRatio   = 4.5f
    )

    // Para el gradiente de fondo usamos el color crudo (con alpha alto) — no necesita
    // contraste de texto, solo aporta atmósfera. Incluso un negro queda bien aquí.
    val gradient = Brush.verticalGradient(
        listOf(
            dominantColor?.copy(alpha = 0.55f)
                ?: primary.copy(alpha = 0.30f),
            dominantColor?.copy(alpha = 0.15f)
                ?: bgColor,
            bgColor
        )
    )

    // Pre-extract strings in composable scope — avoids "Querying resource values using
    // LocalContext.current" Lint warning when context.getString() is called inside lambdas.
    val msgPlayingNext    = stringResource(R.string.msg_playing_next)
    val msgAddedTo        = stringResource(R.string.msg_added_to)   // e.g. "Added to"
    val msgSleepCancelled = stringResource(R.string.msg_sleep_timer_cancelled)
    val msgSleepSet       = stringResource(R.string.msg_sleep_timer_set)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // Atmospheric blurred artwork layer (new — matches your original Box structure)
        artwork?.let { bmp ->
            Image(
                bitmap           = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale     = ContentScale.Crop,
                modifier         = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .graphicsLayer {
                        alpha      = 0.18f
                        renderEffect = null  // blur via alpha only for compat
                    }
            )
        }
        // Gradient overlay — same position as original
        Box(Modifier.fillMaxSize().background(gradient))

        if (isLandscape) {
            LandscapeLayout(
                controller = controller, state = state, artwork = artwork,
                displayTitle = displayTitle, displayArtist = displayArtist,
                shuffleEnabled = shuffleEnabled, repeatMode = repeatMode,
                sleepTimerMinutes = sleepTimerMinutes, isCompactHeight = isCompactHeight,
                dominantColor = safeAccent,
                currentItem        = currentItem,
                onBackPressed      = onBackPressed, onSkipNext = onSkipNext,
                onSkipPrevious     = onSkipPrevious, onToggleShuffle = onToggleShuffle,
                onCycleRepeat      = onCycleRepeat,
                onAddToPlaylist    = { showAddToPlaylist = true },
                onPlayNext         = { currentItem?.let { onInsertNext(it) }; scope.launch { snackbarState.showSnackbar(msgPlayingNext) } },
                onShare            = { currentItem?.let { item -> try { val intent = Intent(Intent.ACTION_SEND).apply { type = "audio/*"; putExtra(Intent.EXTRA_STREAM, item.uri); putExtra(Intent.EXTRA_SUBJECT, item.title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, null)) } catch (_: Exception) {} } },
                onSleepTimer       = { showSleepDialog = true },
                onCancelSleepTimer = { onCancelSleepTimer(); scope.launch { snackbarState.showSnackbar(msgSleepCancelled) } },
                onTrackInfo        = { showTrackInfo = true }
            )
        } else {
            PortraitLayout(
                controller = controller, state = state, artwork = artwork,
                displayTitle = displayTitle, displayArtist = displayArtist,
                shuffleEnabled = shuffleEnabled, repeatMode = repeatMode,
                sleepTimerMinutes = sleepTimerMinutes, isCompactWidth = isCompactWidth,
                dominantColor = safeAccent,
                currentItem        = currentItem,
                onBackPressed      = onBackPressed, onSkipNext = onSkipNext,
                onSkipPrevious     = onSkipPrevious, onToggleShuffle = onToggleShuffle,
                onCycleRepeat      = onCycleRepeat,
                onAddToPlaylist    = { showAddToPlaylist = true },
                onPlayNext         = { currentItem?.let { onInsertNext(it) }; scope.launch { snackbarState.showSnackbar(msgPlayingNext) } },
                onShare            = { currentItem?.let { item -> try { val intent = Intent(Intent.ACTION_SEND).apply { type = "audio/*"; putExtra(Intent.EXTRA_STREAM, item.uri); putExtra(Intent.EXTRA_SUBJECT, item.title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, null)) } catch (_: Exception) {} } },
                onSleepTimer       = { showSleepDialog = true },
                onCancelSleepTimer = { onCancelSleepTimer(); scope.launch { snackbarState.showSnackbar(msgSleepCancelled) } },
                onTrackInfo        = { showTrackInfo = true }
            )
        }

        SnackbarHost(
            hostState = snackbarState,
            modifier  = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) { data ->
            Snackbar(snackbarData = data, shape = RoundedCornerShape(10.dp))
        }
    }

    // ── Add to playlist sheet ─────────────────────────────────────────────────
    if (showAddToPlaylist && currentItem != null) {
        AddToPlaylistSheet(
            item            = currentItem,
            playlists       = playlists,
            onAddToPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, listOf(currentItem))
                val name = playlists.find { it.id == playlistId }?.name ?: "playlist"
                scope.launch { snackbarState.showSnackbar("$msgAddedTo \"$name\"") }
            },
            onCreateAndAdd  = { name ->
                onCreatePlaylistAndAdd(name, listOf(currentItem))
                scope.launch { snackbarState.showSnackbar("$msgAddedTo \"$name\"") }
            },
            onPlayNext      = {
                onInsertNext(currentItem)
                scope.launch { snackbarState.showSnackbar(msgPlayingNext) }
            },
            onDismiss       = { showAddToPlaylist = false }
        )
    }

    // ── 3-dot more menu ───────────────────────────────────────────────────────

    if (showSleepDialog) {
        SleepTimerDialog(
            current   = sleepTimerMinutes,
            onSelect  = { minutes ->
                showSleepDialog = false
                onSetSleepTimer(minutes)
                scope.launch { snackbarState.showSnackbar("$msgSleepSet $minutes min") }
            },
            onDismiss = { showSleepDialog = false }
        )
    }

    if (showTrackInfo && currentItem != null) {
        TrackInfoDialog(item = currentItem, state = state, onDismiss = { showTrackInfo = false })
    }
}

// ── Portrait layout — EXACT original structure, no scroll in practice ─────────
// SpaceBetween + verticalScroll = distributes space, scroll only if truly needed
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PortraitLayout(
    controller: PlayerController, state: PlayerState, artwork: Bitmap?,
    displayTitle: String, displayArtist: String,
    shuffleEnabled: Boolean, repeatMode: RepeatMode, sleepTimerMinutes: Int?,
    isCompactWidth: Boolean, dominantColor: Color?,
    currentItem:        PlaylistItem?,
    onBackPressed:      () -> Unit, onSkipNext: () -> Unit, onSkipPrevious: () -> Unit,
    onToggleShuffle:    () -> Unit, onCycleRepeat: () -> Unit,
    onAddToPlaylist:    () -> Unit,
    onPlayNext:         () -> Unit,
    onShare:            () -> Unit,
    onSleepTimer:       () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onTrackInfo:        () -> Unit
) {
    val scrollState = rememberScrollState()
    ScrollRefreshBoost(scrollState)
    Column(
        modifier            = Modifier.fillMaxSize().statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = if (isCompactWidth) 16.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween      // ← original: fills screen, no scroll
    ) {
        TopBar(
            onBackPressed      = onBackPressed,
            sleepTimerMinutes  = sleepTimerMinutes,
            currentItem        = currentItem,
            onAddToPlaylist    = onAddToPlaylist,
            onPlayNext         = onPlayNext,
            onShare            = onShare,
            onSleepTimer       = onSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onTrackInfo        = onTrackInfo
        )

        // Artwork — same sizeFraction as original
        ArtworkBox(
            artwork      = artwork,
            sizeFraction = if (isCompactWidth) 0.90f else 0.65f,
            dominantColor = dominantColor
        )

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (isCompactWidth) 12.dp else 24.dp))
            TrackInfo(state, displayTitle, displayArtist, onAddToPlaylist, dominantColor)
            Spacer(Modifier.height(if (isCompactWidth) 8.dp else 12.dp))
            ProgressSection(controller, state, dominantColor)
            Spacer(Modifier.height(if (isCompactWidth) 4.dp else 8.dp))
            ControlsRow(
                controller, state, shuffleEnabled, repeatMode,
                onSkipNext, onSkipPrevious, onToggleShuffle, onCycleRepeat,
                dominantColor = dominantColor
            )
            Spacer(Modifier.height(if (isCompactWidth) 48.dp else 64.dp))
        }
    }
}

// ── Landscape layout — original structure preserved ───────────────────────────
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LandscapeLayout(
    controller: PlayerController, state: PlayerState, artwork: Bitmap?,
    displayTitle: String, displayArtist: String,
    shuffleEnabled: Boolean, repeatMode: RepeatMode, sleepTimerMinutes: Int?,
    isCompactHeight: Boolean, dominantColor: Color?,
    currentItem:        PlaylistItem?,
    onBackPressed:      () -> Unit, onSkipNext: () -> Unit, onSkipPrevious: () -> Unit,
    onToggleShuffle:    () -> Unit, onCycleRepeat: () -> Unit,
    onAddToPlaylist:    () -> Unit,
    onPlayNext:         () -> Unit,
    onShare:            () -> Unit,
    onSleepTimer:       () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onTrackInfo:        () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxSize().statusBarsPadding()
            .navigationBarsPadding().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.fillMaxHeight().weight(1f)
                .padding(vertical = if (isCompactHeight) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) { ArtworkBox(artwork, 0.8f, dominantColor) }

        Spacer(Modifier.width(24.dp))

        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TopBar(
                onBackPressed      = onBackPressed,
                sleepTimerMinutes  = sleepTimerMinutes,
                currentItem        = currentItem,
                onAddToPlaylist    = onAddToPlaylist,
                onPlayNext         = onPlayNext,
                onShare            = onShare,
                onSleepTimer       = onSleepTimer,
                onCancelSleepTimer = onCancelSleepTimer,
                onTrackInfo        = onTrackInfo
            )
            Spacer(Modifier.height(if (isCompactHeight) 8.dp else 16.dp))
            TrackInfo(state, displayTitle, displayArtist, onAddToPlaylist, dominantColor)
            Spacer(Modifier.height(if (isCompactHeight) 8.dp else 12.dp))
            ProgressSection(controller, state, dominantColor)
            Spacer(Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
            ControlsRow(controller, state, shuffleEnabled, repeatMode,
                onSkipNext, onSkipPrevious, onToggleShuffle, onCycleRepeat,
                compact = isCompactHeight, dominantColor = dominantColor)
            Spacer(Modifier.height(if (isCompactHeight) 8.dp else 16.dp))
        }
    }
}

// ── Top bar — original sizing, new subtle styling ─────────────────────────────

@Composable
private fun TopBar(
    onBackPressed:      () -> Unit,
    sleepTimerMinutes:  Int?,
    currentItem:        PlaylistItem?,
    onAddToPlaylist:    () -> Unit,
    onPlayNext:         () -> Unit,
    onShare:            () -> Unit,
    onSleepTimer:       () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onTrackInfo:        () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackPressed) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.action_minimize),
                tint = MaterialTheme.colorScheme.onBackground)
        }

        // Sleep timer badge — animated (new: uses primaryContainer)
        AnimatedVisibility(
            visible = sleepTimerMinutes != null,
            enter   = fadeIn() + scaleIn(),
            exit    = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bedtime, null,
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${sleepTimerMinutes}m",
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.ctrl_more),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false }
            ) {

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null, modifier = Modifier.size(20.dp)) },
                    text        = { Text(stringResource(R.string.menu_add_to_playlist)) },
                    onClick     = { expanded = false; onAddToPlaylist() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null, modifier = Modifier.size(20.dp)) },
                    text        = { Text(stringResource(R.string.menu_play_next)) },
                    onClick     = { expanded = false; onPlayNext() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(20.dp)) },
                    text        = { Text(stringResource(R.string.action_share)) },
                    onClick     = { expanded = false; onShare() }
                )
                HorizontalDivider()
                if (sleepTimerMinutes != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.BedtimeOff, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)) },
                        text    = { Text(stringResource(R.string.menu_cancel_sleep_timer, sleepTimerMinutes),
                            color = MaterialTheme.colorScheme.error) },
                        onClick = { expanded = false; onCancelSleepTimer() }
                    )
                } else {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null, modifier = Modifier.size(20.dp)) },
                        text        = { Text(stringResource(R.string.menu_sleep_timer)) },
                        onClick     = { expanded = false; onSleepTimer() }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp)) },
                    text        = { Text(stringResource(R.string.menu_track_info)) },
                    onClick     = { expanded = false; onTrackInfo() }
                )
            }
        }
    }
}

// ── Artwork — original sizing + new glow shadow effect ───────────────────────

@Composable
private fun ArtworkBox(artwork: Bitmap?, sizeFraction: Float, dominantColor: Color?) {
    val glowColor  = dominantColor?.copy(alpha = 0.50f)
        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Box(
        Modifier
            .fillMaxWidth(sizeFraction)
            .aspectRatio(1f)
            // NEW: colored glow shadow using dominant color
            .shadow(
                elevation    = 24.dp,
                shape        = RoundedCornerShape(12.dp),
                ambientColor = glowColor,
                spotColor    = glowColor
            )
            .clip(RoundedCornerShape(12.dp))   // slightly more rounded than original 8.dp
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null)
            Image(artwork.asImageBitmap(), stringResource(R.string.track_info_album_art),
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            // NEW: radial gradient placeholder instead of flat color
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.surfaceVariant
                    ))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(60.dp))
            }
    }
}

// ── Track info — original sizes, artist in gold (new) ────────────────────────

@Composable
private fun TrackInfo(
    state: PlayerState, displayTitle: String, displayArtist: String,
    onAddToPlaylist: () -> Unit, dominantColor: Color?
) {
    val title  = displayTitle.ifBlank  { state.title.ifBlank  { stringResource(R.string.unknown_track)  } }
    val artist = displayArtist.ifBlank { state.artist.ifBlank { stringResource(R.string.unknown_artist) } }

    // NEW: artist gets the accent/gold color
    val artistColor = dominantColor?.copy(alpha = 0.85f)
        ?: MaterialTheme.colorScheme.primary

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,
                color      = MaterialTheme.colorScheme.onBackground,
                fontSize   = 18.sp,                             // ← original size
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis)
            Text(artist,
                color    = artistColor,                          // ← NEW: gold/dominant
                fontSize = 13.sp,                               // ← original size
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Rounded.PlaylistAdd, stringResource(R.string.menu_add_to_playlist),
                tint     = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp))
        }
    }
}

// ── Progress — original sizes, slider en color safe (contraste garantizado) ───
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ProgressSection(controller: PlayerController, state: PlayerState, dominantColor: Color?) {
    val trackColor = dominantColor ?: MaterialTheme.colorScheme.onBackground

    // isSeeking: desacopla el slider del poll de progreso mientras el usuario arrastra.
    // Solo hace el seek al soltar — evita conflicto entre drag y actualizaciones de posición.
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.currentPosition) {
        if (!isSeeking) seekValue = state.progress
    }

    Slider(
        value               = seekValue,
        onValueChange       = { isSeeking = true; seekValue = it },
        onValueChangeFinished = {
            controller.seekToPercent(seekValue)
            isSeeking = false
        },
        modifier = Modifier.fillMaxWidth(),
        colors   = SliderDefaults.colors(
            thumbColor         = trackColor,
            activeTrackColor   = trackColor,
            inactiveTrackColor = trackColor.copy(alpha = 0.28f)
        )
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val displayMs = if (isSeeking && state.duration > 0)
            (seekValue * state.duration).toLong()
        else
            state.currentPosition
        Text(state.formatTime(displayMs),
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp)
        Text(state.durationFormatted,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp)
    }
}

// ── Controls — original sizes + play button gradient (new) ───────────────────
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ControlsRow(
    controller:      PlayerController,
    state:           PlayerState,
    shuffleEnabled:  Boolean,
    repeatMode:      RepeatMode,
    onSkipNext:      () -> Unit,
    onSkipPrevious:  () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat:   () -> Unit,
    compact:         Boolean = false,
    dominantColor:   Color?
) {
    // ← original sizes exactly
    val playSize  = if (compact) 48.dp else 60.dp
    val playIcon  = if (compact) 24.dp else 30.dp
    val skipSize  = if (compact) 40.dp else 52.dp
    val skipIcon  = if (compact) 26.dp else 34.dp

    val onBg        = MaterialTheme.colorScheme.onBackground
    val inactive    = MaterialTheme.colorScheme.onSurfaceVariant
    val shuffleTint = if (shuffleEnabled) onBg else inactive
    val repeatTint  = if (repeatMode != RepeatMode.NONE) onBg else inactive
    val repeatIcon  = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne
                      else Icons.Rounded.Repeat

    // NEW: play button uses dominant color or gold from theme
    val playBgColor = dominantColor?.copy(alpha = 0.90f)
        ?: MaterialTheme.colorScheme.primary

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,        // ← original: SpaceEvenly
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Shuffle — original size
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.ctrl_shuffle),
                tint = shuffleTint, modifier = Modifier.size(22.dp))
        }

        // Skip prev — original size
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(skipSize)) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.ctrl_skip_previous),
                tint = onBg, modifier = Modifier.size(skipIcon))
        }

        // Play/Pause — original size, NEW: colored background instead of flat onBg
        Surface(
            onClick   = { controller.togglePlayPause() },
            shape     = CircleShape,
            color     = playBgColor,
            modifier  = Modifier.size(playSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(R.string.ctrl_play_pause),
                    tint     = Color.White,                     // white icon on colored bg
                    modifier = Modifier.size(playIcon)
                )
            }
        }

        // Skip next — original size
        IconButton(onClick = onSkipNext, modifier = Modifier.size(skipSize)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.ctrl_skip_next),
                tint = onBg, modifier = Modifier.size(skipIcon))
        }

        // Repeat — original size
        IconButton(onClick = onCycleRepeat, modifier = Modifier.size(40.dp)) {
            Icon(repeatIcon, stringResource(R.string.ctrl_repeat),
                tint = repeatTint, modifier = Modifier.size(22.dp))
        }
    }
}

// ── 3-dot more menu — unchanged from original ────────────────────────────────

@Composable
private fun MenuRow(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit,
    tint:    Color = MaterialTheme.colorScheme.onSurface
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, color = tint, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
        }
    }
}

// ── Sleep timer dialog ────────────────────────────────────────────────────────

@Composable
private fun SleepTimerDialog(current: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sleep_timer_stop_after),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
                listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                    val selected = current == minutes
                    Surface(
                        onClick = { onSelect(minutes) },
                        shape   = RoundedCornerShape(8.dp),
                        color   = if (selected) MaterialTheme.colorScheme.primaryContainer
                                  else Color.Transparent
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.sleep_timer_minutes, minutes),
                                color    = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp)
                            if (selected)
                                Icon(Icons.Rounded.Check, null,
                                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ── Track info dialog ─────────────────────────────────────────────────────────

@Composable
private fun TrackInfoDialog(item: PlaylistItem, state: PlayerState, onDismiss: () -> Unit) {
    val context  = LocalContext.current
    var fileSize by remember { mutableStateOf("…") }
    var bitrate  by remember { mutableStateOf("…") }
    var codec    by remember { mutableStateOf("…") }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(context, item.uri)
                bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toLongOrNull()?.let { "${it / 1000} kbps" } ?: "Unknown"
                codec   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.substringAfterLast('/') ?: "Unknown"
                r.release()
                context.contentResolver.query(item.uri,
                    arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                    ?.use { cur ->
                        if (cur.moveToFirst()) {
                            val b = cur.getLong(0)
                            fileSize = when {
                                b > 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
                                b > 1024      -> "%.1f KB".format(b / 1024.0)
                                else          -> "$b B"
                            }
                        }
                    }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Rounded.AudioFile, null) },
        title = { Text(stringResource(R.string.track_info_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(stringResource(R.string.track_info_label_title),    item.title.ifBlank  { stringResource(R.string.unknown) })
                InfoRow(stringResource(R.string.track_info_label_artist),   item.artist.ifBlank { stringResource(R.string.unknown) })
                InfoRow(stringResource(R.string.track_info_label_duration), state.durationFormatted)
                InfoRow(stringResource(R.string.track_info_label_format),   codec)
                InfoRow(stringResource(R.string.track_info_label_bitrate),  bitrate)
                InfoRow(stringResource(R.string.track_info_label_size),     fileSize)
                InfoRow(stringResource(R.string.track_info_label_file),
                    item.uri.lastPathSegment ?: item.uri.toString(), small = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, small: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (small) 11.sp else 13.sp,
            modifier = Modifier.weight(0.4f))
        Text(value,
            color    = MaterialTheme.colorScheme.onSurface,
            fontSize = if (small) 11.sp else 13.sp,
            modifier = Modifier.weight(0.6f),
            maxLines = if (small) 1 else 2,
            overflow = TextOverflow.Ellipsis)
    }
}
