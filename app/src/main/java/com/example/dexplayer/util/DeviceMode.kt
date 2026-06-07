package com.example.dexplayer.util

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class DeviceMode {
    DESKTOP_DEX,  // Samsung DeX
    MOBILE_VIDEO, // Phone/Tablet — video
    MOBILE_MUSIC  // Phone/Tablet — audio
}

enum class MediaType {
    VIDEO, AUDIO, UNKNOWN
}

data class DeviceInfo(
    val mode:          DeviceMode,
    val isDeXMode:     Boolean,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape:   Boolean
)

// ── Detection ─────────────────────────────────────────────────────────────────

object DeviceDetector {

    private const val TAG = "DeviceDetector"

    // Samsung official DEX broadcast actions
    private const val ACTION_ENTER_DEX =
        "com.samsung.android.desktopmode.ACTION_DESKTOP_MODE_CHANGED"

    fun isDeXMode(context: Context): Boolean {
        val results = mutableListOf<String>()

        // ── Method 1: UiModeManager (most reliable, works on all Android) ────
        try {
            val mgr = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val type = mgr.currentModeType
            results.add("UiModeType=$type(DESK=${Configuration.UI_MODE_TYPE_DESK})")
            if (type == Configuration.UI_MODE_TYPE_DESK) {
                log(context, true, results)
                return true
            }
        } catch (e: Exception) {
            results.add("UiModeManager=err(${e.javaClass.simpleName})")
        }

        // ── Method 2: Samsung SEM Configuration reflection (OneUI ≤ 5) ───────
        try {
            val config  = context.resources.configuration
            val enabled = config.javaClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null)
            val current = config.javaClass.getField("semDesktopModeEnabled").getInt(config)
            results.add("SEM=$current(expected=$enabled)")
            if (current == enabled) {
                log(context, true, results)
                return true
            }
        } catch (e: Exception) {
            results.add("SEM=err(${e.javaClass.simpleName})")
        }

        // ── Method 3: Samsung SemDesktopModeManager (OneUI 6+) ───────────────
        try {
            val cls      = Class.forName("com.samsung.android.desktopmode.SemDesktopModeManager")
            val instance = context.getSystemService(cls)
                ?: context.getSystemService("desktopmode")
            if (instance != null) {
                val enabled = cls.getMethod("isDesktopModeEnabled").invoke(instance) as? Boolean
                results.add("SemMgr.isEnabled=$enabled")
                if (enabled == true) {
                    log(context, true, results)
                    return true
                }
            } else {
                results.add("SemMgr=null")
            }
        } catch (e: Exception) {
            results.add("SemMgr=err(${e.javaClass.simpleName})")
        }

        // ── Method 4: Configuration.uiMode bitmask ────────────────────────────
        // uiMode & UI_MODE_TYPE_MASK == UI_MODE_TYPE_DESK
        try {
            val uiMode = context.resources.configuration.uiMode
            val type   = uiMode and Configuration.UI_MODE_TYPE_MASK
            results.add("uiModeBitmask=$type(DESK=${Configuration.UI_MODE_TYPE_DESK})")
            if (type == Configuration.UI_MODE_TYPE_DESK) {
                log(context, true, results)
                return true
            }
        } catch (e: Exception) {
            results.add("uiModeBitmask=err(${e.javaClass.simpleName})")
        }

        log(context, false, results)
        return false
    }

    private fun log(context: Context, detected: Boolean, results: List<String>) {
        val config = context.resources.configuration
        val dm     = context.resources.displayMetrics
        val summary = buildString {
            append("DEX=$detected | ")
            append(results.joinToString(" | "))
            append(" | wDp=${config.screenWidthDp} hDp=${config.screenHeightDp}")
            append(" | dpi=${dm.densityDpi}")
            append(" | uiMode=${config.uiMode}")
            append(" | ${Build.MANUFACTURER} ${Build.MODEL} SDK${Build.VERSION.SDK_INT}")
        }
        Log.d(TAG, summary)
        DexLog.i("DeviceDetector", summary)
    }

    fun detectMediaType(path: String?): MediaType {
        if (path == null) return MediaType.UNKNOWN
        return when (path.substringAfterLast(".").lowercase()) {
            "mp3","flac","wav","aac","ogg","wma","m4a","opus","ape","alac" -> MediaType.AUDIO
            "mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mts","m2ts","vob" -> MediaType.VIDEO
            else -> MediaType.UNKNOWN
        }
    }

    fun getDeviceInfo(context: Context, mediaType: MediaType = MediaType.UNKNOWN): DeviceInfo {
        val config = context.resources.configuration
        val isDeX  = isDeXMode(context)
        return DeviceInfo(
            mode           = when { isDeX -> DeviceMode.DESKTOP_DEX
                                    mediaType == MediaType.AUDIO -> DeviceMode.MOBILE_MUSIC
                                    else -> DeviceMode.MOBILE_VIDEO },
            isDeXMode      = isDeX,
            screenWidthDp  = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp,
            isLandscape    = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }
}

// ── Composable that observes DEX connect/disconnect in real time ───────────────
//
// Uses two complementary mechanisms:
//   1. Samsung broadcast ACTION_DESKTOP_MODE_CHANGED — fires when user plugs/unplugs DEX
//   2. Configuration re-read — catches window resize / density changes
//
// Usage in AppNavigation:
//   val deviceInfo by rememberDeviceInfo(mediaType)

@Composable
fun rememberDeviceInfo(mediaType: MediaType): State<DeviceInfo> {
    val context   = LocalContext.current
    val state     = remember(mediaType) {
        mutableStateOf(DeviceDetector.getDeviceInfo(context, mediaType))
    }

    DisposableEffect(mediaType) {
        // Samsung official broadcast for DEX connect/disconnect
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                DexLog.i("DeviceDetector", "DEX broadcast received: ${intent.action}")
                state.value = DeviceDetector.getDeviceInfo(ctx, mediaType)
            }
        }

        val filter = IntentFilter().apply {
            // Samsung official action (all OneUI versions)
            addAction("com.samsung.android.desktopmode.ACTION_DESKTOP_MODE_CHANGED")
            // Fallback — generic display/config change
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    return state
}
