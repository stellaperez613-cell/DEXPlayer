package com.example.dexplayer

import android.content.Context

import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib

class MPVSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val onSurfaceReady: (() -> Unit)? = null,
    private val onSurfaceDestroyed: (() -> Unit)? = null,

) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    companion object {
        private const val TAG = "MPVSurfaceView"
    }

    private var surfaceAttached = false

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")

        if (!MainActivity.isMpvInitialized) {
            Log.e(TAG, "MPV not initialized, cannot attach surface")
            return
        }

        try {
            MPVLib.attachSurface(holder.surface)
            surfaceAttached = true
            MainActivity.isSurfaceReady = true

            Log.d(TAG, "Surface attached successfully")
            onSurfaceReady?.invoke()
            if (MainActivity.savedPosition > 0.0) {
                handler.postDelayed({
                    try {
                        MPVLib.command(arrayOf("seek", MainActivity.savedPosition.toString(), "absolute"))
                        MPVLib.setPropertyBoolean("pause", false)
                        MainActivity.savedPosition = 0.0
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring position", e)
                    }
                }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface", e)
            surfaceAttached = false
        }

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: ${width}x${height}")

        if (!MainActivity.isMpvInitialized || !surfaceAttached) {
            Log.w(TAG, "Ignoring surfaceChanged - MPV not ready")
            return
        }

        try {
            // Usar setPropertyString en lugar de setOptionString
            // Las opciones son para antes de init(), las propiedades para después
            MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set surface size", e)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")

        MainActivity.isSurfaceReady = false

        if (!surfaceAttached) {
            Log.d(TAG, "Surface was not attached, nothing to do")
            return
        }

        try {
            // Pausar primero, luego detach
            if (MainActivity.isMpvInitialized) {
                MPVLib.setPropertyBoolean("pause", true)
                MPVLib.detachSurface()
            }
            surfaceAttached = false

            Log.d(TAG, "Surface detached successfully")
            onSurfaceDestroyed?.invoke()

        } catch (e: Exception) {
            Log.e(TAG, "Error detaching surface", e)
        }
    }
}
