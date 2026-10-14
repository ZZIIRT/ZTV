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
    private var activeChannel: Channel? = null
    private var sources: PlaybackSources? = null

    init {
        tvPlayer.exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    onReady(tvPlayer.currentPlaybackId() ?: activePlaybackId)
                } else if (playbackState == Player.STATE_ENDED && activeChannel != null) {
                    onError(tvPlayer.currentPlaybackId() ?: activePlaybackId, "Источник завершил трансляцию")
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
        if (playbackId != activePlaybackId || activeChannel?.stableKey != channel.stableKey || sources == null) {
            sources = PlaybackSources(channel.url, channel.alternativeUrls)
        }
        activePlaybackId = playbackId
        activeChannel = channel
        playCurrentSource()
    }

    fun updateChannelSources(channel: Channel) {
        if (activeChannel?.stableKey != channel.stableKey) return
        activeChannel = channel
        sources?.update(channel.url, channel.alternativeUrls)
    }

    fun hasAlternativeSource(playbackId: Long): Boolean =
        playbackId == activePlaybackId && sources?.hasNext == true

    fun tryNextSource(playbackId: Long): Boolean {
        if (playbackId != activePlaybackId || sources?.next() == null) return false
        playCurrentSource()
        return true
    }

    private fun playCurrentSource() {
        val channel = activeChannel ?: return
        val url = sources?.currentUrl ?: return
        runCatching { tvPlayer.playLive(channel.copy(url = url), activePlaybackId) }
            .onFailure { error -> onError(activePlaybackId, error.message ?: "Ошибка запуска канала") }
    }

    fun stopForBackground() {
        activeChannel = null
        sources = null
        tvPlayer.stopForBackground()
    }

    fun release() {
        tvPlayer.release()
    }
}
