package com.zziirt.ztv.playlist

import android.content.Context
import com.zziirt.ztv.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    context: Context,
    private val parser: M3uParser = M3uParser(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val cacheFile = File(context.filesDir, "playlist.m3u8")

    suspend fun loadCached(): List<Channel> = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) emptyList() else parser.parse(cacheFile.readText())
    }

    suspend fun refresh(playlistUrl: String): RefreshResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(playlistUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val channels = parser.parse(body)
                if (channels.isEmpty()) error("Playlist has no channels")
                cacheFile.writeText(body)
                RefreshResult.Success(channels, System.currentTimeMillis())
            }
        }.getOrElse { error ->
            val cached = if (cacheFile.exists()) parser.parse(cacheFile.readText()) else emptyList()
            RefreshResult.Failure(cached, error.message ?: "Playlist update failed")
        }
    }
}

sealed interface RefreshResult {
    data class Success(val channels: List<Channel>, val updatedAtEpochMillis: Long) : RefreshResult
    data class Failure(val cachedChannels: List<Channel>, val message: String) : RefreshResult
}
