@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.dexplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.dexplayer.MainActivity
import com.example.dexplayer.util.DexLog

/**
 * PlaybackService — runs as a foreground service and owns the ExoPlayer instance.
 *
 * Uses a ForwardingPlayer wrapper so that when the queue has only 1 item,
 * the next/previous buttons are hidden from the media notification and lock screen.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        DexLog.i("PlaybackService", "onCreate")

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Wrap ExoPlayer so next/prev commands are only available when there's
        // actually a next/previous item — this removes ghost buttons in notifications.
        val wrappedPlayer = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {

            override fun getAvailableCommands(): Player.Commands {
                val base    = super.getAvailableCommands()
                val builder = Player.Commands.Builder()

                // Copy all commands from the base player
                for (i in 0 until base.size()) {
                    builder.add(base.get(i))
                }

                // Remove SEEK_TO_NEXT_MEDIA_ITEM if there is no next item
                if (!exoPlayer.hasNextMediaItem()) {
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                }

                // Remove SEEK_TO_PREVIOUS_MEDIA_ITEM if there is no previous item
                if (!exoPlayer.hasPreviousMediaItem()) {
                    builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    // Keep SEEK_TO_PREVIOUS so the user can still go back to start of track
                }

                return builder.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT ->
                        exoPlayer.hasNextMediaItem()
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                        exoPlayer.hasPreviousMediaItem()
                    else -> super.isCommandAvailable(command)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, wrappedPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        DexLog.i("PlaybackService", "MediaSession created: ${mediaSession?.id}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            DexLog.i("PlaybackService", "onTaskRemoved — stopping service")
            stopSelf()
        }
    }

    override fun onDestroy() {
        DexLog.i("PlaybackService", "onDestroy")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
