package com.example.dexplayer.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Composable que muestra el video usando PlayerView de Media3
 * 
 * @param player El ExoPlayer a mostrar
 * @param modifier Modificador de Compose
 * @param useController Si se muestra el controlador nativo de Media3 (false para UI custom)
 * @param resizeMode Modo de redimensionado del video
 */
@Composable
fun VideoPlayerView(
    player: Player,
    modifier: Modifier = Modifier,
    useController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onPlayerViewCreated: ((PlayerView) -> Unit)? = null
) {
    val context = LocalContext.current
    
    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            this.useController = useController
            this.resizeMode = resizeMode
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Mantener pantalla encendida mientras reproduce
            keepScreenOn = true
        }
    }
    
    DisposableEffect(player) {
        playerView.player = player
        onPlayerViewCreated?.invoke(playerView)
        
        onDispose {
            playerView.player = null
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Vista de video sin controles (para UI custom)
 */
@Composable
fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier
) {
    VideoPlayerView(
        player = player,
        modifier = modifier,
        useController = false,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    )
}

/**
 * Vista de video con controles nativos de Media3
 */
@Composable
fun VideoPlayerWithControls(
    player: Player,
    modifier: Modifier = Modifier
) {
    VideoPlayerView(
        player = player,
        modifier = modifier,
        useController = true,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    )
}
