package com.zziirt.ztv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zziirt.ztv.channels.Channel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class TvPlayer(context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setDefaultRequestProperties(
            mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "*/*",
            ),
        )
    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        playWhenReady = true
        repeatMode = Player.REPEAT_MODE_OFF
    }

    fun playLive(channel: Channel, playbackId: Long) {
        val streamType = StreamTypeResolver.resolve(channel.url)
        val mediaItem = createMediaItem(channel, playbackId, streamType)
        val mediaSource = createMediaSource(mediaItem, streamType)
        exoPlayer.stop()
        exoPlayer.setMediaSource(mediaSource, true)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.seekToDefaultPosition()
    }

    fun currentPlaybackId(): Long? = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull()

    fun stopForBackground() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun release() {
        exoPlayer.release()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    fun createView(context: Context): PlayerView =
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setKeepContentOnPlayerReset(true)
        }

    fun isReady(): Boolean = exoPlayer.playbackState == Player.STATE_READY

    private fun createMediaItem(channel: Channel, playbackId: Long, streamType: StreamType): MediaItem =
        MediaItem.Builder()
            .setUri(channel.url)
            .setMediaId(playbackId.toString())
            .setMimeType(
                when (streamType) {
                    StreamType.Dash -> MimeTypes.APPLICATION_MPD
                    StreamType.Hls -> MimeTypes.APPLICATION_M3U8
                    StreamType.MpegTs -> MimeTypes.VIDEO_MP2T
                    else -> null
                },
            )
            .build()

    private fun createMediaSource(mediaItem: MediaItem, streamType: StreamType): MediaSource =
        when (streamType) {
            StreamType.Dash -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            StreamType.Hls -> HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            StreamType.MpegTs,
            StreamType.Progressive -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            StreamType.Rtsp -> RtspMediaSource.Factory().createMediaSource(mediaItem)
            StreamType.Unsupported -> throw UnsupportedStreamException("Адрес канала ведёт на веб-страницу")
        }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 ZTV/0.1.2"
    }
}

class UnsupportedStreamException(message: String) : IllegalArgumentException(message)
