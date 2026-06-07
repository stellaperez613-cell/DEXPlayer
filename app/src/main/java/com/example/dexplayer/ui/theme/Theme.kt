package com.example.dexplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

// ── Obsidian & Gold palette ──────────────────────────────────────────────────
private val Gold          = Color(0xFFC9A84C)
private val GoldDim       = Color(0xFF8B6820)
private val GoldContainer = Color(0xFF3D2E0A)
private val OnGold        = Color(0xFF1A1000)

private val DarkScheme = darkColorScheme(
    primary              = Gold,
    onPrimary            = OnGold,
    primaryContainer     = GoldContainer,
    onPrimaryContainer   = Color(0xFFFFDFA0),
    secondary            = Color(0xFF8B9AB5),
    onSecondary          = Color(0xFF0D1520),
    secondaryContainer   = Color(0xFF1E2840),
    onSecondaryContainer = Color(0xFFCBD8F0),
    tertiary             = Color(0xFFB07A60),
    onTertiary           = Color(0xFF1A0A00),
    background           = Color(0xFF0B0B10),
    onBackground         = Color(0xFFF0EBE0),
    surface              = Color(0xFF121218),
    onSurface            = Color(0xFFE8E0D2),
    surfaceVariant       = Color(0xFF1C1C26),
    onSurfaceVariant     = Color(0xFF9A9080),
    surfaceTint          = Gold,
    outline              = Color(0xFF3A3830),
    outlineVariant       = Color(0xFF2A2820),
    error                = Color(0xFFCF6679),
    onError              = Color(0xFF1A000A),
)

private val LightScheme = lightColorScheme(
    primary              = GoldDim,
    onPrimary            = Color(0xFFFFFBF0),
    primaryContainer     = Color(0xFFFFEBC0),
    onPrimaryContainer   = Color(0xFF2C1A00),
    secondary            = Color(0xFF4D6B8A),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFD0E0F4),
    onSecondaryContainer = Color(0xFF0A1E32),
    tertiary             = Color(0xFF8B5940),
    onTertiary           = Color(0xFFFFFFFF),
    background           = Color(0xFFF6F3EE),
    onBackground         = Color(0xFF18160F),
    surface              = Color(0xFFFAF8F4),
    onSurface            = Color(0xFF201E17),
    surfaceVariant       = Color(0xFFEDE8DF),
    onSurfaceVariant     = Color(0xFF706755),
    surfaceTint          = GoldDim,
    outline              = Color(0xFFB8AE9A),
    outlineVariant       = Color(0xFFD8D0C0),
    error                = Color(0xFFB3223A),
    onError              = Color(0xFFFFFFFF),
)

private val DexTypography = Typography(
    displayLarge   = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Black,     letterSpacing = (-1.5).sp),
    displayMedium  = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    displaySmall   = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold,      letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,      letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = (-0.25).sp),
    headlineSmall  = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.sp),
    titleLarge     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.sp),
    titleMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.15.sp),
    bodyMedium     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.15.sp),
    bodySmall      = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.2.sp),
    labelLarge     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.5.sp),
)

@Composable
fun DexPlayerTheme(
    darkTheme:    Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content:      @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else      -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = DexTypography,
        content     = content
    )
}
