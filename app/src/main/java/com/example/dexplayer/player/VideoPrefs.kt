package com.example.dexplayer.player

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit

// ── Subtitle font options ─────────────────────────────────────────────────────

enum class SubtitleFont(val displayName: String, val fontFamily: String?) {
    DEFAULT("Default",        null),
    SANS    ("Sans-serif",    "sans-serif"),
    SERIF   ("Serif",         "serif"),
    MONO    ("Monospace",     "monospace"),
    CURSIVE ("Cursive",       "cursive"),
}

// ── Subtitle background style ─────────────────────────────────────────────────

enum class SubtitleBackground(val displayName: String) {
    BOX        ("Box"),         // solid rounded rect behind text
    SHADOW_ONLY("Shadow only"), // no box, just text shadow
    NONE       ("None"),        // plain text
}

// ── Subtitle preferences ──────────────────────────────────────────────────────

data class SubtitlePrefs(
    val fontSize:     Float              = 16f,           // sp
    val font:         SubtitleFont       = SubtitleFont.DEFAULT,
    val textColorInt: Int                = Color.White.toArgb(),
    val bgAlpha:      Float              = 0.60f,          // 0–1
    val bgColorInt:   Int                = Color.Black.toArgb(),
    val background:   SubtitleBackground = SubtitleBackground.BOX,
    val position:     Float              = 0.88f,          // 0 = top, 1 = bottom (fraction of screen)
    val boldText:     Boolean            = false,
) {
    val textColor: Color get() = Color(textColorInt)
    val bgColor:   Color get() = Color(bgColorInt)
}

// ── Video player preferences ──────────────────────────────────────────────────

data class VideoPrefs(
    val doubleTapSeekSec: Int     = 10,         // seconds per double-tap seek
    val keepScreenOn:     Boolean = true,
    val rememberPosition: Boolean = true,       // resume from last position
    val subtitlePrefs:    SubtitlePrefs = SubtitlePrefs()
)

// ── SharedPreferences persistence ────────────────────────────────────────────

object VideoPrefsStore {
    private const val PREFS_NAME = "video_prefs"

    private const val KEY_SEEK_SEC        = "seek_sec"
    private const val KEY_KEEP_SCREEN_ON  = "keep_screen_on"
    private const val KEY_REMEMBER_POS    = "remember_position"
    private const val KEY_SUB_FONT_SIZE   = "sub_font_size"
    private const val KEY_SUB_FONT        = "sub_font"
    private const val KEY_SUB_TEXT_COLOR  = "sub_text_color"
    private const val KEY_SUB_BG_ALPHA    = "sub_bg_alpha"
    private const val KEY_SUB_BG_COLOR    = "sub_bg_color"
    private const val KEY_SUB_BG_STYLE    = "sub_bg_style"
    private const val KEY_SUB_POSITION    = "sub_position"
    private const val KEY_SUB_BOLD        = "sub_bold"

    fun load(context: Context): VideoPrefs {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return VideoPrefs(
            doubleTapSeekSec = p.getInt(KEY_SEEK_SEC, 10),
            keepScreenOn     = p.getBoolean(KEY_KEEP_SCREEN_ON, true),
            rememberPosition = p.getBoolean(KEY_REMEMBER_POS, true),
            subtitlePrefs    = SubtitlePrefs(
                fontSize     = p.getFloat(KEY_SUB_FONT_SIZE, 16f),
                font         = SubtitleFont.entries.getOrElse(
                    p.getInt(KEY_SUB_FONT, 0)) { SubtitleFont.DEFAULT },
                textColorInt = p.getInt(KEY_SUB_TEXT_COLOR, Color.White.toArgb()),
                bgAlpha      = p.getFloat(KEY_SUB_BG_ALPHA, 0.60f),
                bgColorInt   = p.getInt(KEY_SUB_BG_COLOR, Color.Black.toArgb()),
                background   = SubtitleBackground.entries.getOrElse(
                    p.getInt(KEY_SUB_BG_STYLE, 0)) { SubtitleBackground.BOX },
                position     = p.getFloat(KEY_SUB_POSITION, 0.88f),
                boldText     = p.getBoolean(KEY_SUB_BOLD, false),
            )
        )
    }

    fun save(context: Context, prefs: VideoPrefs) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt    (KEY_SEEK_SEC,       prefs.doubleTapSeekSec)
            putBoolean(KEY_KEEP_SCREEN_ON, prefs.keepScreenOn)
            putBoolean(KEY_REMEMBER_POS,   prefs.rememberPosition)
            val s = prefs.subtitlePrefs
            putFloat  (KEY_SUB_FONT_SIZE,  s.fontSize)
            putInt    (KEY_SUB_FONT,        s.font.ordinal)
            putInt    (KEY_SUB_TEXT_COLOR,  s.textColorInt)
            putFloat  (KEY_SUB_BG_ALPHA,    s.bgAlpha)
            putInt    (KEY_SUB_BG_COLOR,    s.bgColorInt)
            putInt    (KEY_SUB_BG_STYLE,    s.background.ordinal)
            putFloat  (KEY_SUB_POSITION,    s.position)
            putBoolean(KEY_SUB_BOLD,        s.boldText)
        }
    }
}
