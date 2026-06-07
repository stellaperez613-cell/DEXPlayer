package com.example.dexplayer.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class SubtitleFontFamily { DEFAULT, SANS_SERIF, SERIF, MONOSPACE }

enum class SubtitlePosition   { BOTTOM, TOP }

enum class VideoAspectRatio   { FIT, FILL, STRETCH }

// ── All video player preferences in one data class ────────────────────────────

data class VideoSettings(
    // Subtitle appearance
    val subtitleEnabled:       Boolean           = true,
    val subtitleFontSizeSp:    Float             = 16f,
    val subtitleFontFamily:    SubtitleFontFamily = SubtitleFontFamily.DEFAULT,
    val subtitleTextColorArgb: Int               = Color.White.toArgb(),
    val subtitleBgColorArgb:   Int               = Color.Black.toArgb(),
    val subtitleBgOpacity:     Float             = 0.62f,
    val subtitlePosition:      SubtitlePosition  = SubtitlePosition.BOTTOM,
    val subtitleBoldText:      Boolean           = false,

    // Playback gestures & behavior
    val doubleTapSeekSeconds: Int             = 10,
    val brightnessGesture:    Boolean         = true,
    val volumeGesture:        Boolean         = true,
    val defaultAspectRatio:   VideoAspectRatio = VideoAspectRatio.FIT,
    val autoRotate:           Boolean         = true,
)
