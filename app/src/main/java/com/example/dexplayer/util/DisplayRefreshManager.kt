package com.example.dexplayer.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Window
import androidx.media3.common.Tracks
import com.example.dexplayer.util.MediaType

/**
 * DisplayRefreshManager — controla la tasa de refresco de la pantalla.
 *
 * Estrategia:
 *  - MUSIC / UI:  siempre la tasa máxima del display (120Hz en el Fold 7)
 *  - VIDEO:       matchear al framerate del video (24/25/30/48/60fps)
 *                 para eliminar judder (pulldown artefacts)
 *                 Fallback a 120Hz si el framerate no está disponible.
 *
 * Compatibilidad:
 *  - API 23+: preferredDisplayModeId  (método estable)
 *  - API 34+: frameRateBoostOnTouchEnabled (complementario, activa boost en touch)
 *
 * Uso desde MainActivity:
 *   DisplayRefreshManager.applyForMusic(window)
 *   DisplayRefreshManager.applyForVideo(window, tracks)
 */
object DisplayRefreshManager {

    // Modos candidatos para video — en orden de preferencia.
    // ExoPlayer reporta frameRate en Hz (ej: 23.976, 24.0, 29.97, 30.0, 59.94, 60.0)
    private val VIDEO_RATE_CANDIDATES = listOf(120f, 60f, 48f, 30f, 25f, 24f)

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Modo música / UI general.
     * Solicita la tasa de refresco más alta disponible (120Hz).
     */
    fun applyForMusic(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (window.context).display ?: return
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        }
        val modes = display.supportedModes
        val maxMode = modes.maxByOrNull { it.refreshRate } ?: return
        DexLog.i("DisplayRefreshManager",
            "Music mode → preferredDisplayModeId=${maxMode.modeId} (${maxMode.refreshRate}Hz)")
        setMode(window, maxMode.modeId)
    }

    /**
     * Modo video.
     * Lee el framerate real del video desde Media3 [Tracks] y busca el modo
     * de display más compatible para eliminar judder.
     *
     * Ejemplos:
     *  23.976fps → modo 24Hz (si existe) o 120Hz (divisible por ~24)
     *  29.97fps  → modo 30Hz o 60Hz
     *  59.94fps  → modo 60Hz o 120Hz
     */
    fun applyForVideo(window: Window, tracks: Tracks) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (window.context).display ?: return
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        }
        val modes = display.supportedModes

        val videoFps = extractVideoFrameRate(tracks)
        DexLog.i("DisplayRefreshManager", "Video tracks framerate: $videoFps fps")

        val targetMode = if (videoFps != null) {
            findBestVideoMode(modes, videoFps)
        } else {
            // Sin info de framerate — máxima tasa (120Hz)
            modes.maxByOrNull { it.refreshRate }
        }

        if (targetMode != null) {
            DexLog.i("DisplayRefreshManager",
                "Video mode → modeId=${targetMode.modeId} " +
                "(${targetMode.refreshRate}Hz) for videoFps=$videoFps")
            setMode(window, targetMode.modeId)
        }
    }

    /**
     * Restablecer al modo por defecto del sistema.
     * Llamar cuando la app pasa a background o cuando no hay media cargada.
     */
    fun resetToDefault(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        DexLog.i("DisplayRefreshManager", "Resetting to system default display mode")
        setMode(window, 0)  // 0 = modo por defecto del sistema
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private fun setMode(window: Window, modeId: Int) {
        try {
            val params = window.attributes
            params.preferredDisplayModeId = modeId
            window.attributes = params

            // API 34+: activar boost de framerate en interacciones táctiles
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                params.frameRateBoostOnTouchEnabled = true
                window.attributes = params
            }
        } catch (e: Exception) {
            DexLog.e("DisplayRefreshManager", "Error setting display mode $modeId", e)
        }
    }

    /**
     * Extrae el framerate del primer track de video activo en la sesión.
     * Media3 reporta frameRate en Format.frameRate (Float, -1f si desconocido).
     */
    private fun extractVideoFrameRate(tracks: Tracks): Float? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val mime   = format.sampleMimeType ?: continue
                if (!mime.startsWith("video")) continue
                val fps = format.frameRate
                if (fps > 0f) return fps
            }
        }
        return null
    }

    /**
     * Encuentra el modo de display más compatible con el framerate del video.
     *
     * Lógica:
     * 1. Busca modos cuya tasa sea múltiplo entero del fps del video
     *    (ej: 24fps → 48Hz, 72Hz, 120Hz son todos válidos)
     * 2. De los candidatos válidos, prefiere el múltiplo más bajo para
     *    ahorrar energía (a menos que la diff sea pequeña)
     * 3. Si el video es 60fps o mayor, prefiere el modo más alto disponible.
     *
     * El Galaxy Z Fold 7 típicamente tiene modos: 60Hz, 120Hz (LTPO adaptive).
     */
    private fun findBestVideoMode(
        modes: Array<android.view.Display.Mode>,
        videoFps: Float
    ): android.view.Display.Mode? {
        // Normalizar fps (23.976 → 24, 29.97 → 30, 59.94 → 60)
        val normalizedFps = normalizeFrameRate(videoFps)
        DexLog.d("DisplayRefreshManager",
            "findBestVideoMode: videoFps=$videoFps normalized=$normalizedFps")

        // Modos cuya tasa de refresco es múltiplo del fps normalizado
        val compatible = modes.filter { mode ->
            val hz = mode.refreshRate
            val ratio = hz / normalizedFps
            val rounded = Math.round(ratio).toFloat()
            // Tolerancia del 2% para manejar variaciones (59.94 vs 60, etc.)
            val isMultiple = Math.abs(ratio - rounded) < 0.02f && rounded >= 1f
            if (isMultiple) {
                DexLog.d("DisplayRefreshManager",
                    "  Compatible: ${hz}Hz (×${rounded.toInt()} of ${normalizedFps}fps)")
            }
            isMultiple
        }

        return when {
            compatible.isEmpty() -> {
                DexLog.w("DisplayRefreshManager",
                    "No compatible mode found for ${normalizedFps}fps — usando máximo")
                modes.maxByOrNull { it.refreshRate }
            }
            normalizedFps >= 60f -> {
                // Video de alta frecuencia — usar el modo más alto para máxima fluidez
                compatible.maxByOrNull { it.refreshRate }
            }
            else -> {
                // Video de baja frecuencia (24/30fps) — usar múltiplo más alto
                // disponible (120Hz > 60Hz para 24fps: 120=5×24, 60=2.5×24 ← no exacto)
                // Para 24fps: 120Hz es el mejor (5×24=120, exacto)
                // Para 30fps: 60Hz o 120Hz ambos son válidos
                compatible.maxByOrNull { it.refreshRate }
            }
        }
    }

    /**
     * Normaliza framerates NTSC a sus equivalentes enteros.
     * 23.976 → 24, 29.97 → 30, 47.95 → 48, 59.94 → 60
     */
    private fun normalizeFrameRate(fps: Float): Float {
        val candidates = listOf(24f, 25f, 30f, 48f, 50f, 60f, 90f, 120f)
        return candidates.minByOrNull { Math.abs(it - fps) } ?: fps
    }
}
