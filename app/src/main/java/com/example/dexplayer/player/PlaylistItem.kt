package com.example.dexplayer.player

import android.net.Uri

data class PlaylistItem(
    val uri: Uri,
    val isVideo: Boolean,
    val title: String = "",   // título limpio del MediaStore
    val artist: String = ""   // artista limpio del MediaStore
)
