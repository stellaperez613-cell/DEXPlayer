package com.example.dexplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import `is`.xyz.mpv.MPVLib
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile var isMpvInitialized = false
            private set
        @Volatile var isSurfaceReady = false
        @Volatile var savedPosition = 0.0  // <- agrega esto
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Crear directorios necesarios
        filesDir.mkdirs()
        cacheDir.mkdirs()
        File(filesDir, "fonts").mkdirs()

        // Inicializar MPV con configuración correcta
        initializeMpv()

        setContent {
            MaterialTheme {
                PlayerScreen()
            }
        }
    }

    private fun initializeMpv() {
        try {
            MPVLib.create(this)

            // ══════════════════════════════════════════════
            // CRÍTICO: Configurar ANTES de init()
            // ══════════════════════════════════════════════

            // Video output
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec-copy")
            MPVLib.setOptionString("hwdec-codecs", "all")

            // Ventana y superficie
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setOptionString("keep-open", "yes")

            // Audio
            MPVLib.setOptionString("ao", "audiotrack")
            MPVLib.setOptionString("audio-channels", "stereo")

            // Performance
            MPVLib.setOptionString("video-sync", "audio")
            MPVLib.setOptionString("interpolation", "no")

            // Cache para streaming
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("demuxer-max-bytes", "50MiB")
            MPVLib.setOptionString("demuxer-max-back-bytes", "25MiB")

            // Logs para debug
            MPVLib.setOptionString("msg-level", "all=v")

            MPVLib.init()
            isMpvInitialized = true

            android.util.Log.d("DexPlayer", "MPV initialized successfully")

        } catch (e: Exception) {
            android.util.Log.e("DexPlayer", "MPV initialization failed", e)
            isMpvInitialized = false
        }
    }
    override fun onStop() {
        super.onStop()
        if (isMpvInitialized) {
            // Guardar posición antes de perder el surface
            savedPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
            android.util.Log.d("DexPlayer", "Saved position: $savedPosition")
        }
    }

    override fun onStart() {
        super.onStart()
    }
    override fun onPause() {
        super.onPause()
        if (isMpvInitialized) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (e: Exception) {
                android.util.Log.e("DexPlayer", "Error pausing", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // NO hacer nada aquí - el SurfaceView maneja la reanudación
        // cuando el Surface está listo
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMpvInitialized) {
            try {
                MPVLib.destroy()
            } catch (e: Exception) {
                android.util.Log.e("DexPlayer", "Error destroying MPV", e)
            }
            isMpvInitialized = false
        }
        isSurfaceReady = false
    }
}
