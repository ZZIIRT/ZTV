package com.zziirt.ztv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class TvPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
        repeatMode = Player.REPEAT_MODE_OFF
    }

    fun playLive(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url), true)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.seekToDefaultPosition()
    }

    fun stopForBackground() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        exoPlayer.release()
    }

    @OptIn(UnstableApi::class)
    fun createView(context: Context): PlayerView =
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setKeepContentOnPlayerReset(true)
        }

    fun isReady(): Boolean = exoPlayer.playbackState == Player.STATE_READY
}
