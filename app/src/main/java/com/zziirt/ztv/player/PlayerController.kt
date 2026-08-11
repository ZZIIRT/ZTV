package com.zziirt.ztv.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.zziirt.ztv.channels.Channel

class PlayerController(
    context: Context,
    private val onReady: (Long) -> Unit,
    private val onError: (Long, String) -> Unit,
) {
    val tvPlayer = TvPlayer(context)
    private var activePlaybackId = 0L

    init {
        tvPlayer.exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    onReady(tvPlayer.currentPlaybackId() ?: activePlaybackId)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(
                    tvPlayer.currentPlaybackId() ?: activePlaybackId,
                    error.message ?: "Ошибка воспроизведения",
                )
            }
        })
    }

    fun play(channel: Channel, playbackId: Long) {
        activePlaybackId = playbackId
        runCatching { tvPlayer.playLive(channel, playbackId) }
            .onFailure { error -> onError(playbackId, error.message ?: "Ошибка запуска канала") }
    }

    fun stopForBackground() {
        tvPlayer.stopForBackground()
    }

    fun release() {
        tvPlayer.release()
    }
}
