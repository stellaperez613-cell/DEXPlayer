package com.example.dexplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cargar las librerías nativas de MPV
        System.loadLibrary("mpv")

        setContent {
            MaterialTheme {
                PlayerScreen()
            }
        }
    }
}