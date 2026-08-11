package com.zziirt.ztv.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.zziirt.ztv.channels.Channel

class PlayerController(
    context: Context,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    val tvPlayer = TvPlayer(context)

    init {
        tvPlayer.exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onReady()
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.message ?: "Ошибка воспроизведения")
            }
        })
    }

    fun play(channel: Channel) {
        tvPlayer.playLive(channel.url)
    }

    fun stopForBackground() {
        tvPlayer.stopForBackground()
    }

    fun release() {
        tvPlayer.release()
    }
}
