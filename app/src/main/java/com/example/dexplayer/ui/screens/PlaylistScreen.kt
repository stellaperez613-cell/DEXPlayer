package com.example.dexplayer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dexplayer.player.Playlist
import com.example.dexplayer.player.PlaylistItem
import com.example.dexplayer.util.ScrollRefreshBoost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Playlist list screen ──────────────────────────────────────────────────────

@Composable
fun PlaylistScreen(
    playlists:           List<Playlist>,
    onPlaylistClick:     (Playlist) -> Unit,
    onCreatePlaylist:    (String) -> Unit,
    onDeletePlaylist:    (String) -> Unit,
    onRenamePlaylist:    (String, String) -> Unit,
    onImportM3U:         (Uri, String) -> Unit,
    onExportPlaylist:    (String, Uri) -> Unit
) {
    var showCreateDialog  by remember { mutableStateOf(false) }
    var renameTarget      by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget      by remember { mutableStateOf<Playlist?>(null) }
    var exportTarget      by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Playlist"
            onImportM3U(uri, name)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val id = exportTarget
        if (uri != null && id != null) onExportPlaylist(id, uri)
        exportTarget = null
    }

    // No Scaffold — already inside LibraryScreen's Scaffold.
    // FAB is positioned with Box overlay instead.
    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            PlaylistEmptyState()
        } else {
            val listState1 = rememberLazyListState()
            ScrollRefreshBoost(listState1)
            LazyColumn(
                state               = listState1,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp  // room for FAB
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${playlists.size} playlist${if (playlists.size != 1) "s" else ""}",
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                itemsIndexed(playlists) { _, pl ->
                    PlaylistCard(
                        playlist = pl,
                        onClick  = { onPlaylistClick(pl) },
                        onRename = { renameTarget = pl },
                        onDelete = { deleteTarget = pl },
                        onExport = {
                            val safeName = pl.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                            exportTarget = pl.id
                            exportLauncher.launch("$safeName.m3u")
                        }
                    )
                }
            }
        }

        // FAB column — bottom-end overlay
        Column(
            modifier              = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment   = Alignment.End,
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Rounded.FileUpload, "Import M3U", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick        = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor   = MaterialTheme.colorScheme.background
            ) {
                Icon(Icons.Rounded.Add, "New Playlist")
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showCreateDialog) {
        NameDialog(
            title       = "New Playlist",
            initial     = "",
            confirmText = "Create",
            onConfirm   = { name -> onCreatePlaylist(name); showCreateDialog = false },
            onDismiss   = { showCreateDialog = false }
        )
    }

    renameTarget?.let { pl ->
        NameDialog(
            title       = "Rename Playlist",
            initial     = pl.name,
            confirmText = "Rename",
            onConfirm   = { name -> onRenamePlaylist(pl.id, name); renameTarget = null },
            onDismiss   = { renameTarget = null }
        )
    }

    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon             = { Icon(Icons.Rounded.DeleteForever, null) },
            title            = { Text("Delete playlist?") },
            text             = { Text("\"${pl.name}\" and its ${pl.items.size} tracks will be removed. This cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = { onDeletePlaylist(pl.id); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Playlist card ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick:  () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent   = MaterialTheme.colorScheme.onBackground

    Surface(
        modifier       = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art mosaic (up to 4 thumbnails)
            PlaylistCover(playlist.items, size = 56.dp)

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${playlist.items.size} track${if (playlist.items.size != 1) "s" else ""}",
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as M3U") },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onExport() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ── Playlist detail screen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist:         Playlist,
    onBack:           () -> Unit,
    onPlayAll:        (List<PlaylistItem>, Int) -> Unit,
    onRemoveItem:     (Int) -> Unit,
    onExport:         (Uri) -> Unit
) {
    var deleteItemIndex  by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) onExport(uri)
    }

    Scaffold(
        containerColor      = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("${playlist.items.size} tracks",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val safeName = playlist.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                        exportLauncher.launch("$safeName.m3u")
                    }) {
                        Icon(Icons.Rounded.FileDownload, "Export M3U")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (playlist.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QueueMusic, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Empty playlist",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text("Long-press songs in your library to add them here",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val listState2 = rememberLazyListState()
            ScrollRefreshBoost(listState2)
            LazyColumn(
                state          = listState2,
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Play all header
                item {
                    PlayAllHeader(
                        trackCount = playlist.items.size,
                        onPlayAll  = { onPlayAll(playlist.items, 0) },
                        onShuffle  = { onPlayAll(playlist.items.shuffled(), 0) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                itemsIndexed(playlist.items) { index, item ->
                    PlaylistTrackCard(
                        item     = item,
                        index    = index,
                        onClick  = { onPlayAll(playlist.items, index) },
                        onRemove = { deleteItemIndex = index }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Confirm remove
    deleteItemIndex?.let { idx ->
        val item = playlist.items.getOrNull(idx)
        if (item != null) {
            AlertDialog(
                onDismissRequest = { deleteItemIndex = null },
                title = { Text("Remove track?") },
                text  = { Text("\"${item.title.ifBlank { "Track ${idx+1}" }}\" will be removed from this playlist.") },
                confirmButton = {
                    TextButton(onClick = { onRemoveItem(idx); deleteItemIndex = null }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteItemIndex = null }) { Text("Cancel") }
                }
            )
        }
    }
}

// ── Play all header ───────────────────────────────────────────────────────────

@Composable
private fun PlayAllHeader(trackCount: Int, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick        = onPlayAll,
            modifier       = Modifier.weight(1f),
            colors         = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor   = MaterialTheme.colorScheme.background
            ),
            shape          = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play all")
        }
        OutlinedButton(
            onClick  = onShuffle,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
    }
}

// ── Track card inside playlist ────────────────────────────────────────────────

@Composable
private fun PlaylistTrackCard(
    item:     PlaylistItem,
    index:    Int,
    onClick:  () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val accent  = MaterialTheme.colorScheme.onBackground
    var bitmap  by remember(item.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.uri) {
        bitmap = null
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    context.contentResolver.loadThumbnail(item.uri, Size(96, 96), null)
                else if (!item.isVideo) {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(context, item.uri)
                    val b = r.embeddedPicture; r.release()
                    if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
                } else null
            } catch (e: Exception) { null }
        }
    }

    Surface(
        modifier       = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape          = RoundedCornerShape(10.dp),
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier          = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index number
            Text(
                "${index + 1}",
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.width(24.dp)
            )

            // Thumbnail
            Box(
                modifier          = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.07f)),
                contentAlignment  = Alignment.Center
            ) {
                if (bitmap != null)
                    Image(bitmap!!.asImageBitmap(), null,
                        modifier     = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                else
                    Icon(
                        if (item.isVideo) Icons.Rounded.VideoFile else Icons.Rounded.AudioFile,
                        null,
                        tint     = accent.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { item.uri.lastPathSegment ?: "Track ${index+1}" },
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (item.artist.isNotBlank()) {
                    Text(item.artist,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.RemoveCircleOutline, "Remove",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Playlist cover mosaic ─────────────────────────────────────────────────────

@Composable
fun PlaylistCover(items: List<PlaylistItem>, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val accent  = MaterialTheme.colorScheme.onBackground
    val uris    = items.filter { !it.isVideo }.take(4).map { it.uri }
        .ifEmpty { items.take(4).map { it.uri } }

    var bitmaps by remember(uris) { mutableStateOf<List<Bitmap?>>(emptyList()) }
    LaunchedEffect(uris) {
        bitmaps = uris.map { uri ->
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
                    else {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(context, uri)
                        val b = r.embeddedPicture; r.release()
                        if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
                    }
                } catch (e: Exception) { null }
            }
        }
    }

    Box(
        modifier         = Modifier.size(size).clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmaps.any { it != null } && bitmaps.size >= 2) {
            // 2×2 mosaic
            val half = size / 2
            Column {
                Row {
                    repeat(2) { col ->
                        val bmp = bitmaps.getOrNull(col)
                        if (bmp != null)
                            Image(bmp.asImageBitmap(), null,
                                modifier     = Modifier.size(half),
                                contentScale = ContentScale.Crop)
                        else
                            Box(Modifier.size(half).background(accent.copy(alpha = 0.05f)))
                    }
                }
                Row {
                    repeat(2) { col ->
                        val bmp = bitmaps.getOrNull(col + 2)
                        if (bmp != null)
                            Image(bmp.asImageBitmap(), null,
                                modifier     = Modifier.size(half),
                                contentScale = ContentScale.Crop)
                        else
                            Box(Modifier.size(half).background(accent.copy(alpha = 0.03f)))
                    }
                }
            }
        } else {
            val single = bitmaps.firstOrNull { it != null }
            if (single != null)
                Image(single.asImageBitmap(), null,
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            else
                Icon(Icons.Rounded.QueueMusic, null,
                    tint     = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(size * 0.45f))
        }
    }
}

// ── Add to playlist bottom sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    item:             PlaylistItem,
    playlists:        List<Playlist>,
    onAddToPlaylist:  (String) -> Unit,   // playlistId
    onCreateAndAdd:   (String) -> Unit,   // new playlist name
    onPlayNext:       () -> Unit,
    onDismiss:        () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val sheetState       = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add to playlist",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 16.sp,
                        color      = MaterialTheme.colorScheme.onSurface)
                    Text(
                        item.title.ifBlank { "Track" },
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Play next option
            ListItem(
                headlineContent = { Text("Play next") },
                leadingContent  = {
                    Icon(Icons.Rounded.QueuePlayNext, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable { onPlayNext(); onDismiss() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Create new playlist
            ListItem(
                headlineContent = { Text("New playlist…") },
                leadingContent  = {
                    Box(
                        modifier         = Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, null,
                            tint     = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.clickable { showCreateDialog = true }
            )

            // Existing playlists
            playlists.forEach { pl ->
                ListItem(
                    headlineContent   = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${pl.items.size} track${if (pl.items.size != 1) "s" else ""}",
                            fontSize = 12.sp)
                    },
                    leadingContent    = {
                        PlaylistCover(pl.items, size = 40.dp)
                    },
                    trailingContent   = {
                        val alreadyAdded = pl.items.any { it.uri == item.uri }
                        if (alreadyAdded)
                            Icon(Icons.Rounded.CheckCircle, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                    },
                    modifier = Modifier.clickable {
                        onAddToPlaylist(pl.id)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title       = "New Playlist",
            initial     = "",
            confirmText = "Create & Add",
            onConfirm   = { name -> onCreateAndAdd(name); showCreateDialog = false; onDismiss() },
            onDismiss   = { showCreateDialog = false }
        )
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
fun NameDialog(
    title:       String,
    initial:     String,
    confirmText: String,
    onConfirm:   (String) -> Unit,
    onDismiss:   () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text("Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled  = text.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PlaylistEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.QueueMusic, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("No playlists yet",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to create one or import an M3U file",
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
