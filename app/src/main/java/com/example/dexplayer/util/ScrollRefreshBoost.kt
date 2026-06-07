package com.example.dexplayer.util

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * ScrollRefreshBoost
 *
 * Eleva la tasa de refresco a 120Hz mientras hay scroll activo,
 * y la devuelve al modo por defecto del sistema (LTPO 1-60Hz) cuando
 * el scroll se detiene.
 *
 * Uso con LazyListState:
 *   val listState = rememberLazyListState()
 *   ScrollRefreshBoost(listState)
 *   LazyColumn(state = listState) { ... }
 *
 * Uso con ScrollState (Column/verticalScroll):
 *   val scrollState = rememberScrollState()
 *   ScrollRefreshBoost(scrollState)
 *   Column(modifier = Modifier.verticalScroll(scrollState)) { ... }
 *
 * Uso con LazyGridState:
 *   val gridState = rememberLazyGridState()
 *   ScrollRefreshBoost(gridState)
 *   LazyVerticalGrid(state = gridState) { ... }
 *
 * No hace nada en dispositivos con API < 23.
 */

// ── Tiempo de inactividad antes de bajar el refresh rate ─────────────────────
private const val IDLE_TIMEOUT_MS = 300L

// ── LazyListState ─────────────────────────────────────────────────────────────
@Composable
fun ScrollRefreshBoost(state: LazyListState) {
    val isScrolling = state.isScrollInProgress
    ApplyRefreshBoost(isScrolling)
}

// ── ScrollState (Column/Row con verticalScroll/horizontalScroll) ──────────────
@Composable
fun ScrollRefreshBoost(state: ScrollState) {
    val isScrolling = state.isScrollInProgress
    ApplyRefreshBoost(isScrolling)
}

// ── LazyGridState ─────────────────────────────────────────────────────────────
@Composable
fun ScrollRefreshBoost(state: LazyGridState) {
    val isScrolling = state.isScrollInProgress
    ApplyRefreshBoost(isScrolling)
}

// ── Lógica central ────────────────────────────────────────────────────────────
@Composable
private fun ApplyRefreshBoost(isScrolling: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

    val context = LocalContext.current
    val activity = context as? Activity ?: return

    // Estado con timeout: sube inmediatamente al empezar scroll,
    // espera IDLE_TIMEOUT_MS antes de bajar (evita flicker en pausas cortas)
    var boosted by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            // Subir inmediatamente
            if (!boosted) {
                boosted = true
                DisplayRefreshManager.applyForMusic(activity.window)   // usa max Hz
            }
        } else {
            // Esperar un poco antes de bajar (fling deceleration aún activo)
            delay(IDLE_TIMEOUT_MS)
            if (!isScrolling) {
                boosted = false
                DisplayRefreshManager.resetToDefault(activity.window)
            }
        }
    }
}
