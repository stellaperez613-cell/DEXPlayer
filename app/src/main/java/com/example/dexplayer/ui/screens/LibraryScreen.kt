package com.example.dexplayer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dexplayer.player.PlaylistItem
import com.example.dexplayer.player.PlayerState
import com.example.dexplayer.player.Playlist
import com.example.dexplayer.util.MediaType
import com.example.dexplayer.util.ScrollRefreshBoost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Data ────────────────────────────────────────────────────────────────────
enum class LibraryFilter    { MUSIC, VIDEO }
enum class MusicSubFilter   { SONGS, ARTISTS, GENRES, PLAYLISTS }
enum class MusicSortOrder   { ASC, DESC }

data class MediaFile(
    val id: Long,
    val uri: Uri,
    val path: String,   // absolute path — used for folder exclusion
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long,
    val isVideo: Boolean
)

// ─── Screen ──────────────────────────────────────────────────────────────────
@Composable
fun LibraryScreen(
    playerState: PlayerState,
    currentMediaType: MediaType,
    isPlayerActive: Boolean,
    miniPlayerTitle: String,
    miniPlayerArtist: String,
    miniPlayerUri: Uri?,
    currentFilter: LibraryFilter,
    excludedFolders: Set<String>,
    // Playlist data & callbacks
    playlists:           List<Playlist> = emptyList(),
    onPlaylistClick:     (Playlist) -> Unit = {},
    onCreatePlaylist:    (String) -> Unit = {},
    onDeletePlaylist:    (String) -> Unit = {},
    onRenamePlaylist:    (String, String) -> Unit = { _, _ -> },
    onAddToPlaylist:     (String, List<PlaylistItem>) -> Unit = { _, _ -> },
    onImportM3U:         (Uri, String) -> Unit = { _, _ -> },
    onExportPlaylist:    (String, Uri) -> Unit = { _, _ -> },
    onAddToQueue:        (PlaylistItem) -> Unit = {},
    // Fix #3: scroll position persisted in ViewModel, restored on re-enter
    savedScrollIndex:  Int = 0,
    savedScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
    onFilterChange: (LibraryFilter) -> Unit,
    musicSubFilter: MusicSubFilter = MusicSubFilter.SONGS,
    onSubFilterChange: (MusicSubFilter) -> Unit = {},
    libraryReloadTick: Int = 0,
    onFileSelected: (index: Int, playlist: List<PlaylistItem>) -> Unit,
    onMiniPlayerClick: () -> Unit,
    onMiniPlayerTogglePlayPause: () -> Unit,
    onMiniPlayerSkipNext: () -> Unit = {},
    onMiniPlayerSkipPrevious: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val context   = LocalContext.current
    var allFiles  by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var musicSortOrder by remember { mutableStateOf(MusicSortOrder.ASC) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var longPressedItem by remember { mutableStateOf<PlaylistItem?>(null) }
    val snackbarState   = remember { SnackbarHostState() }
    val scope           = rememberCoroutineScope()

    // Reload when excluded folders change OR when permissions are freshly granted
    LaunchedEffect(excludedFolders, libraryReloadTick) {
        isLoading = true
        allFiles  = loadMediaFiles(context)
        isLoading = false
    }

    val filteredFiles = remember(allFiles, currentFilter, musicSortOrder, excludedFolders, searchQuery) {
        val base = when (currentFilter) {
            LibraryFilter.MUSIC -> allFiles.filter { !it.isVideo }
            LibraryFilter.VIDEO -> allFiles.filter { it.isVideo }
        }.filter { file ->
            excludedFolders.none { excluded -> file.path.startsWith(excluded) }
        }
        val ordered = if (musicSortOrder == MusicSortOrder.DESC)
            base.sortedByDescending { it.title.lowercase() }
        else
            base.sortedBy { it.title.lowercase() }
        // Search filter: matches title, artist, or album (case-insensitive)
        if (searchQuery.isBlank()) ordered
        else {
            val q = searchQuery.trim().lowercase()
            ordered.filter { file ->
                file.title.lowercase().contains(q)  ||
                file.artist.lowercase().contains(q) ||
                file.album.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost        = {
            SnackbarHost(snackbarState) { data ->
                Snackbar(snackbarData = data,
                    modifier = Modifier.padding(bottom = if (isPlayerActive) 72.dp else 0.dp))
            }
        },
        bottomBar = {
            if (isPlayerActive) {
                MiniPlayerBar(
                    title             = miniPlayerTitle,
                    artist            = miniPlayerArtist,
                    uri               = miniPlayerUri,
                    isPlaying         = playerState.isPlaying,
                    mediaType         = currentMediaType,
                    onClick           = onMiniPlayerClick,
                    onTogglePlayPause = onMiniPlayerTogglePlayPause,
                    onSkipPrevious    = onMiniPlayerSkipPrevious,
                    onSkipNext        = onMiniPlayerSkipNext
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FilterRow(
                current      = currentFilter,
                onSelect     = { filter ->
                    onFilterChange(filter)
                    searchActive = false
                    searchQuery  = ""
                },
                onSettings    = onOpenSettings,
                searchActive = searchActive,
                searchQuery  = searchQuery,
                onSearchOpen  = { searchActive = true },
                onSearchClose = { searchActive = false; searchQuery = "" },
                onSearchQuery = { searchQuery = it },
                showSearch    = currentFilter == LibraryFilter.MUSIC
            )

            when (currentFilter) {
                LibraryFilter.MUSIC -> {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        // Stats + sub-filter row
                        StatsRow(filteredFiles, currentFilter, musicSortOrder, searchQuery)
                        MusicSubFilterRow(
                            current     = musicSubFilter,
                            onSelect    = {
                                onSubFilterChange(it)
                            },
                            sortOrder   = musicSortOrder,
                            onSortOrder = { musicSortOrder = it }
                        )

                        when (musicSubFilter) {
                            MusicSubFilter.SONGS -> {
                                if (filteredFiles.isEmpty()) {
                                    EmptyState(currentFilter, searchQuery)
                                } else {
                                    val listState = rememberLazyListState(
                                        initialFirstVisibleItemIndex        = savedScrollIndex,
                                        initialFirstVisibleItemScrollOffset = savedScrollOffset
                                    )
                                    LaunchedEffect(
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset
                                    ) {
                                        onScrollChanged(
                                            listState.firstVisibleItemIndex,
                                            listState.firstVisibleItemScrollOffset
                                        )
                                    }
                                    ScrollRefreshBoost(listState)
                                    LaunchedEffect(musicSortOrder) { listState.scrollToItem(0) }
                                    LazyColumn(
                                        state               = listState,
                                        modifier            = Modifier.fillMaxSize(),
                                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(filteredFiles, key = { it.uri.toString() }) { file ->
                                            val playlistItem = PlaylistItem(
                                                uri     = file.uri,
                                                isVideo = file.isVideo,
                                                title   = file.title,
                                                artist  = file.artist
                                            )
                                            MediaFileCard(
                                                file      = file,
                                                onClick   = {
                                                    val playlist = filteredFiles.map {
                                                        PlaylistItem(uri = it.uri, isVideo = it.isVideo,
                                                            title = it.title, artist = it.artist)
                                                    }
                                                    onFileSelected(filteredFiles.indexOf(file), playlist)
                                                },
                                                onLongPress = { longPressedItem = playlistItem }
                                            )
                                        }
                                    }
                                }
                            }

                            MusicSubFilter.ARTISTS -> {
                                ArtistsView(
                                    files        = filteredFiles,
                                    sortOrder    = musicSortOrder,
                                    onPlayArtist = { artistFiles, startIdx ->
                                        val items = artistFiles.map {
                                            PlaylistItem(uri = it.uri, isVideo = false,
                                                title = it.title, artist = it.artist)
                                        }
                                        onFileSelected(startIdx, items)
                                    },
                                    onLongPress = { longPressedItem = it }
                                )
                            }

                            MusicSubFilter.GENRES -> {
                                GenresView(
                                    files       = filteredFiles,
                                    sortOrder   = musicSortOrder,
                                    onPlayGenre = { genreFiles, startIdx ->
                                        val items = genreFiles.map {
                                            PlaylistItem(uri = it.uri, isVideo = false,
                                                title = it.title, artist = it.artist)
                                        }
                                        onFileSelected(startIdx, items)
                                    },
                                    onLongPress = { longPressedItem = it }
                                )
                            }

                            MusicSubFilter.PLAYLISTS -> {
                                PlaylistScreen(
                                    playlists         = playlists,
                                    onPlaylistClick   = onPlaylistClick,
                                    onCreatePlaylist  = onCreatePlaylist,
                                    onDeletePlaylist  = onDeletePlaylist,
                                    onRenamePlaylist  = onRenamePlaylist,
                                    onImportM3U       = onImportM3U,
                                    onExportPlaylist  = onExportPlaylist
                                )
                            }
                        }
                    }
                }

                LibraryFilter.VIDEO -> {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else if (filteredFiles.isEmpty()) {
                        EmptyState(currentFilter, searchQuery)
                    } else {
                        StatsRow(filteredFiles, currentFilter, musicSortOrder, searchQuery)
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex        = savedScrollIndex,
                            initialFirstVisibleItemScrollOffset = savedScrollOffset
                        )
                        LaunchedEffect(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset
                        ) {
                            onScrollChanged(
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset
                            )
                        }
                        ScrollRefreshBoost(listState)
                        LaunchedEffect(musicSortOrder) { listState.scrollToItem(0) }
                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredFiles, key = { it.uri.toString() }) { file ->
                                val playlistItem = PlaylistItem(
                                    uri     = file.uri,
                                    isVideo = file.isVideo,
                                    title   = file.title,
                                    artist  = file.artist
                                )
                                MediaFileCard(
                                    file      = file,
                                                                        onClick   = {
                                        val playlist = filteredFiles.map {
                                            PlaylistItem(uri = it.uri, isVideo = it.isVideo,
                                                title = it.title, artist = it.artist)
                                        }
                                        onFileSelected(filteredFiles.indexOf(file), playlist)
                                    },
                                    onLongPress = { longPressedItem = playlistItem }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add to playlist bottom sheet ──────────────────────────────────────────
    longPressedItem?.let { item ->
        AddToPlaylistSheet(
            item            = item,
            playlists       = playlists,
            onAddToPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, listOf(item))
                val name = playlists.find { it.id == playlistId }?.name ?: "playlist"
                scope.launch { snackbarState.showSnackbar("Added to \"$name\"") }
            },
            onCreateAndAdd  = { name ->
                onAddToPlaylist("__NEW__:$name", listOf(item))
                scope.launch { snackbarState.showSnackbar("Added to \"$name\"") }
            },
            onPlayNext      = {
                onAddToQueue(item)
                scope.launch { snackbarState.showSnackbar("Playing next") }
            },
            onDismiss       = { longPressedItem = null }
        )
    }
}

// ─── Mini Player Bar ─────────────────────────────────────────────────────────
@Composable
private fun MiniPlayerBar(
    title: String, artist: String, uri: Uri?,
    isPlaying: Boolean, mediaType: MediaType,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {}
) {
    val context   = LocalContext.current
    val isVideo   = mediaType == MediaType.VIDEO
    val accent    = MaterialTheme.colorScheme.onBackground
    val shownTitle = cleanForDisplay(title, if (isVideo) "Unknown Video" else "Unknown Track")
    val shownSub   = if (isVideo) "Video" else cleanForDisplay(artist, "Audio")

    var thumbnail by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        thumbnail = null
        if (uri != null) {
            thumbnail = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
                    else if (!isVideo) {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(context, uri)
                        val b = r.embeddedPicture; r.release()
                        if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
                    } else null
                } catch (e: Exception) { null }
            }
        }
    }

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation  = 0.dp,
        shadowElevation = 12.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(72.dp)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tappable area: thumbnail + text → opens full player
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onClick)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnail != null)
                            Image(bitmap = thumbnail!!.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else
                            Icon(if (isVideo) Icons.Rounded.VideoFile else Icons.Rounded.MusicNote,
                                null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(shownTitle,
                            color      = MaterialTheme.colorScheme.onSurface,
                            style      = MaterialTheme.typography.titleMedium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(shownSub,
                            color    = MaterialTheme.colorScheme.primary,
                            style    = MaterialTheme.typography.bodySmall,
                            maxLines = 1)
                    }
                }

                // Controls
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }

                // Play/Pause with filled circle background
                Box(
                    Modifier.size(42.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }

            // Thin gold line at very bottom as visual separator
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
    }
}

// ─── Filter Row — chips + sort menu + settings + search ───────────────────────
@Composable
private fun FilterRow(
    current: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    onSettings: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQuery: (String) -> Unit,
    showSearch: Boolean = true
) {
    var dropdownOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    // Auto-focus the text field when search opens
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    // Close search bar when keyboard is dismissed
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom by rememberUpdatedState(
        androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density))
    LaunchedEffect(imeBottom) {
        if (imeBottom == 0 && searchActive) {
            onSearchClose()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Search bar (expands to fill row when active) ──────────────────────
        AnimatedVisibility(
            visible = searchActive,
            enter   = fadeIn() + expandHorizontally(),
            exit    = fadeOut() + shrinkHorizontally()
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = onSearchQuery,
                modifier      = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder   = {
                    Text(
                        if (current == LibraryFilter.MUSIC) "Search songs, artists, albums…"
                        else "Search videos…",
                        fontSize = 14.sp
                    )
                },
                leadingIcon  = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQuery("") }) {
                            Icon(Icons.Rounded.Clear, "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape  = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
            )
        }

        // ── Normal chips (hidden while search is active) ──────────────────────
        AnimatedVisibility(
            visible = !searchActive,
            enter   = fadeIn() + expandHorizontally(),
            exit    = fadeOut() + shrinkHorizontally()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                LibFilterChip("Music", Icons.Rounded.MusicNote,    current == LibraryFilter.MUSIC) { onSelect(LibraryFilter.MUSIC) }
                LibFilterChip("Video", Icons.Rounded.VideoLibrary, current == LibraryFilter.VIDEO) { onSelect(LibraryFilter.VIDEO) }
            }
        }

        if (!searchActive) Spacer(Modifier.weight(1f))

        // ── Search toggle button ──────────────────────────────────────────────
        if (showSearch) {
            IconButton(
                onClick  = { if (searchActive) onSearchClose() else onSearchOpen() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (searchActive) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                    "Search",
                    tint     = if (searchActive) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Kebab menu — settings only ────────────────────────────────────────
        AnimatedVisibility(visible = !searchActive) {
            Box {
                IconButton(onClick = { dropdownOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, "More",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(18.dp)) },
                        text        = { Text("Settings") },
                        onClick     = { dropdownOpen = false; onSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
                Text(label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        },
        onClick = onClick
    )
}

@Composable
private fun LibFilterChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_ct"
    )
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(24.dp),
        color   = bgColor,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(15.dp))
            Text(label, color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun StatsRow(files: List<MediaFile>, filter: LibraryFilter, sortOrder: MusicSortOrder = MusicSortOrder.ASC, searchQuery: String = "") {
    val sortLabel = if (filter == LibraryFilter.MUSIC && searchQuery.isBlank()) {
        if (sortOrder == MusicSortOrder.DESC) " · Z → A" else " · A → Z"
    } else ""
    val countLabel = when (filter) {
        LibraryFilter.MUSIC -> "${files.size} song${if (files.size != 1) "s" else ""}"
        LibraryFilter.VIDEO -> "${files.size} video${if (files.size != 1) "s" else ""}"
    }
    val searchLabel = if (searchQuery.isNotBlank()) " for \"$searchQuery\"" else ""
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(5.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
        )
        Text(
            countLabel + sortLabel + searchLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─── Media File Card (Fix #5: no play button) ─────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaFileCard(file: MediaFile, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    val context = LocalContext.current

    var bitmap by remember(file.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.uri) {
        bitmap = null
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    context.contentResolver.loadThumbnail(file.uri, Size(96, 96), null)
                else if (!file.isVideo) {
                    val r = MediaMetadataRetriever(); r.setDataSource(context, file.uri)
                    val b = r.embeddedPicture; r.release()
                    if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
                } else null
            } catch (e: Exception) { null }
        }
    }

    val primaryText   = file.title
    val secondaryText = file.artist.ifEmpty { "Unknown Artist" }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val accentColor  = MaterialTheme.colorScheme.primary

    Surface(
        modifier        = Modifier.fillMaxWidth().combinedClickable(
            onClick     = onClick,
            onLongClick = onLongPress
        ),
        shape           = RoundedCornerShape(14.dp),
        color           = surfaceColor,
        tonalElevation  = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Album art thumbnail
            Box(
                modifier         = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null)
                    Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else
                    Icon(if (file.isVideo) Icons.Rounded.VideoFile else Icons.Rounded.MusicNote,
                        null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(primaryText,
                    color      = MaterialTheme.colorScheme.onSurface,
                    style      = MaterialTheme.typography.titleMedium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (secondaryText.isNotEmpty()) {
                        Text(secondaryText,
                            color    = accentColor.copy(alpha = 0.8f),
                            style    = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.size(2.5.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                    }
                    Text(formatDuration(file.duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            // Format badge
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    formatSize(file.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(filter: LibraryFilter, searchQuery: String = "") {
    val (icon, title, subtitle) = if (searchQuery.isNotBlank()) {
        Triple(
            Icons.Rounded.SearchOff,
            "No results for \"$searchQuery\"",
            "Try a different title, artist or album"
        )
    } else {
        when (filter) {
            LibraryFilter.MUSIC -> Triple(Icons.Rounded.MusicOff,    "No music files found", "Add MP3, FLAC or other audio files")
            LibraryFilter.VIDEO -> Triple(Icons.Rounded.VideocamOff, "No video files found", "Add MP4, MKV or other video files")
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(96.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null,
                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title,
            color      = MaterialTheme.colorScheme.onSurface,
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

// ─── Music sub-filter row ─────────────────────────────────────────────────────

@Composable
private fun MusicSubFilterRow(
    current:     MusicSubFilter,
    onSelect:    (MusicSubFilter) -> Unit,
    sortOrder:   MusicSortOrder,
    onSortOrder: (MusicSortOrder) -> Unit
) {
    var sortDropdownOpen by remember { mutableStateOf(false) }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chips take all available space, evenly distributed
        Row(
            modifier              = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            SubFilterChip("Songs",     Icons.Rounded.MusicNote,   current == MusicSubFilter.SONGS)     { onSelect(MusicSubFilter.SONGS) }
            SubFilterChip("Artists",   Icons.Rounded.Person,      current == MusicSubFilter.ARTISTS)   { onSelect(MusicSubFilter.ARTISTS) }
            SubFilterChip("Genres",    Icons.Rounded.LocalOffer,  current == MusicSubFilter.GENRES)    { onSelect(MusicSubFilter.GENRES) }
            SubFilterChip("Playlists", Icons.Rounded.QueueMusic,  current == MusicSubFilter.PLAYLISTS) { onSelect(MusicSubFilter.PLAYLISTS) }
        }

        // Sort button anchored to the right
        Box {
            IconButton(
                onClick  = { sortDropdownOpen = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (sortOrder == MusicSortOrder.ASC) Icons.Rounded.ArrowUpward
                    else Icons.Rounded.ArrowDownward,
                    contentDescription = "Sort",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded         = sortDropdownOpen,
                onDismissRequest = { sortDropdownOpen = false }
            ) {
                SortMenuItem("A → Z", Icons.Rounded.ArrowUpward,   sortOrder == MusicSortOrder.ASC)  { onSortOrder(MusicSortOrder.ASC);  sortDropdownOpen = false }
                SortMenuItem("Z → A", Icons.Rounded.ArrowDownward, sortOrder == MusicSortOrder.DESC) { onSortOrder(MusicSortOrder.DESC); sortDropdownOpen = false }
            }
        }
    }
}

@Composable
private fun SubFilterChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent       = MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sub_ct"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(
            onClick       = onClick,
            shape         = RoundedCornerShape(12.dp),
            colors        = ButtonDefaults.textButtonColors(
                contentColor = contentColor
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
        // Underline indicator
        AnimatedVisibility(selected) {
            Box(
                Modifier.height(2.dp).width(24.dp)
                    .background(accent, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ─── Artists view ─────────────────────────────────────────────────────────────

@Composable
private fun ArtistsView(
    files:        List<MediaFile>,
    sortOrder:    MusicSortOrder,
    onPlayArtist: (List<MediaFile>, Int) -> Unit,
    onLongPress:  (PlaylistItem) -> Unit
) {
    val grouped = remember(files, sortOrder) {
        val map = files.groupBy { it.artist.ifBlank { "Unknown Artist" } }
        if (sortOrder == MusicSortOrder.DESC)
            map.toSortedMap(compareByDescending { it.lowercase() })
        else
            map.toSortedMap(compareBy { it.lowercase() })
    }

    if (grouped.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var expandedArtist by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    ScrollRefreshBoost(listState)
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }

    LazyColumn(
        state          = listState,
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        grouped.forEach { (artist, artistFiles) ->
            item(key = "artist_$artist") {
                val isExpanded = expandedArtist == artist
                Surface(
                    onClick = { expandedArtist = if (isExpanded) null else artist },
                    shape   = RoundedCornerShape(10.dp),
                    color   = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant
                              else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isExpanded) 2.dp else 0.dp
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artist avatar
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Person, null,
                                tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                color      = MaterialTheme.colorScheme.onSurface)
                            Text("${artistFiles.size} song${if (artistFiles.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onPlayArtist(artistFiles, 0) }) {
                            Icon(Icons.Rounded.PlayCircle, "Play artist",
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                        Icon(
                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded song list
            if (expandedArtist == artist) {
                items(artistFiles, key = { "artist_song_${it.uri}" }) { file ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick    = {
                                    val startIdx = artistFiles.indexOf(file).coerceAtLeast(0)
                                    onPlayArtist(artistFiles, startIdx)
                                },
                                onLongClick = {
                                    onLongPress(PlaylistItem(uri = file.uri, isVideo = false,
                                        title = file.title, artist = file.artist))
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.title.ifBlank { "Unknown" },
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color    = MaterialTheme.colorScheme.onSurface)
                            if (file.album.isNotBlank()) {
                                Text(file.album, fontSize = 11.sp,
                                    color   = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(formatDuration(file.duration),
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "artist_divider_$artist") {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─── Genres view ──────────────────────────────────────────────────────────────

@Composable
private fun GenresView(
    files:       List<MediaFile>,
    sortOrder:   MusicSortOrder,
    onPlayGenre: (List<MediaFile>, Int) -> Unit,
    onLongPress: (PlaylistItem) -> Unit
) {
    // Genre comes from MediaStore — derive it per file
    val context = LocalContext.current
    var genreMap by remember { mutableStateOf<Map<String, List<MediaFile>>>(emptyMap()) }

    LaunchedEffect(files, sortOrder) {
        withContext(Dispatchers.IO) {
            // Query genre for each file via MediaStore
            val result = mutableMapOf<String, MutableList<MediaFile>>()
            files.forEach { file ->
                val genre = try {
                    context.contentResolver.query(
                        android.net.Uri.withAppendedPath(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            file.id.toString()
                        ),
                        arrayOf(android.provider.MediaStore.Audio.AudioColumns.GENRE),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst())
                            cursor.getString(0)?.takeIf { it.isNotBlank() }
                        else null
                    }
                } catch (_: Exception) { null } ?: "Unknown Genre"
                result.getOrPut(genre) { mutableListOf() }.add(file)
            }
            genreMap = if (sortOrder == MusicSortOrder.DESC)
                    result.toSortedMap(compareByDescending { it.lowercase() })
                else
                    result.toSortedMap(compareBy { it.lowercase() })
        }
    }

    if (genreMap.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    var expandedGenre by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    ScrollRefreshBoost(listState)
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }

    LazyColumn(
        state          = listState,
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        genreMap.forEach { (genre, genreFiles) ->
            item(key = "genre_$genre") {
                val isExpanded = expandedGenre == genre
                Surface(
                    onClick = { expandedGenre = if (isExpanded) null else genre },
                    shape   = RoundedCornerShape(10.dp),
                    color   = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant
                              else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isExpanded) 2.dp else 0.dp
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LocalOffer, null,
                                tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(genre,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                color      = MaterialTheme.colorScheme.onSurface)
                            Text("${genreFiles.size} song${if (genreFiles.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onPlayGenre(genreFiles, 0) }) {
                            Icon(Icons.Rounded.PlayCircle, "Play genre",
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                        Icon(
                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (expandedGenre == genre) {
                items(genreFiles, key = { "genre_song_${it.uri}" }) { file ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick     = {
                                    val startIdx = genreFiles.indexOf(file).coerceAtLeast(0)
                                    onPlayGenre(genreFiles, startIdx)
                                },
                                onLongClick = {
                                    onLongPress(PlaylistItem(uri = file.uri, isVideo = false,
                                        title = file.title, artist = file.artist))
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.title.ifBlank { "Unknown" },
                                fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color    = MaterialTheme.colorScheme.onSurface)
                            Text(file.artist.ifBlank { "Unknown Artist" },
                                fontSize = 11.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatDuration(file.duration),
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "genre_divider_$genre") { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}
private val UNKNOWN_ARTIST_TAGS = setOf("<unknown>","unknown","unknown artist","artista desconocido","unbekannter interpret","artiste inconnu")
private fun cleanTitle(raw: String?, fallback: String): String {
    if (raw.isNullOrBlank()) return fallback
    if (raw.trim().all { it.isDigit() || it == '-' }) return fallback
    return raw.trim()
}
private fun cleanArtist(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    if (raw.trim().lowercase() in UNKNOWN_ARTIST_TAGS) return ""
    if (raw.trim().all { it.isDigit() || it == '-' }) return ""
    return raw.trim()
}
private fun cleanForDisplay(value: String, fallback: String): String {
    if (value.isBlank()) return fallback
    if (value.trim().all { it.isDigit() || it == '-' }) return fallback
    return value.trim()
}

fun loadMediaFiles(context: android.content.Context): List<MediaFile> {
    val files = mutableListOf<MediaFile>()

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATA),
        null, null, "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dnCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val ttCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val arCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val alCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val szCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val id   = cursor.getLong(idCol)
            val dn   = cursor.getString(dnCol) ?: ""
            val path = cursor.getString(pathCol) ?: ""
            val rawAlbum = cursor.getString(alCol)?.takeIf { it.isNotBlank() && it.lowercase() != "<unknown>" }
            files.add(MediaFile(
                id       = id,
                uri      = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()),
                path     = path,
                title    = cleanTitle(cursor.getString(ttCol), dn.substringBeforeLast(".").ifBlank { dn }),
                artist   = cleanArtist(cursor.getString(arCol)),
                album    = rawAlbum ?: "",
                duration = cursor.getLong(durCol), size = cursor.getLong(szCol), isVideo = false
            ))
        }
    }

    context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATA),
        null, null, "${MediaStore.Video.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val dnCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val ttCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
        val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val szCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        while (cursor.moveToNext()) {
            val id   = cursor.getLong(idCol)
            val dn   = cursor.getString(dnCol) ?: ""
            val path = cursor.getString(pathCol) ?: ""
            files.add(MediaFile(
                id = id,
                uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()),
                path = path,
                title = cleanTitle(cursor.getString(ttCol), dn.substringBeforeLast(".").ifBlank { dn }),
                artist = "", album = "",
                duration = cursor.getLong(durCol), size = cursor.getLong(szCol), isVideo = true
            ))
        }
    }

    return deduplicateFiles(files).sortedBy { it.title.lowercase() }
}

/**
 * Elimina duplicados en dos pasadas:
 *
 * Pasada 1 — Por path exacto (mismo archivo, múltiples entradas en MediaStore
 *            después de un rescan). Cuando hay colisión, conserva el de mayor tamaño.
 *
 * Pasada 2 — Por huella de contenido: title.lowercase + artist.lowercase +
 *            bucket de duración (±2 segundos). Detecta copias del mismo archivo
 *            en carpetas distintas (ej. Downloads/ y Music/).
 *            Cuando hay colisión, conserva el de mayor tamaño.
 */
private fun deduplicateFiles(files: List<MediaFile>): List<MediaFile> {
    // Pasada 1: mismo path físico — duplicados de MediaStore tras rescan
    val byPath = files
        .groupBy { it.path.trimEnd('/') }
        .values
        .map { group -> group.maxByOrNull { it.size } ?: group.first() }

    // Pasada 2: misma canción en distintas carpetas
    // Bucket de 2000ms para tolerar diferencias de duración entre re-encodings
    val byContent = byPath
        .groupBy { file ->
            Triple(
                file.title.lowercase().trim(),
                file.artist.lowercase().trim(),
                file.duration / 2000L
            )
        }
        .values
        .map { group -> group.maxByOrNull { it.size } ?: group.first() }

    return byContent
}
