package com.zziirt.ztv.playlist

import android.content.Context
import android.util.AtomicFile
import com.zziirt.ztv.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    context: Context,
    private val parser: M3uParser = M3uParser(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    private val cacheFile = File(context.filesDir, "playlist.m3u8")
    private val downloadFile = File(context.cacheDir, "playlist.download")

    suspend fun loadCached(): List<Channel> = withContext(Dispatchers.IO) {
        runCatching { parseFile(cacheFile) }.getOrDefault(emptyList())
    }

    suspend fun refresh(playlistUrl: String): RefreshResult = withContext(Dispatchers.IO) {
        runCatching {
            downloadPlaylist(playlistUrl)
            val channels = parseFile(downloadFile)
            if (channels.isEmpty()) error("Playlist has no playable channels")
            replaceCache(downloadFile)
            RefreshResult.Success(channels, System.currentTimeMillis())
        }.getOrElse { error ->
            RefreshResult.Failure(
                cachedChannels = runCatching { parseFile(cacheFile) }.getOrDefault(emptyList()),
                message = error.message ?: "Playlist update failed",
            )
        }.also {
            downloadFile.delete()
        }
    }

    private fun downloadPlaylist(playlistUrl: String) {
        val request = Request.Builder()
            .url(playlistUrl)
            .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, text/plain, */*")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Playlist response is empty")
            if (body.contentLength() > MAX_PLAYLIST_BYTES) error("Playlist is too large")
            downloadFile.outputStream().buffered().use { output ->
                body.byteStream().use { input -> input.copyToWithLimit(output, MAX_PLAYLIST_BYTES) }
            }
        }
    }

    private fun parseFile(file: File): List<Channel> {
        if (!file.isFile || file.length() == 0L) return emptyList()
        return file.inputStream().buffered().use(parser::parse)
    }

    private fun replaceCache(source: File) {
        val atomicFile = AtomicFile(cacheFile)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            source.inputStream().buffered().use { input -> input.copyTo(output) }
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    private fun InputStream.copyToWithLimit(output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) return
            total += count
            if (total > limit) throw IOException("Playlist is too large")
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_PLAYLIST_BYTES = 16L * 1024 * 1024
        const val USER_AGENT = "ZTV/0.1.10 (Android TV)"
    }
}

sealed interface RefreshResult {
    data class Success(val channels: List<Channel>, val updatedAtEpochMillis: Long) : RefreshResult
    data class Failure(val cachedChannels: List<Channel>, val message: String) : RefreshResult
}
